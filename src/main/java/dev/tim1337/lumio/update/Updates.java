package dev.tim1337.lumio.update;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.experimental.util.AddonUpdates;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.Threads;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.util.ModMeta;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * The updater in the game: checks once per game start, always, on the title screen, or about a minute after joining
 * a world when no title screen came first (quick play), Check now in the settings, the notice's buttons (on the title screens and the game's
 * pause menu, never in chat), and the swap when the game closes. Network and files are only touched on a thread of its own.
 */
public final class Updates {
	private static final String RELEASE_FLAG = "lumio:release";
	/** On the game's own title screen: how large the notice's layout units are in the GUI's pixels, and its distance from the corner. */
	private static final float PLAIN_SCALE = 0.5f;
	private static final float PLAIN_EDGE = 16f;
	/** How long after joining a world the check of this game start runs, when no title screen has run it yet. */
	private static final long IN_WORLD_DELAY_NANOS = 60_000_000_000L;
	private static final ExecutorService WORKER = Threads.daemonExecutor("Lumio updates");
	/** The time of the last check in the settings; made once, as the settings ask for the text every frame. */
	private static final DateTimeFormatter LAST_CHECK_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm");
	private static Updater updater;
	private static boolean checkedThisStart;
	/** When the check runs in a world (System.nanoTime), or 0 when none waits. */
	private static long checkInWorldAt;
	private static volatile List<String> installed = List.of();

	private Updates() {
	}

