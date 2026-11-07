package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.ApiVersion;

import java.util.regex.Pattern;

/**
 * The API versions an add-on works with, written as in fabric.mod.json: conditions separated by spaces (">=1.0.0 <2.0.0"), each a comparison
 * (>=, >, <=, <, =) with a version, or "*" for any. A range that cannot be read accepts nothing, so a broken add-on is left out, not run.
 */
final class ApiRange {
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private ApiRange() {
	}

	/** @return whether the version meets every condition of the range */
	static boolean accepts(String range, ApiVersion version) {
		if (range == null || range.isBlank()) {
			return false;
		}
		for (String condition : SPACES.split(range.strip())) {
			if (!meets(condition, version)) {
				return false;
			}
		}
		return true;
	}

	private static boolean meets(String condition, ApiVersion version) {
		if (condition.equals("*")) {
			return true;
		}
		int length = condition.startsWith(">=") || condition.startsWith("<=") ? 2
			: condition.startsWith(">") || condition.startsWith("<") || condition.startsWith("=") ? 1 : 0;
		String operator = length == 0 ? "=" : condition.substring(0, length);
		ApiVersion wanted;
		try {
			wanted = ApiVersion.parse(condition.substring(length));
		} catch (IllegalArgumentException e) {
			return false;
		}
		int compared = version.compareTo(wanted);
		return switch (operator) {
			case ">=" -> compared >= 0;
			case ">" -> compared > 0;
			case "<=" -> compared <= 0;
			case "<" -> compared < 0;
			default -> compared == 0;
		};
	}
}
