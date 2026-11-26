package dev.tim1337.lumio.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The release to offer: newer, signed, from its own repository, with the jar for this Minecraft version, under the new and the first beta's
 * naming; pre-releases only to a pre-release; never older; the file name built by the updater.
 */
class ReleasePickerTest {
	private static final UpdateSources.Source LUMIO = UpdateSources.LUMIO;
	private static final UpdateSources.Source ADDON = UpdateSources.KNOWN_ADDONS.getFirst();
	private static final LumioVersion BETA_1 = LumioVersion.parse("1.0.0-beta.1");
	private static final LumioVersion RELEASE = LumioVersion.parse("1.0.0");

	/** A release of Lumio's repository whose jars each have a signature beside them. */
	static GitHubRelease release(String tag, boolean pre, String... jars) {
		return release("lumio", tag, pre, true, jars);
	}

	static GitHubRelease release(String repository, String tag, boolean pre, boolean signed, String... jars) {
		List<GitHubRelease.Asset> list = new ArrayList<>();
		String base = "https://github.com/tim1337xd/" + repository + "/releases/download/" + tag + "/";
		for (String name : jars) {
			list.add(new GitHubRelease.Asset(name, 100, base + name, "sha256:" + "0".repeat(64)));
			if (signed) {
				list.add(new GitHubRelease.Asset(name + ReleaseSignature.SUFFIX, 88, base + name + ReleaseSignature.SUFFIX, ""));
			}
		}
		return new GitHubRelease(tag, pre, "https://github.com/tim1337xd/" + repository + "/releases/tag/" + tag, "", list);
	}

	private static ReleasePicker.Pick pick(List<GitHubRelease> releases, LumioVersion installed, UpdateSources.Source source, String minecraft) {
		return ReleasePicker.pick(releases, installed, source, minecraft, null, null);
	}

	@Test
	void theJarForThisMinecraftVersionAndItsSignature() {
		List<GitHubRelease> releases = List.of(release("v1.0.0-beta.2", true, "lumio-1.0.0-beta.2-26.2.jar", "lumio-1.0.0-beta.2-26.3.jar"));
		ReleasePicker.Pick pick = pick(releases, BETA_1, LUMIO, "26.3");
		assertEquals("lumio-1.0.0-beta.2-26.3.jar", pick.asset().name());
		assertEquals("lumio-1.0.0-beta.2-26.3.jar.sig", pick.signature().name());
		assertNull(pick(releases, BETA_1, LUMIO, "26.4"), "no jar for this Minecraft version: nothing offered");
	}

	@Test
	void anUnsignedReleaseIsNeverOffered() {
		List<GitHubRelease> releases = List.of(release("lumio", "v1.0.0-beta.2", true, false, "lumio-1.0.0-beta.2-26.3.jar"));
		assertNull(pick(releases, BETA_1, LUMIO, "26.3"));
	}

	@Test
	void onlyFilesOfTheModsOwnRepository() {
		List<GitHubRelease> elsewhere = List.of(release("someone-else", "v1.0.0-beta.2", true, true, "lumio-1.0.0-beta.2-26.3.jar"));
		assertNull(pick(elsewhere, BETA_1, LUMIO, "26.3"), "Lumio's release with files of another repository");
		List<GitHubRelease> lumios = List.of(release("lumio", "v1.0.0-beta.2", true, true, "lumio-skyblock-1.0.0-beta.2-26.3.jar"));
		assertNull(pick(lumios, BETA_1, ADDON, "26.3"), "the add-on's jar from Lumio's repository");
		List<GitHubRelease> own = List.of(release("Lumio-SkyBlock", "v1.0.0-beta.2", true, true, "lumio-skyblock-1.0.0-beta.2-26.3.jar"));
		assertEquals("lumio-skyblock-1.0.0-beta.2-26.3.jar", pick(own, BETA_1, ADDON, "26.3").asset().name(), "GitHub's own capitals");
	}

	@Test
	void pathTricksInNamesAreNeverPicked() {
		List<GitHubRelease> releases = List.of(release("v1.0.0-beta.2", true, "../lumio-1.0.0-beta.2-26.3.jar", "lumio-1.0.0-beta.2-26.3.jar/../../x.jar",
			"..\\lumio-1.0.0-beta.2-26.3.jar"));
		assertNull(pick(releases, BETA_1, LUMIO, "26.3"), "only the exact name the updater builds is taken");
		assertEquals("lumio-1.0.0-beta.2-26.3.jar", ReleasePicker.fileName(LUMIO, LumioVersion.parse("1.0.0-beta.2"), "26.3"));
	}

