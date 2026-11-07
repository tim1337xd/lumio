package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** What an add-on and the mod share about a scoreboard's lines: a line among them, where a line's location symbol is, and how a line is really built. Plain text, so it is unit tested. */
public final class ScoreLines {
	/** A thin line across the panel in place of a text line; recognized by identity. */
	public static final Component LINE = Component.literal("");

	private static final char COLOR_CODE = '§';
	private static final int LOCATION = '⏣';
	private static final int RIFT_LOCATION = 'ф';
	private static final int PIN_EMOJI = 0x1F4CD;
	private static final int VARIATION_FROM = 0xFE00;
	private static final int VARIATION_TO = 0xFE0F;
	private static final int MORE_VARIATION_FROM = 0xE0100;
	private static final int MORE_VARIATION_TO = 0xE01EF;
	private static final int PARTS_OF_A_TEAM_LINE = 3;
	private static final String[] PART_NAMES = {"prefix", "player", "suffix"};

	private ScoreLines() {
	}

	/** @return whether a character is a symbol Hypixel marks a place's name with */
	public static boolean locationSymbol(int codepoint) {
		return codepoint == LOCATION || codepoint == RIFT_LOCATION || codepoint == PIN_EMOJI;
	}

	/** @return whether a character shows nothing, or is one of the server's fillers: spaces, zero-width and private characters, variation selectors */
	public static boolean padding(int codepoint) {
		if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)) {
			return true;
		}
		int type = Character.getType(codepoint);
		return type == Character.FORMAT || type == Character.SURROGATE || type == Character.PRIVATE_USE || type == Character.UNASSIGNED || type == Character.CONTROL
			|| codepoint >= VARIATION_FROM && codepoint <= VARIATION_TO || codepoint >= MORE_VARIATION_FROM && codepoint <= MORE_VARIATION_TO;
	}

	/** @return where what shows starts from a place on, after color codes, padding and the server's filler emoji: there a name starts */
	public static int nameStart(String text, int from) {
		int at = from;
		while (at < text.length()) {
			int c = text.codePointAt(at);
			if (c == COLOR_CODE) {
				at += 2;
			} else if (padding(c) || Character.isSupplementaryCodePoint(c) && !Character.isLetterOrDigit(c)) {
				at += Character.charCount(c);
			} else {
				return at;
			}
		}
		return at;
	}

	/**
	 * @param text a whole line, across all its parts
	 * @return where its location symbol is, or -1: before the line's first letter or digit, whatever comes before it, and with a name after it. A Rift's "ф" is a letter itself: it counts only with no letter directly after it
	 */
	public static int locationStart(String text) {
		int at = 0;
		while (at < text.length()) {
			int c = text.codePointAt(at);
			if (c == COLOR_CODE) {
				at += 2;
				continue;
			}
			if (locationSymbol(c)) {
				int after = at + Character.charCount(c);
				if (c == RIFT_LOCATION && after < text.length() && Character.isLetter(shownAt(text, after))) {
					return -1;
				}
				return nameStart(text, after) < text.length() ? at : -1;
			}
			if (Character.isLetterOrDigit(c)) {
				return -1;
			}
			at += Character.charCount(c);
		}
		return -1;
	}

	/** @return the next character shown directly from a place on, after color codes only */
	private static int shownAt(String text, int from) {
		int at = from;
		while (at + 1 < text.length() && text.charAt(at) == COLOR_CODE) {
			at += 2;
		}
		return at < text.length() ? text.codePointAt(at) : ' ';
	}

	/** @return how a line is really built, for the debug log: each part, by its name in a team's line, with its style and every character as U+XXXX */
	public static String describe(Component line) {
		StringBuilder text = new StringBuilder();
		List<Component> siblings = line.getSiblings();
		boolean team = siblings.size() == PARTS_OF_A_TEAM_LINE;
		text.append("whole: ").append(codes(line.getString())).append(" | own style ").append(line.getStyle());
		for (int i = 0; i < siblings.size(); i++) {
			String name = team ? PART_NAMES[i] : "part " + (i + 1);
			siblings.get(i).visit((Style style, String part) -> {
				text.append(" | ").append(name).append(" [").append(style).append("]: ").append(codes(part));
				return Optional.empty();
			}, line.getStyle());
		}
		return text.toString();
	}

	/** @return every character of a text as U+XXXX */
	public static String codes(String text) {
		StringBuilder codes = new StringBuilder();
		text.codePoints().forEach(c -> codes.append(String.format(Locale.ROOT, "U+%04X ", c)));
		return codes.toString().strip();
	}
}
