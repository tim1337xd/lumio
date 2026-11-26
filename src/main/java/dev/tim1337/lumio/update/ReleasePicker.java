package dev.tim1337.lumio.update;

import java.util.List;

/**
 * Picks the release to offer: the newest one newer than the installed version that has the jar for the Minecraft version in use, its signature
 * beside it, and both served from the mod's own repository. Never an older or the same version. Pre-releases (GitHub's flag, or a beta in the tag)
 * only while a pre-release is installed; alphas never. Both ways of naming are understood: one release for all Minecraft versions (tag
 * v1.0.0-beta.2, files lumio-1.0.0-beta.2-26.3.jar) and the first beta's one release per Minecraft version (tag v1.0.0-beta.1+mc26.3).
 */
final class ReleasePicker {
	/** A release to offer, its jar for this Minecraft version and the jar's signature file. */
	record Pick(GitHubRelease release, LumioVersion version, GitHubRelease.Asset asset, GitHubRelease.Asset signature) {
	}

	private static final String MC_TAG = "mc";
	private static final String JAR = ".jar";

	private ReleasePicker() {
	}

	/**
	 * @param minecraft  the Minecraft version in use, "26.3"
	 * @param skipped    a version the player chose to skip, or null
	 * @param testServer the local test server of a jar that is no public release, or null: its files count as the repository's
	 * @return the release to offer, or null when there is none
	 */
	static Pick pick(List<GitHubRelease> releases, LumioVersion installed, UpdateSources.Source source, String minecraft, LumioVersion skipped,
		String testServer) {
		Pick best = null;
		for (GitHubRelease release : releases) {
			LumioVersion version = LumioVersion.parse(release.tag());
			if (version == null || version.compareTo(installed) <= 0 || !wanted(release, version, installed) || version.equals(skipped)) {
				continue;
			}
			if (best != null && version.compareTo(best.version()) <= 0) {
				continue;
			}
			String tagMinecraft = LumioVersion.buildPart(release.tag());
			if (!tagMinecraft.isEmpty() && !tagMinecraft.equals(MC_TAG + minecraft)) {
				// a release of the per-Minecraft-version kind, for another Minecraft version
				continue;
			}
			GitHubRelease.Asset asset = assetFor(release, version, source.jarName(), minecraft);
			GitHubRelease.Asset signature = asset == null ? null : named(release, asset.name() + ReleaseSignature.SUFFIX);
			if (asset != null && signature != null && fromSource(asset, source, testServer) && fromSource(signature, source, testServer)) {
				best = new Pick(release, version, asset, signature);
			}
		}
		return best;
	}

	/** @return whether a release may be offered to this installed version: a pre-release only to a pre-release, an alpha never */
	static boolean wanted(GitHubRelease release, LumioVersion version, LumioVersion installed) {
		if (version.stage() == LumioVersion.ALPHA) {
			return false;
		}
		boolean prerelease = release.prerelease() || version.stage() != LumioVersion.RELEASE;
		return !prerelease || installed.stage() != LumioVersion.RELEASE;
	}

	/** @return whether a file is served from the mod's own repository (or the test server) */
	private static boolean fromSource(GitHubRelease.Asset asset, UpdateSources.Source source, String testServer) {
		return source.ownsDownload(asset.url()) || testServer != null && asset.url().startsWith(testServer + "/");
	}

	/** @return the name the updater gives the jar in the mods folder: built here, never taken from an answer */
	static String fileName(UpdateSources.Source source, LumioVersion version, String minecraft) {
		return source.jarName() + "-" + version + "-" + minecraft + JAR;
	}

	/** @return the release's jar for this Minecraft version under either way of naming, or null when it has none */
	static GitHubRelease.Asset assetFor(GitHubRelease release, LumioVersion version, String jarName, String minecraft) {
		GitHubRelease.Asset now = named(release, jarName + "-" + version + "-" + minecraft + JAR);
		return now != null ? now : named(release, jarName + "-" + version + "+" + MC_TAG + minecraft + JAR);
	}

	private static GitHubRelease.Asset named(GitHubRelease release, String name) {
		for (GitHubRelease.Asset asset : release.assets()) {
			if (asset.name().equals(name)) {
				return asset;
			}
		}
		return null;
	}
}
