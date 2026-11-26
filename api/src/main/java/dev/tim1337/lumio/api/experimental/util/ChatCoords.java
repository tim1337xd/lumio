package dev.tim1337.lumio.api.experimental.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the block coordinates a player shared in a chat message, in the clear formats players use: "x: 12, y: 70, z: -30" (commas or not) or three
 * plain whole numbers "12 70 -30" / "12, 70, -30", with the words before them that may name the place ("Divan 512 80 300" gives "Divan"). The same
 * reader as Lumio's "+ Waypoint" buttons in chat. Plain text, so it is unit tested.
 */
public final class ChatCoords {
	/** In chat: "x: 12, y: 70, z: -30", whole numbers with colons, commas or not, nothing glued after the last. */
	private static final Pattern LABELLED = Pattern.compile(
		"(?i)(?<![\\w])x\\s*:\\s*(-?\\d{1,8})\\s*,?\\s*y\\s*:\\s*(-?\\d{1,3})\\s*,?\\s*z\\s*:\\s*(-?\\d{1,8})(?![\\w.,:%×]|\\s*,?\\s*-?\\d)");
	/**
	 * In chat: three whole numbers apart by spaces or a comma and a space ("12 70 -30", "12, 70, -30"), nothing glued before or after (no unit,
	 * suffix, decimal, time or thousands separator), and no fourth number after them.
	 */
	private static final Pattern PLAIN = Pattern.compile(
		"(?<![\\w.,:%×-])(-?\\d{1,8})(?:,\\s+|\\s+)(-?\\d{1,3})(?:,\\s+|\\s+)(-?\\d{1,8})(?![\\w.,:%×]|\\s*,?\\s*-?\\d)");
	/** The words that may stand before coordinates and say what they are: "corpse at x: 87" gives "Corpse". */
	private static final Pattern NAME_BEFORE = Pattern.compile("(?i)([\\p{L}][\\p{L}'\\- ]{0,30}?)\\s*(?:\\bat\\b|@|:)?\\s*$");
	private static final Pattern NOT_A_NAME = Pattern.compile("(?i)^(at|is|the|my|here|coords?|x|waypoint|location)$");
	/** The farthest a place in the world can be across, in blocks. */
	private static final int MOST_HORIZONTAL = 30_000_000;
	/** Heights of blocks in the worlds of the game; numbers outside are not coordinates. */
	public static final int LOWEST_Y = -64;
	public static final int HIGHEST_Y = 320;
	private static final int MAX_NAME = 24;
	private static final int MOST_TEXT = 2000;
	private static final int NAME_LOOK_BACK = 60;

	/** A position found in a message, with the words before it that may name it ("" when there are none). */
	public record Found(int x, int y, int z, String name) {
	}

	private ChatCoords() {
	}

	/**
	 * @param message what a player wrote (never a line of the game)
	 * @return the position in a chat message, only in the clear formats players share; never numbers with a unit or suffix (05m, 8s, 12k, 3M, 50%,
	 *         x3), thousands separators (125,000), times (1:23), a longer row of numbers, or a height or place outside the world; null when there is
	 *         none
	 */
	public static Found inChat(String message) {
		if (message == null || message.isEmpty() || message.length() > MOST_TEXT) {
			return null;
		}
		Matcher labelled = LABELLED.matcher(message);
		while (labelled.find()) {
			Found found = strict(labelled.group(1), labelled.group(2), labelled.group(3), message.substring(0, labelled.start()));
			if (found != null) {
				return found;
			}
		}
		Matcher plain = PLAIN.matcher(message);
		while (plain.find()) {
			if (numberBefore(message, plain.start())) {
				continue;
			}
			Found found = strict(plain.group(1), plain.group(2), plain.group(3), message.substring(0, plain.start()));
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** @return whether a number stands right before a place (then the three numbers are part of a longer row) */
	private static boolean numberBefore(String text, int start) {
		int at = start - 1;
		while (at >= 0 && (text.charAt(at) == ' ' || text.charAt(at) == ',')) {
			at--;
		}
		return at >= 0 && at < start - 1 && Character.isDigit(text.charAt(at));
	}

	private static Found strict(String x, String y, String z, String before) {
		try {
			int px = Integer.parseInt(x);
			int py = Integer.parseInt(y);
			int pz = Integer.parseInt(z);
			if (py < LOWEST_Y || py > HIGHEST_Y || Math.abs(px) > MOST_HORIZONTAL || Math.abs(pz) > MOST_HORIZONTAL) {
				return null;
			}
			return new Found(px, py, pz, nameBefore(before));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** @return the last words before coordinates, as a name: "corpse at" gives "Corpse"; "" when they are no name */
	public static String nameBefore(String before) {
		String tail = before.length() > NAME_LOOK_BACK ? before.substring(before.length() - NAME_LOOK_BACK) : before;
		// only what follows the sender's colon: "[Party] Kiwi: corpse at" names the corpse, not the player
		int colon = tail.lastIndexOf(": ");
		if (colon >= 0) {
			tail = tail.substring(colon + 2);
		}
		Matcher matcher = NAME_BEFORE.matcher(tail);
		if (!matcher.find()) {
			return "";
		}
		String name = matcher.group(1).strip();
		if (name.endsWith(" at")) {
			name = name.substring(0, name.length() - 3).strip();
		}
		if (name.isEmpty() || NOT_A_NAME.matcher(name).matches()) {
			return "";
		}
		name = name.length() > MAX_NAME ? name.substring(name.length() - MAX_NAME).strip() : name;
		return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
	}
}
