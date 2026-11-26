package dev.tim1337.lumio.update;

import dev.tim1337.lumio.api.experimental.util.AddonUpdates;
import dev.tim1337.lumio.api.experimental.util.HttpLimits;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.function.Supplier;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The update check and the update the player asks for. Checks once per game start and on Check now, never in a developer's build; one request
 * to GitHub's API per mod and check, at most every few hours unless asked, answered from its ETag when nothing changed. Downloads only from
 * {@link #update}, which only the Update button calls: each file is checked ({@link JarCheck}, {@link ReleaseSignature}) before it gets its name,
 * and installed by {@link SwapHelper} when the game closes. Plain Java behind {@link Env} and {@link UpdateHttp}, so it is tested without the
 * game or a network.
 */
final class Updater {
	/** What the updater knows of the game and its mods. */
	interface Env {
		/** @return whether this jar was built for a public release */
		boolean releaseBuild();

		/** @return a mod's installed version, or null when it is not installed */
		String installedVersion(String modId);

		/** @return the jar a mod was loaded from, or null when it is no single jar (a developer's run) */
		Path installedJar(String modId);

		String minecraft();

		int java();

		String loader();

		/** @return the Lumio API version installed */
		String api();

		/** @return the updater's own folder: lumio/updates in the game's folder */
		Path folder();

		List<AddonUpdates.Source> addons();

		long now();

		/** @return the public key a release's signature must match: {@link ReleaseSignature#PUBLIC_KEY} in the game */
		String releaseKey();
	}

	/** Where the updater stands, for the notice and the settings. */
	enum Stage {
		IDLE, CHECKING, OFFER, DOWNLOADING, READY, FAILED
	}

	/** A mod with an update to offer. */
	record Offer(UpdateSources.Source source, ReleasePicker.Pick pick) {
	}

	static final long CHECK_EVERY_MILLIS = 6L * 60 * 60 * 1000;
	private static final String API = "https://api.github.com";
	private static final String STATE_FILE = "state.json";
	private static final String PART = ".part";
	private static final String BACKUP = "backup";
	private static final int BUFFER = 64 * 1024;
	/** The longest a download may take as a whole: each read also has its own timeout, but a server trickling bytes must not hold it forever. */
	static final long DOWNLOAD_DEADLINE_NANOS = 10L * 60 * 1_000_000_000L;
	static final String NOT_VERIFIED = "the update could not be verified";

	private final Env env;
	private final UpdateHttp http;
	private final Supplier<String> testServer;
	private final UpdateState state;
	private volatile Stage stage = Stage.IDLE;
	private volatile List<Offer> offers = List.of();
	private volatile String problem = "";
	private volatile String lastResult = "";
	/** How much of the download is done, from 0 to 1. */
	private volatile float progress;
	private boolean dismissed;

	Updater(Env env, UpdateHttp http, Supplier<String> testServer) {
		this.env = env;
		this.http = http;
		this.testServer = testServer;
		this.state = UpdateState.load(env.folder().resolve(STATE_FILE));
	}

	Stage stage() {
		return stage;
	}

	List<Offer> offers() {
		return offers;
	}

	String problem() {
		return problem;
	}

	float progress() {
		return progress;
	}

	/** @return the time of the last check, or 0 */
	long lastCheck() {
		return state.lastCheck;
	}

	/** @return what the last check found, for the settings */
	String lastResult() {
		return lastResult;
	}

	/** @return whether the notice should show: there is something to offer, under way, ready or failed, and it was not put off */
	boolean noticeShown() {
		return !dismissed && (stage == Stage.OFFER || stage == Stage.DOWNLOADING || stage == Stage.READY || stage == Stage.FAILED);
	}

	/**
	 * @return whether a check may reach the network: never in a developer's build (a test server only outside a public release), and then only
	 *         when the check is on or the player asked
	 */
	boolean mayCheck(boolean manual, boolean checkOn) {
		boolean releaseLike = env.releaseBuild() && !LumioVersion.devBuild(env.installedVersion(UpdateSources.LUMIO.modId()));
		return (releaseLike || testServer.get() != null) && (manual || checkOn);
	}

	/**
	 * Checks for updates, on the caller's thread (the game runs it on a background thread). Does nothing unless {@link #mayCheck}; without
	 * {@code manual} also nothing while the last check is recent.
	 */
	void check(boolean manual, boolean checkOn) {
		if (!mayCheck(manual, checkOn) || stage == Stage.DOWNLOADING || stage == Stage.READY) {
			return;
		}
		boolean fresh = !manual && env.now() - state.lastCheck < CHECK_EVERY_MILLIS;
		stage = Stage.CHECKING;
		List<Offer> found = new ArrayList<>();
		String failure = null;
		String server = testServer.get();
		for (UpdateSources.Source source : UpdateSources.all(env.addons())) {
			String installedText = env.installedVersion(source.modId());
			LumioVersion installed = LumioVersion.parse(installedText);
			// an add-on built by a developer is never replaced, as Lumio's own developer's build never checks
			if (installed == null || server == null && LumioVersion.devBuild(installedText)) {
				continue;
			}
			String body;
			try {
				body = fresh && state.bodies.containsKey(source.modId()) ? state.bodies.get(source.modId()) : fetch(source);
			} catch (IOException | RuntimeException e) {
				failure = "could not reach GitHub";
				continue;
			}
			ReleasePicker.Pick pick = ReleasePicker.pick(GitHubRelease.parseList(body), installed, source, env.minecraft(),
				LumioVersion.parse(state.skipped.get(source.modId())), server);
			if (pick != null) {
				found.add(new Offer(source, pick));
			}
		}
		if (!fresh) {
			state.lastCheck = env.now();
		}
		state.save();
		offers = List.copyOf(found);
		lastResult = failure != null && found.isEmpty() ? "Failed: " + failure : found.isEmpty() ? "Up to date" : summary(found);
		dismissed = false;
		stage = found.isEmpty() ? Stage.IDLE : Stage.OFFER;
	}

	private static String summary(List<Offer> found) {
		return found.getFirst().pick().version().display() + " available";
	}

	/** @return the list of releases of a mod: one request, answered from the saved list when its ETag still holds */
	private String fetch(UpdateSources.Source source) throws IOException {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("User-Agent", "Lumio-Updater/" + env.installedVersion(UpdateSources.LUMIO.modId()));
		headers.put("Accept", "application/vnd.github+json");
		String etag = state.etags.get(source.modId());
		if (etag != null && state.bodies.containsKey(source.modId())) {
			headers.put("If-None-Match", etag);
		}
		String server = testServer.get();
		UpdateHttp.Answer answer = http.get(URI.create((server != null ? server : API) + source.releasesPath()), headers);
		try (InputStream in = answer.body()) {
			if (answer.status() == 304 && state.bodies.containsKey(source.modId())) {
				return state.bodies.get(source.modId());
			}
			if (answer.status() != 200 || in == null) {
				throw new IOException("GitHub answered " + answer.status());
			}
			String body = HttpLimits.readText(in);
			state.bodies.put(source.modId(), body);
			state.etags.put(source.modId(), answer.etag());
			return body;
		}
	}

	/** The notice's Later: hidden until the next check. */
	void later() {
		dismissed = true;
	}

	/** The notice's Skip this version: the offered versions are not offered again. */
	void skip() {
		for (Offer offer : offers) {
			state.skipped.put(offer.source().modId(), offer.pick().version().toString());
		}
		state.save();
		offers = List.of();
		stage = Stage.IDLE;
	}

	/**
	 * The Update button, the only way anything is downloaded: every offered jar and its signature into the updater's folder, the jar under a
	 * part name first. It gets its name only after every check passed: size and SHA-256 as GitHub lists them, the signature against Lumio's
	 * release key, and its mod file (the same mod and version, made for this game). When all fit, the plan for the swap at the game's close is
	 * written. Any mismatch deletes what was downloaded and keeps the installed versions; the installed jars are never touched here.
	 */
	void update() {
		if (stage != Stage.OFFER || offers.isEmpty()) {
			return;
		}
		stage = Stage.DOWNLOADING;
		progress = 0f;
		List<Path> downloaded = new ArrayList<>();
		List<String> plan = new ArrayList<>();
		String newApi = null;
		try {
			Files.createDirectories(env.folder());
			long total = 0;
			for (Offer offer : offers) {
				total += offer.pick().asset().size();
			}
			long done = 0;
			for (Offer offer : offers) {
				UpdateSources.Source source = offer.source();
				ReleasePicker.Pick pick = offer.pick();
				GitHubRelease.Asset asset = pick.asset();
				// the file's name is built here, never taken from GitHub's answer
				String name = ReleasePicker.fileName(source, pick.version(), env.minecraft());
				Path file = env.folder().resolve(name);
				Path part = part(file);
				downloaded.add(file);
				downloaded.add(part);
				byte[] signature = signature(pick.signature());
				download(asset, part, done, total);
				done += asset.size();
				String api = source == UpdateSources.LUMIO ? null : newApi != null ? newApi : env.api();
				String reason = JarCheck.problem(part, asset, new JarCheck.Expected(source.modId(), pick.version(), env.minecraft(), env.java(), env.loader(), api));
				if (reason == null && !ReleaseSignature.verify(part, signature, env.releaseKey())) {
					reason = NOT_VERIFIED;
				}
				if (reason != null) {
					throw new UpdateFailed(reason);
				}
				Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				if (source == UpdateSources.LUMIO) {
					JsonObject mod = JarCheck.modFile(file);
					newApi = mod == null ? null : JarCheck.lowestApi(mod);
				}
				// where the old jar is, from Fabric: a developer's run has no single jar to replace
				Path oldJar = env.installedJar(source.modId());
				if (oldJar == null) {
					throw new UpdateFailed(source.jarName() + " is not installed as a jar here");
				}
				plan.add(String.join(SwapHelper.SEPARATOR, oldJar.toString(), file.toString(), name,
					env.folder().resolve(BACKUP).resolve(source.modId()).toString(), label(source, pick.version()), JarCheck.sha256(file)));
			}
			writePlan(plan);
			progress = 1f;
			stage = Stage.READY;
		} catch (IOException | UpdateFailed | RuntimeException e) {
			for (Path file : downloaded) {
				deleteQuietly(file);
			}
			problem = e instanceof UpdateFailed ? e.getMessage() : "the download did not finish";
			stage = Stage.FAILED;
		}
	}

	/** Writes the plan for the helper through a temporary file, so it is whole or not there. */
	private void writePlan(List<String> lines) throws IOException {
		Path plan = env.folder().resolve(SwapHelper.PLAN);
		Path temp = plan.resolveSibling(SwapHelper.PLAN + PART);
		Files.write(temp, lines, StandardCharsets.UTF_8);
		Files.move(temp, plan, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static String label(UpdateSources.Source source, LumioVersion version) {
		return (source == UpdateSources.LUMIO ? "Lumio" : source.jarName()) + " " + version.display();
	}

	private static Path part(Path file) {
		return file.resolveSibling(file.getFileName() + PART);
	}

	private Map<String, String> downloadHeaders() {
		return Map.of("User-Agent", "Lumio-Updater/" + env.installedVersion(UpdateSources.LUMIO.modId()), "Accept", "application/octet-stream");
	}

	/** @return a release's signature file, a short line kept in memory */
	private byte[] signature(GitHubRelease.Asset asset) throws IOException, UpdateFailed {
		if (asset.size() > ReleaseSignature.MOST_FILE_BYTES) {
			throw new UpdateFailed(NOT_VERIFIED);
		}
		UpdateHttp.Answer answer = http.get(URI.create(asset.url()), downloadHeaders());
		try (InputStream in = answer.body()) {
			if (answer.status() != 200 || in == null) {
				throw new IOException("GitHub answered " + answer.status());
			}
			return in.readNBytes(ReleaseSignature.MOST_FILE_BYTES);
		}
	}

	/** Downloads a release's file into a part file: at most its listed size, within the deadline. */
	private void download(GitHubRelease.Asset asset, Path part, long before, long total) throws IOException {
		long start = System.nanoTime();
		UpdateHttp.Answer answer = http.get(URI.create(asset.url()), downloadHeaders());
		try (InputStream in = answer.body()) {
			if (answer.status() != 200 || in == null) {
				throw new IOException("GitHub answered " + answer.status());
			}
			try (InputStream limited = HttpLimits.limited(in, asset.size()); OutputStream out = Files.newOutputStream(part)) {
				byte[] buffer = new byte[BUFFER];
				long read = 0;
				int count;
				while ((count = limited.read(buffer)) > 0) {
					out.write(buffer, 0, count);
					read += count;
					progress = Math.min(0.99f, (before + read) / (float) Math.max(1, total));
					if (System.nanoTime() - start > DOWNLOAD_DEADLINE_NANOS) {
						throw new IOException("The download took too long");
					}
				}
			}
		}
	}

	/** The notice's Cancel once an update is ready: the downloaded files and the plan are deleted. */
	void cancel() {
		if (stage != Stage.READY && stage != Stage.FAILED) {
			return;
		}
		for (Offer offer : offers) {
			Path file = env.folder().resolve(ReleasePicker.fileName(offer.source(), offer.pick().version(), env.minecraft()));
			deleteQuietly(file);
			deleteQuietly(part(file));
		}
		deleteQuietly(env.folder().resolve(SwapHelper.PLAN));
		stage = offers.isEmpty() ? Stage.IDLE : Stage.OFFER;
	}

	/** @return whether a previous version is kept to restore */
	boolean canRestore() {
		return !backups().isEmpty();
	}

	/** Restore previous version: the kept jars go back when the game closes; only after the player asked. @return whether anything is planned */
	boolean restore() {
		List<String> plan = new ArrayList<>();
		try {
			for (Map.Entry<UpdateSources.Source, Path> backup : backups().entrySet()) {
				Path current = env.installedJar(backup.getKey().modId());
				if (current != null) {
					String name = backup.getValue().getFileName().toString();
					plan.add(String.join(SwapHelper.SEPARATOR, current.toString(), backup.getValue().toString(), name,
						backup.getValue().getParent().toString(), "restored " + name, JarCheck.sha256(backup.getValue())));
				}
			}
			if (plan.isEmpty()) {
				return false;
			}
			Files.createDirectories(env.folder());
			writePlan(plan);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private Map<UpdateSources.Source, Path> backups() {
		Map<UpdateSources.Source, Path> found = new LinkedHashMap<>();
		for (UpdateSources.Source source : UpdateSources.all(env.addons())) {
			Path dir = env.folder().resolve(BACKUP).resolve(source.modId());
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (DirectoryStream<Path> jars = Files.newDirectoryStream(dir, "*.jar")) {
				for (Path jar : jars) {
					found.put(source, jar);
				}
			} catch (IOException ignored) {
				// no backup to offer
			}
		}
		return found;
	}

	/** @return whether a swap waits for the game to close */
	boolean planned() {
		return Files.isRegularFile(env.folder().resolve(SwapHelper.PLAN));
	}

	/** @return what the helper installed since the game last ran (each once), for a line in chat */
	List<String> installedSinceLastRun() {
		Path done = env.folder().resolve(SwapHelper.DONE);
		try {
			if (!Files.isRegularFile(done)) {
				return List.of();
			}
			List<String> lines = Files.readAllLines(done, StandardCharsets.UTF_8);
			Files.delete(done);
			return lines.size() > 4 ? lines.subList(0, 4) : lines;
		} catch (IOException e) {
			return List.of();
		}
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// it goes with the next update
		}
	}

	/** An update that must not go on, with the reason the player sees. */
	static final class UpdateFailed extends Exception {
		UpdateFailed(String reason) {
			super(reason);
		}
	}

	/** What is kept between game starts: the time of the last check, the lists and their ETags, and the versions the player skipped. */
	static final class UpdateState {
		private static final int MOST_BODY_CHARS = HttpLimits.TEXT_BYTES;
		private final Path file;
		long lastCheck;
		final Map<String, String> etags = new HashMap<>();
		final Map<String, String> bodies = new HashMap<>();
		final Map<String, String> skipped = new HashMap<>();

		private UpdateState(Path file) {
			this.file = file;
		}

		static UpdateState load(Path file) {
			UpdateState state = new UpdateState(file);
			try {
				if (Files.isRegularFile(file) && Files.size(file) < 4L * MOST_BODY_CHARS) {
					JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
					state.lastCheck = json.has("last_check") ? json.get("last_check").getAsLong() : 0;
					read(json, "etags", state.etags);
					read(json, "bodies", state.bodies);
					read(json, "skipped", state.skipped);
				}
			} catch (IOException | RuntimeException e) {
				// a broken file: a fresh start
			}
			return state;
		}

		private static void read(JsonObject json, String key, Map<String, String> into) {
			if (json.get(key) instanceof JsonObject object) {
				for (var entry : object.entrySet()) {
					if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().length() <= MOST_BODY_CHARS) {
						into.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
			}
		}

		void save() {
			JsonObject json = new JsonObject();
			json.addProperty("last_check", lastCheck);
			json.add("etags", write(etags));
			json.add("bodies", write(bodies));
			json.add("skipped", write(skipped));
			try {
				Files.createDirectories(file.getParent());
				Path temp = file.resolveSibling(file.getFileName() + ".tmp");
				Files.writeString(temp, json.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignored) {
				// checked again next time
			}
		}

		private static JsonObject write(Map<String, String> values) {
			JsonObject object = new JsonObject();
			values.forEach(object::addProperty);
			return object;
		}
	}
}
