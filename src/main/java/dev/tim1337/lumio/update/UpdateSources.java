package dev.tim1337.lumio.update;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.util.AddonUpdates;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Where updates come from: Lumio's own releases and those of the add-ons that offer theirs, each only from a GitHub repository written here. An
 * add-on naming any other repository takes no part.
 */
final class UpdateSources {
	/** A mod's releases: its mod id, the repository and the start of its jars' names. */
	record Source(String modId, String owner, String repository, String jarName) {
		String releasesPath() {
			return releasesPrefix() + "?per_page=" + PER_PAGE;
		}

		/** @return the API's path of the repository's releases, in lower case */
		String releasesPrefix() {
			return ("/repos/" + owner + "/" + repository + "/releases").toLowerCase(Locale.ROOT);
		}

		/** @return the start of the repository's release files' paths on github.com, in lower case */
		String downloadPrefix() {
			return ("/" + owner + "/" + repository + "/releases/download/").toLowerCase(Locale.ROOT);
		}

		/** @return whether an address is one of this repository's release files on github.com */
		boolean ownsDownload(String url) {
			return url.toLowerCase(Locale.ROOT).startsWith("https://" + UpdateHttp.DOWNLOAD_HOST + downloadPrefix());
		}
	}

	static final String OWNER = "tim1337xd";
	static final Source LUMIO = new Source("lumio", OWNER, "lumio", "lumio");
	/** The repositories an add-on may name. */
	static final List<Source> KNOWN_ADDONS = List.of(new Source("lumio-skyblock", OWNER, "lumio-skyblock", "lumio-skyblock"));
	private static final int PER_PAGE = 20;

	private UpdateSources() {
	}

	/** @return a release's page on GitHub (opened through the game's link prompt), or null for a mod that is not Lumio or a known add-on */
	static String releasePage(String modId, String version) {
		for (Source source : java.util.stream.Stream.concat(java.util.stream.Stream.of(LUMIO), KNOWN_ADDONS.stream()).toList()) {
			if (source.modId().equals(modId)) {
				return "https://github.com/" + source.owner() + "/" + source.repository() + "/releases/tag/v" + version;
			}
		}
		return null;
	}

	/** @return Lumio and the add-ons that offered their releases and name a repository written here */
	static List<Source> all(List<AddonUpdates.Source> offered) {
		List<Source> sources = new ArrayList<>();
		sources.add(LUMIO);
		for (AddonUpdates.Source addon : offered) {
			Source known = known(addon);
			if (known != null && !sources.contains(known)) {
				sources.add(known);
			}
		}
		return sources;
	}

	/**
	 * @param path  an address's path in lower case
	 * @param start the part of a known repository's paths the address must start with
	 * @return whether the path belongs to Lumio's or a known add-on's repository; no "..", so it cannot climb out
	 */
	static boolean knownPath(String path, Function<Source, String> start) {
		if (path.contains("..") || path.contains("%2e") || path.contains("%2f") || path.contains("\\")) {
			return false;
		}
		if (path.startsWith(start.apply(LUMIO))) {
			return true;
		}
		for (Source source : KNOWN_ADDONS) {
			if (path.startsWith(start.apply(source))) {
				return true;
			}
		}
		return false;
	}

	/** @return the known source an add-on names, or null when it names another repository */
	static Source known(AddonUpdates.Source addon) {
		for (Source source : KNOWN_ADDONS) {
			if (source.modId().equals(addon.modId()) && source.owner().equalsIgnoreCase(addon.owner())
				&& source.repository().equalsIgnoreCase(addon.repository()) && source.jarName().equals(addon.jarName())) {
				return source;
			}
		}
		return null;
	}

	/**
	 * @return the test server a debug tool set for a manual test ({@link DebugHooks#setUpdateTestServer}, "http://127.0.0.1:<port>"), or null:
	 *         always null in a public release
	 */
	static String testServer(boolean releaseBuild) {
		return testServer(releaseBuild, DebugHooks.updateTestServer());
	}

	/**
	 * @return the key a release's signature must match: Lumio's release key; a debug tool's test key ({@link DebugHooks#setUpdateTestKey}) only in a
	 *         jar that is no public release and while its test server is set. A public release never takes another key.
	 */
	static String releaseKey(boolean releaseBuild) {
		return releaseKey(releaseBuild, testServer(releaseBuild), DebugHooks.updateTestKey());
	}

	/** @return the release key, as {@link #releaseKey(boolean)}, from the test server and test key set */
	static String releaseKey(boolean releaseBuild, String testServer, String testKey) {
		return releaseBuild || testServer == null || testKey == null ? ReleaseSignature.PUBLIC_KEY : testKey;
	}

	/** @return the test server, as {@link #testServer(boolean)}, from the address asked for */
	static String testServer(boolean releaseBuild, String value) {
		if (releaseBuild || value == null) {
			return null;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		// only this computer: a test server is never somewhere else
		return lower.startsWith("http://127.0.0.1:") || lower.startsWith("http://localhost:") ? value : null;
	}
}
