package dev.tim1337.lumio.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version of Lumio or an add-on as released: MAJOR.MINOR.PATCH with an optional stage, 1.0.0-alpha, 1.0.0-beta.2 or 1.0.0 for a release; ordered
 * as SemVer orders them, an alpha before a beta before the release, beta.2 after beta.1. A build part (+build.31) marks a developer's build.
 */
record LumioVersion(int major, int minor, int patch, int stage, int stageNumber) implements Comparable<LumioVersion> {
	static final int ALPHA = 0;
	static final int BETA = 1;
	static final int RELEASE = 2;
	private static final int MOST_PART = 9_999;
	private static final Pattern FORM = Pattern.compile("^v?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(?:-(alpha|beta)(?:\\.(\\d{1,4}))?)?(\\+[0-9A-Za-z.\\-]{1,64})?$");

	/** @return the version in a text (a tag may start with v), or null when it is none of this form */
	static LumioVersion parse(String text) {
		if (text == null) {
			return null;
		}
		Matcher matcher = FORM.matcher(text.strip());
		if (!matcher.matches()) {
			return null;
		}
		int stage = matcher.group(4) == null ? RELEASE : matcher.group(4).equals("beta") ? BETA : ALPHA;
		int number = matcher.group(5) == null ? 0 : Math.min(MOST_PART, Integer.parseInt(matcher.group(5)));
		return new LumioVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)), stage, number);
	}

	/** @return the build part of a version text after its +, or "" without one (a tag's +mc26.3 is the Minecraft version, not a build) */
	static String buildPart(String text) {
		int plus = text == null ? -1 : text.indexOf('+');
		return plus < 0 ? "" : text.substring(plus + 1);
	}

	/** @return whether a version text is a developer's build: an alpha, or one with a build part */
	static boolean devBuild(String text) {
		LumioVersion version = parse(text);
		return version == null || version.stage == ALPHA || !buildPart(text).isEmpty();
	}

	boolean beta() {
		return stage == BETA;
	}

	@Override
	public int compareTo(LumioVersion other) {
		int order = Integer.compare(major, other.major);
		if (order == 0) {
			order = Integer.compare(minor, other.minor);
		}
		if (order == 0) {
			order = Integer.compare(patch, other.patch);
		}
		if (order == 0) {
			order = Integer.compare(stage, other.stage);
		}
		if (order == 0) {
			order = Integer.compare(stageNumber, other.stageNumber);
		}
		return order;
	}

	@Override
	public String toString() {
		String base = major + "." + minor + "." + patch;
		return switch (stage) {
			case ALPHA -> base + "-alpha";
			case BETA -> base + "-beta." + stageNumber;
			default -> base;
		};
	}

	/** @return the version as players see it: "1.0.0 Beta 2", "1.0.0" */
	String display() {
		String base = major + "." + minor + "." + patch;
		return switch (stage) {
			case ALPHA -> base + " Alpha";
			case BETA -> base + " Beta " + stageNumber;
			default -> base;
		};
	}
}