	/** Sets the updater up; once, while Lumio starts. Reaches nothing on the network. */
	public static void register() {
		GameEnv env = new GameEnv();
		// a debug tool may set a local test server at any time, so it is read at each check
		Supplier<String> testServer = () -> UpdateSources.testServer(env.releaseBuild());
		updater = new Updater(env, new UpdateHttp.Network(testServer), testServer);
		WORKER.execute(() -> installed = updater.installedSinceLastRun());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> startHelper());
		ClientTickEvents.END_CLIENT_TICK.register(Updates::tick);
		// the game's own title screen (with Lumio's main menu off) gets the check and the notice too, at its top right; the game's pause menu
		// only the notice, so a check made in a world shows there
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			boolean title = screen.getClass() == TitleScreen.class;
			if (!title && screen.getClass() != PauseScreen.class) {
				return;
			}
			if (title) {
				titleScreenShown();
			}
			UpdateNotice notice = new UpdateNotice();
			ScreenEvents.afterExtract(screen).register((shown, graphics, mouseX, mouseY, delta) -> {
				if (!UpdateNotice.shown()) {
					return;
				}
				Ui ui = new Ui(graphics, client.font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), delta, true);
				graphics.pose().pushMatrix();
				graphics.pose().scale(PLAIN_SCALE, PLAIN_SCALE);
				Ui.setZoom(PLAIN_SCALE);
				notice.draw(ui, width / PLAIN_SCALE - PLAIN_EDGE, PLAIN_EDGE, Math.round(mouseX / PLAIN_SCALE), Math.round(mouseY / PLAIN_SCALE));
				Ui.setZoom(1f);
				graphics.pose().popMatrix();
			});
			ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> !notice.click(event.x() / PLAIN_SCALE, event.y() / PLAIN_SCALE));
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!checkedThisStart && checkInWorldAt == 0L) {
				// with quick play no title screen may ever come: the check of this game start runs a minute into the world instead
				checkInWorldAt = Math.max(1L, System.nanoTime() + IN_WORLD_DELAY_NANOS);
			}
			List<String> lines = installed;
			installed = List.of();
			for (String line : lines) {
				LumioChat.info(Component.literal(line.startsWith("restored ") ? "Restored the previous version (" + line.substring(9) + ")" : "Updated to " + line));
			}
		});
	}

	/** The title screen is shown: the check of this game start, if the player turned it on. */
	/** @return whether this is a release build (only such a jar checks for updates) */
	public static boolean releaseBuild() {
		return new GameEnv().releaseBuild();
	}

	/** @return the page of a release of Lumio or a known add-on on GitHub, or null for a mod it does not know */
	public static String releasePage(String modId, String version) {
		return UpdateSources.releasePage(modId, version);
	}

	public static void titleScreenShown() {
		checkOnce();
	}

	/** In a world: the check of this game start once a minute has passed since joining, if no title screen ran it yet. */
	private static void tick(Minecraft client) {
		if (checkInWorldAt == 0L || client.level == null || System.nanoTime() - checkInWorldAt < 0L) {
			return;
		}
		checkInWorldAt = 0L;
		checkOnce();
	}

	/** The check of this game start: always on, at most once per game start, wherever it comes from. Downloads only on Update. */
	private static void checkOnce() {
		if (updater == null || checkedThisStart) {
			return;
		}
		checkedThisStart = true;
		checkInWorldAt = 0L;
		if (updater.mayCheck(false, true)) {
			WORKER.execute(() -> updater.check(false, true));
		}
	}

	/** Check now in the settings: one check, also with the check off (never in a developer's build). */
	public static void checkNow() {
		if (updater == null) {
			return;
		}
		WORKER.execute(() -> updater.check(true, false));
	}

	/** @return whether this game's jar may check at all (no developer's build) */
	public static boolean available() {
		return updater != null && updater.mayCheck(true, false);
	}

	/** The notice's Update: downloads and checks the offered jars; the only way anything is downloaded. */
	public static void update() {
		if (updater != null && updater.stage() == Updater.Stage.OFFER) {
			WORKER.execute(updater::update);
		}
	}

	public static void later() {
		updater.later();
	}

	public static void skip() {
		WORKER.execute(updater::skip);
	}

	public static void cancel() {
		WORKER.execute(updater::cancel);
	}

	public static boolean canRestore() {
		return updater != null && updater.canRestore();
	}

	/** Restore previous version: planned for when the game closes, only after the player pressed it. */
	public static void restore() {
		if (updater != null) {
			WORKER.execute(() -> {
				if (updater.restore()) {
					Logs.info("The previous version is put back when the game closes");
				}
			});
		}
	}

	/** @return the updater for the notice, or null before Lumio started */
	static Updater updater() {
		return updater;
	}

	/** @return the time of the last check and what it found, for the settings */
	public static String lastCheckText() {
		if (updater == null || !updater.mayCheck(true, false)) {
			return "Not in a developer's build";
		}
		long at = updater.lastCheck();
		if (at == 0) {
			return "Never";
		}
		String when = LAST_CHECK_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()));
		String result = updater.lastResult();
		return result.isEmpty() ? when : when + " · " + result;
	}

	/** The game closes: with an update ready (or a restore asked for), the helper is started to swap the jars once the game has ended. */
	private static void startHelper() {
		if (updater == null || !updater.planned()) {
			return;
		}
		Path folder = new GameEnv().folder();
		try {
			Path helper = folder.resolve("helper");
			Path classFile = helper.resolve(SwapHelper.class.getName().replace('.', '/') + ".class");
			Files.createDirectories(classFile.getParent());
			// the helper runs from a copy of its one class, so no jar it moves is held open by it
			try (InputStream in = SwapHelper.class.getResourceAsStream(SwapHelper.class.getSimpleName() + ".class")) {
				if (in == null) {
					throw new IOException("the helper class is missing");
				}
				Files.copy(in, classFile, StandardCopyOption.REPLACE_EXISTING);
			}
			String java = ProcessHandle.current().info().command()
				.orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
			new ProcessBuilder(java, "-cp", helper.toString(), SwapHelper.class.getName(), Long.toString(ProcessHandle.current().pid()),
				folder.resolve(SwapHelper.PLAN).toString())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			Logs.info("The update is installed once the game has closed");
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not start the update's helper; it is tried again when the game closes next time", e);
		}
	}

	/** The game and its mods as Fabric knows them. */
	private static final class GameEnv implements Updater.Env {
		@Override
		public boolean releaseBuild() {
			Optional<ModContainer> lumio = FabricLoader.getInstance().getModContainer(UpdateSources.LUMIO.modId());
			return lumio.isPresent() && ModMeta.flag(lumio.get().getMetadata(), RELEASE_FLAG);
		}

		@Override
		public String installedVersion(String modId) {
			return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(null);
		}

		@Override
		public Path installedJar(String modId) {
			Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
			if (container.isEmpty() || container.get().getOrigin().getKind() != ModOrigin.Kind.PATH) {
				return null;
			}
			List<Path> paths = container.get().getOrigin().getPaths();
			if (paths.size() != 1) {
				return null;
			}
			Path jar = paths.getFirst().toAbsolutePath().normalize();
			return Files.isRegularFile(jar) && jar.getFileName().toString().endsWith(".jar") ? jar : null;
		}

		@Override
		public String minecraft() {
			// the Minecraft line the jars are named by: 26.3 also for a 26.3.1
			String version = installedVersion("minecraft");
			String[] parts = version == null ? new String[0] : version.split("\\.");
			return parts.length >= 2 ? parts[0] + "." + parts[1] : String.valueOf(version);
		}

		@Override
		public int java() {
			return Runtime.version().feature();
		}

		@Override
		public String loader() {
			return installedVersion("fabricloader");
		}

		@Override
		public String api() {
			return installedVersion("lumio-api");
		}

		@Override
		public Path folder() {
			return FabricLoader.getInstance().getGameDir().resolve("lumio").resolve("updates");
		}

		@Override
		public List<AddonUpdates.Source> addons() {
			return AddonUpdates.sources();
		}

		@Override
		public long now() {
			return System.currentTimeMillis();
		}

		@Override
		public String releaseKey() {
			return UpdateSources.releaseKey(releaseBuild());
		}
	}
}
