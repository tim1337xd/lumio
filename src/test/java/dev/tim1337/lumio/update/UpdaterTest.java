package dev.tim1337.lumio.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.util.AddonUpdates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The updater against a fake GitHub: a developer's build reaches nothing; a check asks the API only, never downloads; only Update downloads, and a
 * file that does not fit (checksum, signature, version) or does not arrive whole is deleted, with no part file left and the installed jars
 * untouched; the swap is planned for the game's close; the add-on fits Lumio's new API.
 */
class UpdaterTest {
	private static final String LUMIO_LIST = "https://api.github.com/repos/tim1337xd/lumio/releases?per_page=20";
	private static final String ADDON_LIST = "https://api.github.com/repos/tim1337xd/lumio-skyblock/releases?per_page=20";
	private static final String TAG = "v1.0.0-beta.3";
	private static final String NEW_LUMIO = "lumio-1.0.0-beta.3-26.3.jar";
	private static final String ETAG = "\"etag-1\"";
	/** The test's own release keys: releases are signed with the first, the fake game knows its public half. */
	private static KeyPair key;
	private static KeyPair otherKey;

	@TempDir
	Path dir;

	private final FakeHttp http = new FakeHttp();
	private FakeEnv env;

	/** How a test's release is broken, if at all. */
	private enum Broken {
		NOTHING, DIGEST, SIGNATURE, OTHER_KEY, NO_SIGNATURE
	}

	@BeforeAll
	static void keys() throws Exception {
		key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		otherKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
	}

	@BeforeEach
	void setUp() throws Exception {
		env = new FakeEnv(dir, Base64.getEncoder().encodeToString(key.getPublic().getEncoded()));
		Files.createDirectories(dir.resolve("mods"));
		Files.writeString(dir.resolve("mods").resolve("lumio-1.0.0-beta.2-26.3.jar"), "old lumio");
		Files.writeString(dir.resolve("mods").resolve("lumio-skyblock-1.0.0-beta.2-26.3.jar"), "old add-on");
	}

	private static byte[] sign(byte[] data, KeyPair with) throws Exception {
		Signature signer = Signature.getInstance("Ed25519");
		signer.initSign(with.getPrivate());
		signer.update(data);
		return Base64.getEncoder().encode(signer.sign());
	}

	/** A release of a mod in its own repository, with its jar for 26.3 and the jar's signature, broken as asked. */
	private void release(String list, String repository, String jarName, byte[] jar, Broken broken) throws Exception {
		String name = jarName + "-1.0.0-beta.3-26.3.jar";
		String base = "https://github.com/tim1337xd/" + repository + "/releases/download/" + TAG + "/";
		String digest = broken == Broken.DIGEST ? "0".repeat(64) : TestJars.sha256(jar);
		byte[] signed = broken == Broken.SIGNATURE ? jar.clone() : jar;
		if (broken == Broken.SIGNATURE) {
			signed[0] ^= 1;
		}
		byte[] signature = sign(signed, broken == Broken.OTHER_KEY ? otherKey : key);
		String signatureAsset = broken == Broken.NO_SIGNATURE ? "" : ",{\"name\":\"" + name + ".sig\",\"size\":" + signature.length + ",\"browser_download_url\":\""
			+ base + name + ".sig\"}";
		String json = "[{\"tag_name\":\"" + TAG + "\",\"prerelease\":true,\"html_url\":\"https://github.com/tim1337xd/" + repository + "/releases/tag/" + TAG + "\","
			+ "\"body\":\"Third beta.\",\"assets\":[{\"name\":\"" + name + "\",\"size\":" + jar.length + ",\"browser_download_url\":\"" + base + name
			+ "\",\"digest\":\"sha256:" + digest + "\"}" + signatureAsset + "]}]";
		http.answers.put(list, json.getBytes(StandardCharsets.UTF_8));
		http.answers.put(base + name, jar);
		http.answers.put(base + name + ".sig", signature);
	}

	private void lumioRelease(byte[] jar, Broken broken) throws Exception {
		release(LUMIO_LIST, "lumio", "lumio", jar, broken);
	}

	private static byte[] lumioJar(String version, String api) throws Exception {
		return TestJars.jar(TestJars.modJson("lumio", version, "~26.3", api));
	}

	private static byte[] lumioJar() throws Exception {
		return lumioJar("1.0.0-beta.3", ">=1.17.0");
	}

