package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.ScoreLines;
import dev.tim1337.lumio.api.experimental.util.ShortNumber;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a scoreboard line as its label and its value ("Purse: 1,204,553"): the custom look has the label on the left and the value on the right. */
final class ScoreboardLines {
	private static final String SEPARATOR = ": ";
	private static final char COLOR_CODE = '§';
	private static final int LOCATION = '\u23E3';
	private static final int RIFT_LOCATION = '\u0444';
	private static final int PIN_EMOJI = 0x1F4CD;
	private static final int NO_BREAK_SPACE = 0xA0;
	private static final String PIN = "pin";

	private ScoreboardLines() {
	}

	/** A line in two parts, with their colors, and what it just gained, such as "+5", or an empty text. */
	record Split(Component label, Component value, String gain) {
	}

	/** A line's icon, by its name or as the symbol itself, the line without it, and the symbol's color as 0xRRGGBB, or -1 for none of its own. */
	record Icon(String name, String symbol, Component rest, int rgb) {
	}

	/** What a value gained, as Hypixel writes it after the value: "12,480,553 (+5)". */
	private static final Pattern GAIN = Pattern.compile("\\s*\\(\\+([0-9][0-9,.]*[kKmMbB]?)\\)\\s*$");
	private static final char COLOR_MARK = '§';
	private static final Pattern NUMBER = Pattern.compile("^-?[0-9][0-9,]*(?:\\.[0-9]+)?$");

	/** A number is only shortened from this many digits on. */
	private static final double LEAST_TO_SHORTEN = 10000.0;

	/** @return where what a value gained starts in its text, or -1 */
	static int gainStart(String value) {
		Matcher matcher = GAIN.matcher(value);
		return matcher.find() ? matcher.start() : -1;
	}

	/** @return what a value gained, as "+5", or an empty text */
	static String gainOf(String value) {
		Matcher matcher = GAIN.matcher(value);
		return matcher.find() ? "+" + matcher.group(1) : "";
	}

	/** @return a number written shorter, readably: "12,480,553" is "12.5M"; a text that is no number, or a small one, as it is */
	static String shortened(String value) {
		String text = value.strip();
		if (!NUMBER.matcher(text).matches()) {
			return value;
		}
		double number = Double.parseDouble(text.replace(",", ""));
		if (Math.abs(number) < LEAST_TO_SHORTEN) {
			return value;
		}
		return ShortNumber.of(text);
	}

	/** @return the name of the mod's icon for a line's leading symbol, or null when the symbol itself is drawn */
	static String iconName(int symbol) {
		return switch (symbol) {
			case LOCATION, RIFT_LOCATION, PIN_EMOJI -> "pin";
			case '\u2600' -> "sun";
			case '\u2744' -> "snow";
			case '\u2694' -> "sword";
			case '\u26CF' -> "pickaxe";
			default -> Character.isSupplementaryCodePoint(symbol) ? "pin" : null;
		};
	}

	private static boolean isSymbol(int codepoint) {
		int type = Character.getType(codepoint);
		return codepoint == '\u0444' || type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL || Character.isSupplementaryCodePoint(codepoint);
	}

	/** @return whether a character is a symbol Hypixel marks the place's name with */
	static boolean locationSymbol(int codepoint) {
		return ScoreLines.locationSymbol(codepoint);
	}

	/** @return the next place from which something shows, after color codes and what shows nothing */
	private static int skipUnseen(String text, int from) {
		int at = from;
		while (at < text.length()) {
			int c = text.codePointAt(at);
			if (c == COLOR_CODE) {
				at += 2;
			} else if (ScoreLines.padding(c)) {
				at += Character.charCount(c);
			} else {
				break;
			}
		}
		return at;
	}

	/** @return the place of a line's leading symbol, or -1: the location's wherever it stands before the first letter, any other first of all and followed by a space */
	static int iconStart(String text) {
		int location = ScoreLines.locationStart(text);
		if (location >= 0) {
			return location;
		}
		int at = skipUnseen(text, 0);
		if (at >= text.length() || !isSymbol(text.codePointAt(at))) {
			return -1;
		}
		int after = at + Character.charCount(text.codePointAt(at));
		while (after + 1 < text.length() && text.charAt(after) == COLOR_CODE) {
			after += 2;
		}
		return after < text.length() && text.charAt(after) == ' ' ? at : -1;
	}

	/** @return a line's leading symbol apart from the rest of it, or null for a line without one */
	static Icon icon(Component line) {
		String whole = line.getString();
		int start = iconStart(whole);
		if (start < 0) {
			return null;
		}
		int symbol = whole.codePointAt(start);
		return apart(line, start, ScoreLines.nameStart(whole, start + Character.charCount(symbol)), iconName(symbol), new String(Character.toChars(symbol)));
	}

