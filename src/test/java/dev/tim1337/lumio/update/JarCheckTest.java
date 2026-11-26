package dev.tim1337.lumio.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A downloaded jar is used only when its size, its checksum and its mod file (the same mod and version) fit; the add-on also needs the Lumio API that will be there. */
class JarCheckTest {
	@TempDir
	Path dir;

	private static final JarCheck.Expected LUMIO = new JarCheck.Expected("lumio", LumioVersion.parse("1.0.0-beta.3"), "26.3", 25, "0.20.1", null);

	private Path file(byte[] data) throws Exception {
		Path file = dir.resolve("lumio-1.0.0-beta.3-26.3.jar");
		Files.write(file, data);
		return file;
	}

	private static GitHubRelease.Asset asset(byte[] data, String sha) {
		return new GitHubRelease.Asset("lumio-1.0.0-beta.3-26.3.jar", data.length, "https://github.com/x", sha.isEmpty() ? "" : "sha256:" + sha);
	}

	@Test
	void aFittingJar() throws Exception {
		byte[] data = TestJars.jar(TestJars.modJson("lumio", "1.0.0-beta.3", "~26.3", ">=1.18.0"));
		assertNull(JarCheck.problem(file(data), asset(data, TestJars.sha256(data)), LUMIO));
	}

	@Test
	void aWrongChecksumIsRejected() throws Exception {
		byte[] data = TestJars.jar(TestJars.modJson("lumio", "1.0.0-beta.3", "~26.3", null));
		assertEquals("the checksum does not match", JarCheck.problem(file(data), asset(data, "0".repeat(64)), LUMIO));
		assertEquals("the release lists no checksum", JarCheck.problem(file(data), asset(data, ""), LUMIO));
		GitHubRelease.Asset longer = new GitHubRelease.Asset("x.jar", data.length + 1, "https://github.com/x", "sha256:" + TestJars.sha256(data));
		assertEquals("the file's size is not the release's", JarCheck.problem(file(data), longer, LUMIO));
	}

	@Test
	void theModFileMustFit() throws Exception {
		byte[] other = TestJars.jar(TestJars.modJson("other", "1.0.0", "~26.3", null));
		assertEquals("it is another mod", JarCheck.problem(file(other), asset(other, TestJars.sha256(other)), LUMIO));
		byte[] older = TestJars.jar(TestJars.modJson("lumio", "1.0.0-beta.3", "~26.2", null));
		assertEquals("it is not made for Minecraft 26.3", JarCheck.problem(file(older), asset(older, TestJars.sha256(older)), LUMIO));
		byte[] olderVersion = TestJars.jar(TestJars.modJson("lumio", "1.0.0-beta.1", "~26.3", null));
		assertEquals("it is another version than the release", JarCheck.problem(file(olderVersion), asset(olderVersion, TestJars.sha256(olderVersion)), LUMIO),
			"an older jar under a newer release's name");
		byte[] noZip = "not a jar".getBytes();
		assertEquals("the file could not be read", JarCheck.problem(file(noZip), asset(noZip, TestJars.sha256(noZip)), LUMIO));
	}

	@Test
	void theAddOnNeedsItsLumioApi() throws Exception {
		byte[] addon = TestJars.jar(TestJars.modJson("lumio-skyblock", "1.0.0-beta.3", "~26.3", ">=1.18.0 <2.0.0"));
		GitHubRelease.Asset asset = asset(addon, TestJars.sha256(addon));
		assertEquals("it needs another version of Lumio", JarCheck.problem(file(addon), asset,
			new JarCheck.Expected("lumio-skyblock", LumioVersion.parse("1.0.0-beta.3"), "26.3", 25, "0.20.1", "1.17.0")));
		assertNull(JarCheck.problem(file(addon), asset, new JarCheck.Expected("lumio-skyblock", LumioVersion.parse("1.0.0-beta.3"), "26.3", 25, "0.20.1", "1.18.0")),
			"with Lumio's update it fits");
	}
}
