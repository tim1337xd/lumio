package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.util.ChatCoords;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds block coordinates in text from chat or the clipboard: "x: 87, y: 118, z: -132", "87 118 -132", "87, 118, -132" and lists of them, one per
 * line. The text comes from other players and other mods, so only plain numbers in a world's range are taken, with a height a world can have.
 */
public final class CoordParser {
	/** "x: 87, y: 118, z: -132", "X 87 Y 118 Z -132", "x=87 y=118 z=-132". */
	private static final Pattern LABELLED = Pattern.compile(
		"(?i)(?<![a-z])x\\s*[:=]?\\s*(-?\\d{1,8}(?:\\.\\d+)?)\\s*[,;/]?\\s*y\\s*[:=]?\\s*(-?\\d{1,4}(?:\\.\\d+)?)\\s*[,;/]?\\s*z\\s*[:=]?\\s*(-?\\d{1,8}(?:\\.\\d+)?)");
	/** Three numbers in a row, apart by spaces or commas: "87 118 -132", "87, 118, -132". */
	private static final Pattern PLAIN = Pattern.compile(
		"(?<![\\w.:-])(-?\\d{1,8}(?:\\.\\d+)?)(?:\\s*,\\s*|\\s+)(-?\\d{1,4}(?:\\.\\d+)?)(?:\\s*,\\s*|\\s+)(-?\\d{1,8}(?:\\.\\d+)?)(?![\\w.%])");
	/** Heights of blocks in the worlds of the game; numbers outside are not coordinates. */
	static final int LOWEST_Y = ChatCoords.LOWEST_Y;
	static final int HIGHEST_Y = ChatCoords.HIGHEST_Y;

	/** A position found in text, with the words before it that may name it ("" when there are none). */
	public record Found(int x, int y, int z, String name) {
	}

	private CoordParser() {
	}

	/** @return the first position in a text, or null */
	public static Found first(String text) {
		if (text == null || text.isEmpty() || text.length() > 2000) {
			return null;
		}
		Found labelled = find(LABELLED.matcher(text), text);
		return labelled != null ? labelled : find(PLAIN.matcher(text), text);
	}

	/** @return the position a player shared in a chat message, as the API's chat reader finds it ({@link ChatCoords#inChat}), or null */
	public static Found inChat(String message) {
		ChatCoords.Found found = ChatCoords.inChat(message);
		return found == null ? null : new Found(found.x(), found.y(), found.z(), found.name());
	}

	/** @return every position in a text, one per line, in order; lines without one are skipped */
	public static List<Found> all(String text, int most) {
		List<Found> found = new ArrayList<>();
		if (text == null) {
			return found;
		}
		for (String line : text.split("\\R")) {
			if (found.size() >= most) {
				break;
			}
			Found one = first(line);
			if (one != null) {
				found.add(one);
			}
		}
		return found;
	}

	private static Found find(Matcher matcher, String text) {
		while (matcher.find()) {
			Found found = of(matcher.group(1), matcher.group(2), matcher.group(3), nameBefore(text.substring(0, matcher.start())));
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static Found of(String x, String y, String z, String name) {
		try {
			double px = Double.parseDouble(x);
			double py = Double.parseDouble(y);
			double pz = Double.parseDouble(z);
			if (py < LOWEST_Y || py > HIGHEST_Y || !WaypointJson.inWorld(px, py, pz)) {
				return null;
			}
			return new Found((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz), name);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** @return the last words before coordinates, as a name: "corpse at" gives "Corpse"; "" when they are no name */
	static String nameBefore(String before) {
		return ChatCoords.nameBefore(before);
	}
}