	@Test
	void theFirstBetasNaming() {
		List<GitHubRelease> releases = List.of(release("v1.0.0-beta.1+mc26.2", true, "lumio-1.0.0-beta.1+mc26.2.jar"),
			release("v1.0.0-beta.1+mc26.3", true, "lumio-1.0.0-beta.1+mc26.3.jar"));
		LumioVersion alpha = LumioVersion.parse("1.0.0-alpha");
		assertEquals("lumio-1.0.0-beta.1+mc26.3.jar", pick(releases, alpha, LUMIO, "26.3").asset().name());
		assertEquals("lumio-1.0.0-beta.1+mc26.2.jar", pick(releases, alpha, LUMIO, "26.2").asset().name());
	}

	@Test
	void preReleasesOnlyToAPreRelease() {
		List<GitHubRelease> flagged = List.of(release("v1.0.1-beta.1", true, "lumio-1.0.1-beta.1-26.3.jar"));
		assertNull(pick(flagged, RELEASE, LUMIO, "26.3"), "a release installed: no beta");
		assertEquals("1.0.1-beta.1", pick(flagged, BETA_1, LUMIO, "26.3").version().toString(), "a beta installed");
		List<GitHubRelease> unflagged = List.of(release("v1.0.1-beta.1", false, "lumio-1.0.1-beta.1-26.3.jar"));
		assertNull(pick(unflagged, RELEASE, LUMIO, "26.3"), "a beta tag counts even without GitHub's flag");
		List<GitHubRelease> alpha = List.of(release("v1.0.1-alpha", true, "lumio-1.0.1-alpha-26.3.jar"));
		assertNull(pick(alpha, BETA_1, LUMIO, "26.3"), "an alpha never");
	}

	@Test
	void neverOlderAndTheNewestNotSkipped() {
		List<GitHubRelease> releases = List.of(release("v1.0.0", false, "lumio-1.0.0-26.3.jar"), release("v1.0.0-beta.3", true, "lumio-1.0.0-beta.3-26.3.jar"),
			release("v0.9.0", false, "lumio-0.9.0-26.3.jar"));
		assertEquals("1.0.0", pick(releases, BETA_1, LUMIO, "26.3").version().toString());
		assertEquals("1.0.0-beta.3", ReleasePicker.pick(releases, BETA_1, LUMIO, "26.3", RELEASE, null).version().toString(), "1.0.0 skipped");
		assertNull(pick(releases, RELEASE, LUMIO, "26.3"), "nothing newer: never the older 0.9.0 nor the same version");
		assertNull(pick(releases, LumioVersion.parse("1.0.1"), LUMIO, "26.3"), "an older release is never offered");
	}

	@Test
	void theAddOnsJars() {
		List<GitHubRelease> releases = List.of(release("lumio-skyblock", "v1.0.0-beta.2", true, true, "lumio-skyblock-1.0.0-beta.2-26.3.jar"));
		assertEquals("lumio-skyblock-1.0.0-beta.2-26.3.jar", pick(releases, BETA_1, ADDON, "26.3").asset().name());
	}

	@Test
	void parsedFromGitHubsAnswer() {
		String json = "[{\"tag_name\":\"v1.0.0-beta.2\",\"prerelease\":true,\"draft\":false,\"html_url\":\"https://github.com/tim1337xd/Lumio/releases/tag/v1.0.0-beta.2\","
			+ "\"body\":\"Second beta.\n\n## What's Changed\n- feat(Sky): clouds\",\"assets\":[{\"name\":\"lumio-1.0.0-beta.2-26.3.jar\",\"size\":2048,"
			+ "\"browser_download_url\":\"https://github.com/tim1337xd/Lumio/releases/download/v1.0.0-beta.2/lumio-1.0.0-beta.2-26.3.jar\","
			+ "\"digest\":\"sha256:" + "ab".repeat(32) + "\"}]},{\"tag_name\":\"v9.9.9\",\"draft\":true,\"assets\":[]}, 5, \"x\"]";
		List<GitHubRelease> releases = GitHubRelease.parseList(json);
		assertEquals(1, releases.size(), "drafts and malformed entries left out");
		assertEquals("ab".repeat(32), releases.getFirst().assets().getFirst().sha256());
		assertEquals(List.of("Second beta.", "Sky: clouds"), releases.getFirst().firstLines(3), "headings left out, the kind of change too");
		assertEquals(0, GitHubRelease.parseList("not json").size());
	}
}