	private Updater updater() {
		return new Updater(env, http, () -> null);
	}

	private Updater updated() {
		Updater updater = updater();
		updater.check(true, false);
		updater.update();
		return updater;
	}

	/** Nothing of a failed update is left: no jar, no part file, no plan, and the installed jar as it was. */
	private void nothingLeft(Updater updater) throws Exception {
		assertEquals(Updater.Stage.FAILED, updater.stage());
		assertFalse(Files.exists(dir.resolve("updates").resolve(NEW_LUMIO)));
		assertFalse(Files.exists(dir.resolve("updates").resolve(NEW_LUMIO + ".part")), "no half file left");
		assertFalse(updater.planned(), "the installed version stays");
		assertEquals("old lumio", Files.readString(dir.resolve("mods").resolve("lumio-1.0.0-beta.2-26.3.jar")));
	}

	@Test
	void aDevelopersBuildNeverChecks() throws Exception {
		lumioRelease(lumioJar(), Broken.NOTHING);
		env.release = false;
		Updater updater = updater();
		updater.check(true, true);
		assertTrue(http.asked.isEmpty(), "a jar that is no public release asks nothing");
		env.release = true;
		env.versions.put("lumio", "1.0.1-alpha+build.31");
		updater.check(true, true);
		assertTrue(http.asked.isEmpty(), "an alpha with its build asks nothing");
	}

	@Test
	void anAddOnBuiltByADeveloperIsNeverReplaced() throws Exception {
		env.addons.add(new AddonUpdates.Source("lumio-skyblock", "tim1337xd", "lumio-skyblock", "lumio-skyblock"));
		env.versions.put("lumio-skyblock", "1.0.1-alpha+build.12");
		release(ADDON_LIST, "lumio-skyblock", "lumio-skyblock", TestJars.jar(TestJars.modJson("lumio-skyblock", "1.0.0-beta.3", "~26.3", ">=1.17.0")),
			Broken.NOTHING);
		updater().check(true, false);
		assertFalse(http.asked.contains(ADDON_LIST));
	}

	@Test
	void aCheckOnlyAsksTheApiAndDownloadsNothing() throws Exception {
		lumioRelease(lumioJar(), Broken.NOTHING);
		Updater updater = updater();
		updater.check(false, true);
		assertEquals(Updater.Stage.OFFER, updater.stage());
		assertEquals("1.0.0-beta.3", updater.offers().getFirst().pick().version().toString());
		assertEquals(List.of(LUMIO_LIST), http.asked, "one request, to the API");
		assertTrue(http.headers.getFirst().get("User-Agent").startsWith("Lumio-Updater/"));
		assertFalse(Files.exists(dir.resolve("updates").resolve(NEW_LUMIO)), "nothing downloaded without Update");
		assertFalse(updater.planned());
	}

	@Test
	void nothingPersonalIsSent() throws Exception {
		lumioRelease(lumioJar(), Broken.NOTHING);
		updated();
		for (Map<String, String> sent : http.headers) {
			assertEquals(List.of("Accept", "User-Agent"), sent.keySet().stream().filter(name -> !name.equals("If-None-Match")).sorted().toList(),
				"only what GitHub needs: no token, name, account or id");
			assertEquals("Lumio-Updater/1.0.0-beta.2", sent.get("User-Agent"));
		}
		for (String asked : http.asked) {
			assertFalse(asked.contains("?") && !asked.endsWith("?per_page=20"), "nothing added to an address");
		}
	}

	@Test
	void aRecentCheckIsNotRepeatedAndAnUnchangedListComesFromItsEtag() throws Exception {
		lumioRelease(lumioJar(), Broken.NOTHING);
		updater().check(false, true);
		http.asked.clear();
		updater().check(false, true);
		assertTrue(http.asked.isEmpty(), "within six hours: no request");
		env.now += Updater.CHECK_EVERY_MILLIS + 1;
		http.notModified = true;
		Updater later = updater();
		later.check(false, true);
		assertEquals(ETAG, http.headers.getLast().get("If-None-Match"));
		assertEquals(Updater.Stage.OFFER, later.stage(), "the saved list answers a 304");
	}

