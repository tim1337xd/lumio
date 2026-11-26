package dev.tim1337.lumio.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A release as GitHub's API lists it: its tag, whether it is a pre-release, its page, its notes and its files. Read from untrusted data: every
 * text is bounded, and anything malformed is left out.
 */
record GitHubRelease(String tag, boolean prerelease, String page, String notes, List<Asset> assets) {
	/** A file of a release: its name, size, download address and the checksum GitHub lists ("sha256:…", or "" without one). */
	record Asset(String name, long size, String url, String digest) {
		/** @return the SHA-256 GitHub lists for it in lower case hex, or "" when it lists none */
		String sha256() {
			String lower = digest.toLowerCase(Locale.ROOT);
			return lower.startsWith(SHA256) && lower.length() == SHA256.length() + SHA256_HEX ? lower.substring(SHA256.length()) : "";
		}
	}

	private static final String SHA256 = "sha256:";
	/** A line's kind of change before its area, "feat(Sky): ", shown as "Sky: ". */
	private static final Pattern BULLET = Pattern.compile("^[-*]\\s+");
	private static final Pattern KIND =Pattern.compile("^(?:feat|fix|perf)\\(([^)]{1,40})\\):\\s*");
	private static final int SHA256_HEX = 64;
	private static final int MOST_RELEASES = 30;
	private static final int MOST_ASSETS = 20;
	private static final int MOST_TEXT = 256;
	private static final int MOST_NOTES = 4_000;
	/** The largest file a release may have to be downloaded: a jar of Lumio is a few megabytes. */
	static final long MOST_ASSET_BYTES = 64L * 1024 * 1024;

	/** @return the releases in an answer of GitHub's API, drafts left out, at most a few dozen */
	static List<GitHubRelease> parseList(String json) {
		List<GitHubRelease> releases = new ArrayList<>();
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (RuntimeException e) {
			return releases;
		}
		if (!(root instanceof JsonArray array)) {
			return releases;
		}
		for (JsonElement element : array) {
			if (releases.size() >= MOST_RELEASES) {
				break;
			}
			if (element instanceof JsonObject object && !bool(object, "draft")) {
				GitHubRelease release = parse(object);
				if (release != null) {
					releases.add(release);
				}
			}
		}
		return releases;
	}

	private static GitHubRelease parse(JsonObject object) {
		String tag = text(object, "tag_name", MOST_TEXT);
		if (tag.isEmpty()) {
			return null;
		}
		List<Asset> assets = new ArrayList<>();
		if (object.get("assets") instanceof JsonArray list) {
			for (JsonElement element : list) {
				if (assets.size() >= MOST_ASSETS) {
					break;
				}
				if (element instanceof JsonObject asset) {
					long size = number(asset, "size");
					String name = text(asset, "name", MOST_TEXT);
					String url = text(asset, "browser_download_url", MOST_TEXT * 4);
					if (!name.isEmpty() && !url.isEmpty() && size > 0 && size <= MOST_ASSET_BYTES) {
						assets.add(new Asset(name, size, url, text(asset, "digest", MOST_TEXT)));
					}
				}
			}
		}
		return new GitHubRelease(tag, bool(object, "prerelease"), text(object, "html_url", MOST_TEXT * 4), text(object, "body", MOST_NOTES), List.copyOf(assets));
	}

	private static String text(JsonObject object, String key, int most) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return "";
		}
		String text = value.getAsString();
		return text.length() > most ? text.substring(0, most) : text;
	}

	private static boolean bool(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
	}

	private static long number(JsonObject object, String key) {
		JsonElement value = object.get(key);
		try {
			return value != null && value.isJsonPrimitive() ? value.getAsLong() : -1;
		} catch (RuntimeException e) {
			return -1;
		}
	}

	/** @return the first lines of the notes that say something, without headings' marks, at most this many */
	List<String> firstLines(int most) {
		List<String> lines = new ArrayList<>();
		for (String line : notes.split("\\R")) {
			String stripped = line.strip();
			// headings (What's Changed, Features, Fixes) and the link to the full changelog are no news
			if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("**Full Changelog**")) {
				continue;
			}
			String plain = KIND.matcher(BULLET.matcher(stripped).replaceFirst("")).replaceFirst("$1: ");
			lines.add(plain);
			if (lines.size() >= most) {
				break;
			}
		}
		return lines;
	}
}
