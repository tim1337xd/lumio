package dev.tim1337.lumio.util;

import dev.tim1337.lumio.Lumio;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mod's version as it is shown, read once from the mod metadata, where the build puts it from gradle.properties: 1.0.0-beta.1 is "1.0.0 Beta", 1.0.0-beta.2 is "1.0.0 Beta 2", the release 1.0.0 is "1.0.0", and an alpha build, which only the developer has,
 * 1.0.1-alpha+build.31 is "1.0.1 Alpha · Build 31". Other builds keep their number inside the jar, only for the log. The only place that formats it.
 */
public final class ModVersion {
	private static final String UNKNOWN = "development";
	private static final String BUILD_KEY = "lumio:build";

	/** How a version reads, worked out without the game. */
	public record Shown(String text, String build) {
		private static final Pattern VERSION = Pattern.compile("(\\d+\\.\\d+\\.\\d+)(?:-(alpha|beta)(?:\\.(\\d+))?)?(?:\\+build\\.(\\d+))?");
		private static final String FIRST_BETA = "1";

		/** @return how a version reads: a first beta is only "Beta" */
		public static Shown of(String raw) {
			Matcher matcher = VERSION.matcher(raw);
			if (!matcher.matches()) {
				return new Shown(raw, "");
			}
			String stage = matcher.group(2) == null ? "" : matcher.group(2).equals("alpha") ? " Alpha" : " Beta";
			String number = matcher.group(3) == null || FIRST_BETA.equals(matcher.group(3)) ? "" : " " + matcher.group(3);
			return new Shown(matcher.group(1) + stage + number, matcher.group(4) == null ? "" : "Build " + matcher.group(4));
		}
	}

	/** The version without the build: "1.0.0 Beta". */
	private static final String SHORT;
	/** "Build 31" for an alpha build, otherwise empty. */
	private static final String BUILD;
	/** The build number in the jar, for the log. */
	private static final String INTERNAL_BUILD;

	static {
		var metadata = FabricLoader.getInstance().getModContainer(Lumio.MOD_ID).map(container -> container.getMetadata());
		Shown shown = Shown.of(metadata.map(data -> data.getVersion().getFriendlyString()).orElse(UNKNOWN));
		SHORT = shown.text();
		BUILD = shown.build();
		CustomValue build = metadata.map(data -> data.getCustomValue(BUILD_KEY)).orElse(null);
		INTERNAL_BUILD = build != null && build.getType() == CustomValue.CvType.STRING ? build.getAsString() : "";
	}

	private ModVersion() {
	}

	/** @return the full version as shown: "1.0.0 Beta" */
	public static String display() {
		return BUILD.isEmpty() ? SHORT : SHORT + " · " + BUILD;
	}

	/** @return the version without the build: "1.0.0 Beta" */
	public static String shortDisplay() {
		return SHORT;
	}

	/** @return the build as shown: "Build 31" on an alpha build, or empty */
	public static String build() {
		return BUILD;
	}

	/** @return the version for the log, with the jar's build number */
	public static String forLog() {
		return INTERNAL_BUILD.isEmpty() ? display() : SHORT + " (build " + INTERNAL_BUILD + ")";
	}

	/** @return how another version reads, such as an add-on's */
	public static String shownAs(String raw) {
		return Shown.of(raw).text();
	}
}