	/** @return a line from one place on, with the color at another place: what stood before is gone, the rest stays as sent */
	private static Icon apart(Component line, int colorAt, int cutTo, String name, String symbol) {
		MutableComponent rest = Component.empty();
		int[] at = {0};
		int[] rgb = {-1};
		line.visit((Style style, String part) -> {
			int from = at[0];
			int to = from + part.length();
			at[0] = to;
			if (colorAt >= from && colorAt < to) {
				rgb[0] = colorAt(style, part.substring(0, colorAt - from));
			}
			if (to > cutTo) {
				int cut = Math.max(0, cutTo - from);
				rest.append(Component.literal(lastColor(part.substring(0, cut)) + part.substring(cut)).withStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return new Icon(name, symbol, rest, rgb[0]);
	}

	/** How a location line was told. */
	enum LocationRule {
		/** It ends with the name of the area the player is in. */
		CURRENT_AREA,
		/** The area is not known yet: symbols before a known area's name. */
		KNOWN_AREA,
		/** Neither: the symbol Hypixel marks a place with, before a name not known here. */
		SYMBOL
	}

	/** Where a location line's name starts, and how it was told. */
	record LocationFound(int nameStart, LocationRule rule) {
	}

	/** @return a character that can be read in a line, or not: what shows nothing and color codes are not there at all */
	private static boolean unseenFiller(int codepoint) {
		return ScoreLines.padding(codepoint) && codepoint != ' ' && codepoint != NO_BREAK_SPACE;
	}

	/**
	 * Tells a location line by the area's name and never by its symbol, which Hypixel changes and which the fonts draw tiny.
	 *
	 * @param whole the whole line, across all its parts, with color codes
	 * @param areas the names of the area the player is in; empty ones count as not known
	 * @param known whether a text is a known area's name
	 * @return where its name starts and how it was told, or null for any other line
	 */
	static LocationFound findLocation(String whole, List<String> areas, Predicate<String> known) {
		// what can be read, with where each character of it stands in the line
		StringBuilder clean = new StringBuilder(whole.length());
		int[] places = new int[whole.length()];
		int at = 0;
		while (at < whole.length()) {
			int c = whole.codePointAt(at);
			if (c == COLOR_CODE) {
				at += 2;
			} else if (unseenFiller(c)) {
				at += Character.charCount(c);
			} else {
				places[clean.length()] = at;
				clean.appendCodePoint(c == NO_BREAK_SPACE ? ' ' : c);
				if (Character.charCount(c) == 2) {
					places[clean.length() - 1] = at + 1;
				}
				at += Character.charCount(c);
			}
		}
		String read = clean.toString();
		String ended = read.stripTrailing();
		for (String area : areas) {
			String name = area == null ? "" : area.strip();
			if (!name.isEmpty() && ended.length() >= name.length() && ended.regionMatches(true, ended.length() - name.length(), name, 0, name.length())) {
				int start = ended.length() - name.length();
				if (onlySymbols(read, start)) {
					return new LocationFound(places[start], LocationRule.CURRENT_AREA);
				}
			}
		}
		int start = firstNameLetter(read);
		if (start > 0 && onlySymbols(read, start) && !read.substring(0, start).isBlank() && known.test(ended.substring(Math.min(start, ended.length())))) {
			return new LocationFound(places[start], LocationRule.KNOWN_AREA);
		}
		int symbol = ScoreLines.locationStart(whole);
		return symbol < 0 ? null : new LocationFound(ScoreLines.nameStart(whole, symbol + Character.charCount(whole.codePointAt(symbol))), LocationRule.SYMBOL);
	}

	/** @return whether nothing but symbols stands before a place: no letter and no number, except the Rift's "ф", which is a symbol there */
	private static boolean onlySymbols(String read, int end) {
		for (int i = 0; i < end; i += Character.charCount(read.codePointAt(i))) {
			int c = read.codePointAt(i);
			if (Character.isLetterOrDigit(c) && !(c == RIFT_LOCATION && i + 1 < read.length() && !Character.isLetter(read.codePointAt(i + 1)))) {
				return false;
			}
		}
		return true;
	}

	/** @return where the first letter of a name is, past the Rift's "ф"; -1 if there is none */
	private static int firstNameLetter(String read) {
		for (int i = 0; i < read.length(); i += Character.charCount(read.codePointAt(i))) {
			int c = read.codePointAt(i);
			if (Character.isLetter(c) && !(c == RIFT_LOCATION && i + 1 < read.length() && !Character.isLetter(read.codePointAt(i + 1)))) {
				return i;
			}
		}
		return -1;
	}

	/** @return a location line's pin apart from its name, which stays as sent; null for any other line */
	static Icon locationIcon(Component line, List<String> areas, Predicate<String> known) {
		String whole = line.getString();
		LocationFound found = findLocation(whole, areas, known);
		if (found == null) {
			return null;
		}
		noteLocation(line, whole, found);
		// the pin has the color of what stood before the name, which it takes the place of, or else of the name
		int colorAt = found.nameStart();
		for (int i = 0; i < found.nameStart(); ) {
			int c = whole.codePointAt(i);
			if (c == COLOR_CODE) {
				i += 2;
			} else if (ScoreLines.padding(c)) {
				i += Character.charCount(c);
			} else {
				colorAt = i;
				break;
			}
		}
		return apart(line, colorAt, found.nameStart(), PIN, "");
	}

	/** @return a location line's pin apart from its name, by what the location source says; null for any other line */
	static Icon locationIcon(Component line) {
		LocationProvider location = Addons.location();
		if (location == null) {
			return locationIcon(line, List.of(), name -> false);
		}
		return locationIcon(line, List.of(location.zoneName(), location.placeName()), location::knownArea);
	}

	/** Says once per change what the line was built of and how it was told, for the debug log. */
	private static void noteLocation(Component line, String whole, LocationFound found) {
		if (!whole.equals(lastNoted)) {
			lastNoted = whole;
			Debug.log("Location line by {}: {}", found.rule(), ScoreLines.describe(line));
		}
	}

	private static String lastNoted = "";

	/** @return the color in force after a text written with color codes, from a part's style, as 0xRRGGBB, or -1 for none */
	private static int colorAt(Style style, String before) {
		int rgb = style.getColor() == null ? -1 : style.getColor().getValue();
		for (int i = before.indexOf(COLOR_CODE); i >= 0 && i + 1 < before.length(); i = before.indexOf(COLOR_CODE, i + 2)) {
			ChatFormatting format = ChatFormatting.getByCode(before.charAt(i + 1));
			TextColor color = format == null ? null : TextColor.fromLegacyFormat(format);
			if (color != null) {
				rgb = color.getValue();
			} else if (format == ChatFormatting.RESET) {
				rgb = -1;
			}
		}
		return rgb;
	}

	/** @return where a line's value starts after its label, or -1 for a line that has none: a colon inside a value ("7:20pm") does not count */
	static int valueStart(String text) {
		int colon = text.indexOf(SEPARATOR);
		if (colon <= 0) {
			return -1;
		}
		int start = colon + SEPARATOR.length();
		return text.substring(start).isBlank() || text.substring(0, colon).isBlank() ? -1 : start;
	}

	/** @return the color codes in force at the end of a text written with them, which a part cut off from it has to start with */
	static String lastColor(String text) {
		StringBuilder codes = new StringBuilder();
		for (int i = text.indexOf(COLOR_CODE); i >= 0 && i + 1 < text.length(); i = text.indexOf(COLOR_CODE, i + 2)) {
			char code = Character.toLowerCase(text.charAt(i + 1));
			if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f' || code == 'r') {
				codes.setLength(0);
			}
			codes.append(COLOR_CODE).append(code);
		}
		return codes.toString();
	}

	/** @return where a value ends before what it gained: the space and the color codes between them, "§6689,000 §e(+12)", are not part of it, or it would move when a gain comes */
	private static int valueEnd(String whole, int gainAt, int start) {
		int end = gainAt;
		while (end > start) {
			if (end - 2 >= start && whole.charAt(end - 2) == COLOR_MARK) {
				end -= 2;
			} else if (whole.charAt(end - 1) == ' ') {
				end--;
			} else {
				break;
			}
		}
		return end;
	}

	static Split split(Component line) {
		String whole = line.getString();
		int start = valueStart(whole);
		if (start < 0) {
			return null;
		}
		int colon = start - SEPARATOR.length();
		int gainAt = gainStart(whole);
		int valueEnd = gainAt > start ? valueEnd(whole, gainAt, start) : whole.length();
		MutableComponent label = Component.empty();
		MutableComponent value = Component.empty();
		int[] at = {0};
		line.visit((Style style, String part) -> {
			int from = at[0];
			int to = from + part.length();
			at[0] = to;
			if (from < colon) {
				label.append(Component.literal(part.substring(0, Math.min(part.length(), colon - from))).withStyle(style));
			}
			if (to > start) {
				// a code earlier in the same part colors the value; a later part starts afresh, as the game draws it
				int cut = Math.max(0, start - from);
				int until = Math.min(part.length(), valueEnd - from);
				if (until > cut) {
					value.append(Component.literal(lastColor(part.substring(0, cut)) + part.substring(cut, until)).withStyle(style));
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return new Split(label, value, gainAt > start ? gainOf(whole) : "");
	}
}