	@Test
	void updateDownloadsVerifiesAndPlansTheSwap() throws Exception {
		lumioRelease(lumioJar(), Broken.NOTHING);
		Updater updater = updated();
		assertEquals(Updater.Stage.READY, updater.stage(), updater.problem());
		Path downloaded = dir.resolve("updates").resolve(NEW_LUMIO);
		assertTrue(Files.exists(downloaded));
		assertFalse(Files.exists(dir.resolve("updates").resolve(NEW_LUMIO + ".part")));
		assertTrue(updater.planned());
		String[] plan = Files.readString(dir.resolve("updates").resolve(SwapHelper.PLAN)).strip().split(SwapHelper.SEPARATOR);
		assertEquals(SwapHelper.FIELDS, plan.length);
		assertEquals(dir.resolve("mods").resolve("lumio-1.0.0-beta.2-26.3.jar").toString(), plan[0]);
		assertEquals(NEW_LUMIO, plan[2], "the name is the updater's own");
		assertEquals("Lumio 1.0.0 Beta 3", plan[4]);
		assertEquals(TestJars.sha256(lumioJar()), plan[5], "the helper checks the jar once more");
		updater.cancel();
		assertFalse(Files.exists(downloaded), "Cancel deletes the download");
		assertFalse(updater.planned());
	}

	@Test
	void aWrongChecksumIsDeleted() throws Exception {
		lumioRelease(lumioJar(), Broken.DIGEST);
		Updater updater = updated();
		assertEquals("the checksum does not match", updater.problem());
		nothingLeft(updater);
	}

	@Test
	void aWrongSignatureIsDeleted() throws Exception {
		lumioRelease(lumioJar(), Broken.SIGNATURE);
		Updater updater = updated();
		assertEquals(Updater.NOT_VERIFIED, updater.problem());
		nothingLeft(updater);
	}

	@Test
	void aSignatureOfAnotherKeyIsDeleted() throws Exception {
		lumioRelease(lumioJar(), Broken.OTHER_KEY);
		Updater updater = updated();
		assertEquals(Updater.NOT_VERIFIED, updater.problem());
		nothingLeft(updater);
	}

	@Test
	void anUnsignedReleaseIsNotOffered() throws Exception {
		lumioRelease(lumioJar(), Broken.NO_SIGNATURE);
		Updater updater = updater();
		updater.check(true, false);
		assertEquals(Updater.Stage.IDLE, updater.stage());
	}

	@Test
	void anOlderJarUnderANewerTagIsDeleted() throws Exception {
		// a signed jar of an older version, put into a newer release: its own version gives it away
		lumioRelease(lumioJar("1.0.0-beta.1", ">=1.17.0"), Broken.NOTHING);
		Updater updater = updated();
		assertEquals("it is another version than the release", updater.problem());
		nothingLeft(updater);
	}

	@Test
	void anInterruptedDownloadLeavesNothing() throws Exception {
		byte[] jar = lumioJar();
		lumioRelease(jar, Broken.NOTHING);
		http.breakAfter = jar.length / 2;
		Updater updater = updated();
		assertEquals("the download did not finish", updater.problem());
		nothingLeft(updater);
	}

	@Test
	void aShortDownloadIsDeleted() throws Exception {
		byte[] jar = lumioJar();
		lumioRelease(jar, Broken.NOTHING);
		http.answers.put("https://github.com/tim1337xd/lumio/releases/download/" + TAG + "/" + NEW_LUMIO, java.util.Arrays.copyOf(jar, jar.length - 10));
		Updater updater = updated();
		assertEquals("the file's size is not the release's", updater.problem());
		nothingLeft(updater);
	}

	@Test
	void updateAllFitsTheAddOnToLumiosNewApi() throws Exception {
		env.addons.add(new AddonUpdates.Source("lumio-skyblock", "tim1337xd", "lumio-skyblock", "lumio-skyblock"));
		lumioRelease(lumioJar("1.0.0-beta.3", ">=1.18.0"), Broken.NOTHING);
		release(ADDON_LIST, "lumio-skyblock", "lumio-skyblock", TestJars.jar(TestJars.modJson("lumio-skyblock", "1.0.0-beta.3", "~26.3", ">=1.18.0 <2.0.0")),
			Broken.NOTHING);
		Updater updater = updater();
		updater.check(true, false);
		assertEquals(2, updater.offers().size());
		updater.update();
		assertEquals(Updater.Stage.READY, updater.stage(), updater.problem());
		assertEquals(2, Files.readAllLines(dir.resolve("updates").resolve(SwapHelper.PLAN)).size());
	}

	@Test
	void theAddOnIsVerifiedTheSameWay() throws Exception {
		env.addons.add(new AddonUpdates.Source("lumio-skyblock", "tim1337xd", "lumio-skyblock", "lumio-skyblock"));
		release(ADDON_LIST, "lumio-skyblock", "lumio-skyblock", TestJars.jar(TestJars.modJson("lumio-skyblock", "1.0.0-beta.3", "~26.3", ">=1.17.0")),
			Broken.SIGNATURE);
		Updater updater = updated();
		assertEquals(Updater.NOT_VERIFIED, updater.problem());
		assertFalse(updater.planned());
	}

	@Test
	void theAddOnAloneNeedsTheNewLumio() throws Exception {
		env.addons.add(new AddonUpdates.Source("lumio-skyblock", "tim1337xd", "lumio-skyblock", "lumio-skyblock"));
		release(ADDON_LIST, "lumio-skyblock", "lumio-skyblock", TestJars.jar(TestJars.modJson("lumio-skyblock", "1.0.0-beta.3", "~26.3", ">=1.18.0 <2.0.0")),
			Broken.NOTHING);
		Updater updater = updated();
		assertEquals("it needs another version of Lumio", updater.problem());
	}

	@Test
	void skippedVersionsAreNotOfferedAgain() throws Exception {
		lumioRelease(lumioJar(), Broken.NOTHING);
		Updater updater = updater();
		updater.check(true, false);
		updater.skip();
		Updater again = updater();
		again.check(true, false);
		assertEquals(Updater.Stage.IDLE, again.stage());
	}

	/** A GitHub of the test's own: answers by address, and keeps what was asked; a download may break off midway. */
	static final class FakeHttp implements UpdateHttp {
		final Map<String, byte[]> answers = new HashMap<>();
		final List<String> asked = new ArrayList<>();
		final List<Map<String, String>> headers = new ArrayList<>();
		boolean notModified;
		/** After this many bytes of a jar the connection breaks, or -1. */
		int breakAfter = -1;

		@Override
		public Answer get(URI uri, Map<String, String> sent) {
			asked.add(uri.toString());
			headers.add(Map.copyOf(sent));
			if (notModified && sent.containsKey("If-None-Match")) {
				return new Answer(304, ETAG, null);
			}
			byte[] body = answers.get(uri.toString());
			if (body == null) {
				return new Answer(404, "", new ByteArrayInputStream(new byte[0]));
			}
			InputStream in = new ByteArrayInputStream(body);
			return new Answer(200, ETAG, breakAfter >= 0 && uri.toString().endsWith(".jar") ? breaking(in, breakAfter) : in);
		}

		private static InputStream breaking(InputStream in, int after) {
			return new InputStream() {
				private int read;

				@Override
				public int read() throws IOException {
					if (read++ >= after) {
						throw new IOException("connection reset");
					}
					return in.read();
				}
			};
		}
	}

	/** A game of the test's own, its folder a temporary one. */
	static final class FakeEnv implements Updater.Env {
		final Path dir;
		final String releaseKey;
		final Map<String, String> versions = new HashMap<>(Map.of("lumio", "1.0.0-beta.2", "lumio-skyblock", "1.0.0-beta.2"));
		final List<AddonUpdates.Source> addons = new ArrayList<>();
		boolean release = true;
		long now = 1_000_000L;

		FakeEnv(Path dir, String releaseKey) {
			this.dir = dir;
			this.releaseKey = releaseKey;
		}

		@Override
		public boolean releaseBuild() {
			return release;
		}

		@Override
		public String installedVersion(String modId) {
			return versions.get(modId);
		}

		@Override
		public Path installedJar(String modId) {
			return dir.resolve("mods").resolve(modId + "-1.0.0-beta.2-26.3.jar");
		}

		@Override
		public String minecraft() {
			return "26.3";
		}

		@Override
		public int java() {
			return 25;
		}

		@Override
		public String loader() {
			return "0.20.1";
		}

		@Override
		public String api() {
			return "1.17.0";
		}

		@Override
		public Path folder() {
			return dir.resolve("updates");
		}

		@Override
		public List<AddonUpdates.Source> addons() {
			return addons;
		}

		@Override
		public long now() {
			return now;
		}

		@Override
		public String releaseKey() {
			return releaseKey;
		}
	}
}
