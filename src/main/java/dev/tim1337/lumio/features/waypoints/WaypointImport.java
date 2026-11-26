package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.waypoint.WaypointColors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Reads waypoint lists other mods share, and tells which one a text is: Lumio's own JSON; Skyblocker's ("[Skyblocker-Waypoint-Data-V1]" and its
 * older ordered lists, gzipped Base64 JSON); SkyHanni's and ColeWeight's (a JSON list of points with "options.name", which SkyHanni numbers);
 * Skytils' ("&lt;Skytils-Waypoint-Data&gt;(V1):" gzipped, or plain Base64 JSON); and plain coordinates, one per line. The formats follow the
 * sources of Skyblocker and SkyHanni. Everything is untrusted: sizes are limited before anything is decoded, and broken points are left out.
 */
public final class WaypointImport {
	/** The longest text taken, and the most a gzipped one may unpack to. */
	static final int MAX_TEXT = 1_000_000;
	static final int MAX_UNPACKED = 4_000_000;
	private static final int MAX_GROUPS = 100;
	static final String SKYBLOCKER = "[Skyblocker-Waypoint-Data-V1]";
	static final String SKYBLOCKER_ORDERED = "[Skyblocker::OrderedWaypoints::v1]";
	static final String SKYTILS = "<Skytils-Waypoint-Data>(V";
	private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/=\\s]+");
	private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d{1,6}");
	/** A point's color when the list has none: Skytils' and ColeWeight's green. */
	private static final int GREEN = 0x00FF00;

	/** What a text was read as. */
	public enum Format {
		LUMIO("Lumio"), SKYBLOCKER("Skyblocker"), SKYHANNI("SkyHanni (ColeWeight list)"), COLEWEIGHT("ColeWeight"), SKYTILS("Skytils"),
		COORDINATES("Coordinates");

		private final String label;

		Format(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** A group read from a text: its points, and the island it was for in the other mod, or "" (an area key of Lumio for Lumio's own). */
	public record Read(WaypointGroup group, String island) {
	}

	/** What a text held: its format and its groups; empty groups when it was none of them. */
	public record Result(Format format, List<Read> groups) {
		public int points() {
			int count = 0;
			for (Read read : groups) {
				count += read.group().points().size();
			}
			return count;
		}
	}

	private WaypointImport() {
	}

	/** @return what the text is, or null when it is no list of points */
	public static Result read(String text) {
		if (text == null || text.isBlank() || text.length() > MAX_TEXT) {
			return null;
		}
		String trimmed = text.strip();
		try {
			Result result;
			if (trimmed.startsWith(SKYBLOCKER)) {
				result = skyblocker(JsonParser.parseString(unpack(trimmed.substring(SKYBLOCKER.length()))));
			} else if (trimmed.startsWith(SKYBLOCKER_ORDERED)) {
				result = skyblockerOrdered(JsonParser.parseString(unpack(trimmed.substring(SKYBLOCKER_ORDERED.length()))));
			} else if (trimmed.startsWith(SKYTILS)) {
				int colon = trimmed.indexOf(':');
				result = colon < 0 ? null : skytils(JsonParser.parseString(unpack(trimmed.substring(colon + 1))));
			} else if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
				result = json(JsonParser.parseString(trimmed));
			} else if (BASE64.matcher(trimmed).matches() && trimmed.length() > 8) {
				String decoded = new String(Base64.getMimeDecoder().decode(trimmed), StandardCharsets.UTF_8).strip();
				result = decoded.startsWith("{") || decoded.startsWith("[") ? skytils(JsonParser.parseString(decoded)) : null;
			} else {
				result = coordinates(trimmed);
			}
			if (result == null || result.points() == 0) {
				return null;
			}
			for (Read read : result.groups()) {
				shareColor(read.group());
			}
			return result;
		} catch (RuntimeException | IOException e) {
			// not a list in any of the formats: a broken paste is not worth more than "nothing found"
			return null;
		}
	}

	/** JSON that is not Skyblocker's or Skytils' header: Lumio's own, a ColeWeight or SkyHanni list, or a Skytils list without the header. */
	private static Result json(JsonElement root) {
		if (root instanceof JsonObject object && object.has(WaypointJson.MARK)) {
			List<Read> reads = new ArrayList<>();
			for (WaypointGroup group : WaypointJson.read(object, saved -> "import")) {
				reads.add(new Read(group, ""));
			}
			return new Result(Format.LUMIO, reads);
		}
		if (root instanceof JsonObject object && object.get("categories") instanceof JsonArray) {
			return skytils(root);
		}
		if (root instanceof JsonArray array && !array.isEmpty() && array.get(0) instanceof JsonObject first) {
			if (first.has("options") || first.has("r")) {
				return coleweight(array);
			}
			if (first.has("pos") || first.has("waypoints")) {
				return skyblocker(root);
			}
			return skytils(root);
		}
		return null;
	}

	/** Skyblocker: a list of groups with "name", "island", "ordered" and points with "pos" [x, y, z], "name", "colorComponents" and "shouldRender". */
	private static Result skyblocker(JsonElement root) {
		List<Read> reads = new ArrayList<>();
		if (root instanceof JsonArray groups) {
			for (JsonElement element : groups) {
				if (reads.size() >= MAX_GROUPS || !(element instanceof JsonObject json)) {
					continue;
				}
				WaypointGroup group = new WaypointGroup("import", text(json.get("name"), "Skyblocker"), WaypointColors.DEFAULT);
				group.show(WaypointGroup.Show.ALL);
				if (bool(json, "ordered")) {
					group.show(WaypointGroup.Show.NEXT);
				}
				if (json.get("renderThroughWalls") instanceof JsonPrimitive through && through.isBoolean()) {
					group.set(WaypointGroup.Look.THROUGH_WALLS, through.getAsBoolean());
				}
				addSkyblockerPoints(group, json.get("waypoints"));
				reads.add(new Read(group, string(json, "island")));
			}
		}
		return new Result(Format.SKYBLOCKER, reads);
	}

	/** Skyblocker's older ordered lists: groups by name, each with points of only a position; they are routes. */
	private static Result skyblockerOrdered(JsonElement root) {
		List<Read> reads = new ArrayList<>();
		if (root instanceof JsonObject groups) {
			for (var entry : groups.entrySet()) {
				if (reads.size() >= MAX_GROUPS || !(entry.getValue() instanceof JsonObject json)) {
					continue;
				}
				WaypointGroup group = new WaypointGroup("import", text(json.get("name"), entry.getKey()), WaypointColors.GREEN);
				group.show(WaypointGroup.Show.ALL);
				group.show(WaypointGroup.Show.NEXT);
				addSkyblockerPoints(group, json.get("waypoints"));
				reads.add(new Read(group, ""));
			}
		}
		return new Result(Format.SKYBLOCKER, reads);
	}

	private static void addSkyblockerPoints(WaypointGroup group, JsonElement points) {
		if (!(points instanceof JsonArray array)) {
			return;
		}
		for (JsonElement element : array) {
			if (group.points().size() >= WaypointGroup.MAX_POINTS) {
				return;
			}
			if (element instanceof JsonObject json && json.get("pos") instanceof JsonArray pos && pos.size() == 3) {
				Waypoint point = point(pos.get(0), pos.get(1), pos.get(2), text(json.get("name"), ""));
				if (point != null) {
					point.rgb(components(json.get("colorComponents"), group.rgb()));
					if (json.get("shouldRender") instanceof JsonPrimitive shown && shown.isBoolean()) {
						point.hidden(!shown.getAsBoolean());
					}
					group.points().add(point);
				}
			}
		}
	}

	/** Skytils: {"categories": [{"name", "island", "waypoints": [...]}]}, or only a list of points with "name", "x", "y", "z", "color", "enabled". */
	private static Result skytils(JsonElement root) {
		List<Read> reads = new ArrayList<>();
		if (root instanceof JsonObject object && object.get("categories") instanceof JsonArray categories) {
			for (JsonElement element : categories) {
				if (reads.size() < MAX_GROUPS && element instanceof JsonObject json) {
					WaypointGroup group = new WaypointGroup("import", text(json.get("name"), "Skytils"), WaypointColors.GREEN);
					group.show(WaypointGroup.Show.ALL);
					addSkytilsPoints(group, json.get("waypoints"));
					reads.add(new Read(group, string(json, "island")));
				}
			}
		} else if (root instanceof JsonArray points) {
			WaypointGroup group = new WaypointGroup("import", "Skytils", WaypointColors.GREEN);
			group.show(WaypointGroup.Show.ALL);
			addSkytilsPoints(group, points);
			String island = points.size() > 0 && points.get(0) instanceof JsonObject first ? string(first, "island") : "";
			reads.add(new Read(group, island));
		}
		return new Result(Format.SKYTILS, reads);
	}

	private static void addSkytilsPoints(WaypointGroup group, JsonElement points) {
		if (!(points instanceof JsonArray array)) {
			return;
		}
		for (JsonElement element : array) {
			if (group.points().size() >= WaypointGroup.MAX_POINTS) {
				return;
			}
			if (element instanceof JsonObject json) {
				Waypoint point = point(json.get("x"), json.get("y"), json.get("z"), text(json.get("name"), ""));
				if (point != null) {
					// an ARGB number; Skytils leaves it out for its default green
					point.rgb(json.get("color") instanceof JsonPrimitive color && color.isNumber() ? color.getAsInt() & 0xFFFFFF : GREEN);
					if (json.get("enabled") instanceof JsonPrimitive enabled && enabled.isBoolean()) {
						point.hidden(!enabled.getAsBoolean());
					}
					group.points().add(point);
				}
			}
		}
	}

	/**
	 * ColeWeight, and SkyHanni, which reads and writes the same list: points with "x", "y", "z", "r", "g", "b" (0 to 1) and "options": {"name"}. A
	 * list whose names are all numbers is a route; SkyHanni writes only such lists.
	 */
	private static Result coleweight(JsonArray array) {
		WaypointGroup group = new WaypointGroup("import", "ColeWeight", WaypointColors.GREEN);
		group.show(WaypointGroup.Show.ALL);
		boolean numbered = true;
		for (JsonElement element : array) {
			if (group.points().size() >= WaypointGroup.MAX_POINTS) {
				break;
			}
			if (!(element instanceof JsonObject json)) {
				continue;
			}
			String name = json.get("options") instanceof JsonObject options ? text(options.get("name"), "") : "";
			Waypoint point = point(json.get("x"), json.get("y"), json.get("z"), name);
			if (point == null) {
				continue;
			}
			numbered &= WHOLE_NUMBER.matcher(name).matches();
			if (json.has("r") || json.has("g") || json.has("b")) {
				point.rgb(rgb(number(json, "r"), number(json, "g"), number(json, "b")));
			}
			group.points().add(point);
		}
		if (numbered) {
			group.show(WaypointGroup.Show.NEXT);
			group.rename("SkyHanni route");
		}
		return new Result(numbered ? Format.SKYHANNI : Format.COLEWEIGHT, List.of(new Read(group, "")));
	}

	/** Plain coordinates, one point per line, with the words before them as names. */
	private static Result coordinates(String text) {
		WaypointGroup group = new WaypointGroup("import", "Pasted", WaypointColors.DEFAULT);
		group.show(WaypointGroup.Show.ALL);
		for (CoordParser.Found found : CoordParser.all(text, WaypointGroup.MAX_POINTS)) {
			group.points().add(new Waypoint(found.x(), found.y(), found.z(), found.name()));
		}
		if (group.points().size() > 1) {
			group.show(WaypointGroup.Show.NEXT);
		}
		return new Result(Format.COORDINATES, List.of(new Read(group, "")));
	}

	/** Points that all have one color give it to their group, so the group's color can be changed for all of them at once. */
	private static void shareColor(WaypointGroup group) {
		if (group.points().isEmpty()) {
			return;
		}
		int first = group.points().getFirst().rgb();
		for (Waypoint point : group.points()) {
			if (point.rgb() != first) {
				return;
			}
		}
		if (first != 0) {
			group.rgb(first);
		}
		for (Waypoint point : group.points()) {
			point.rgb(0);
		}
	}

	// ------------------------------------------------------------------------------------------------------ values

	private static Waypoint point(JsonElement x, JsonElement y, JsonElement z, String name) {
		if (!(x instanceof JsonPrimitive px && px.isNumber() && y instanceof JsonPrimitive py && py.isNumber() && z instanceof JsonPrimitive pz
			&& pz.isNumber())) {
			return null;
		}
		double dx = px.getAsDouble();
		double dy = py.getAsDouble();
		double dz = pz.getAsDouble();
		if (!WaypointJson.inWorld(dx, dy, dz)) {
			return null;
		}
		return new Waypoint((int) Math.floor(dx), (int) Math.floor(dy), (int) Math.floor(dz), name);
	}

	/** @return a name: a string, a number, or the text of a chat component as Skyblocker writes it */
	static String text(JsonElement element, String fallback) {
		if (element instanceof JsonPrimitive primitive) {
			return primitive.getAsString();
		}
		if (element instanceof JsonObject component) {
			StringBuilder text = new StringBuilder(string(component, "text"));
			if (component.get("extra") instanceof JsonArray extra) {
				for (JsonElement part : extra) {
					text.append(text(part, ""));
				}
			}
			return text.toString();
		}
		if (element instanceof JsonArray parts) {
			StringBuilder text = new StringBuilder();
			for (JsonElement part : parts) {
				text.append(text(part, ""));
			}
			return text.toString();
		}
		return fallback;
	}

	private static String string(JsonObject json, String key) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString().toLowerCase(Locale.ROOT) : "";
	}

	private static boolean bool(JsonObject json, String key) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();
	}

	private static double number(JsonObject json, String key) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsDouble() : 0.0;
	}

	/** @return the color of [r, g, b] from 0 to 1, or the fallback */
	private static int components(JsonElement element, int fallback) {
		if (element instanceof JsonArray array && array.size() == 3 && array.get(0).isJsonPrimitive() && array.get(1).isJsonPrimitive()
			&& array.get(2).isJsonPrimitive()) {
			try {
				return rgb(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
			} catch (RuntimeException e) {
				return fallback;
			}
		}
		return fallback;
	}

	static int rgb(double r, double g, double b) {
		return channel(r) << 16 | channel(g) << 8 | channel(b);
	}

	private static int channel(double value) {
		return (int) Math.round(Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0)) * 255.0);
	}

	/** Base64 of gzipped text, unpacked to at most {@link #MAX_UNPACKED} bytes. */
	static String unpack(String base64) throws IOException {
		byte[] packed = Base64.getMimeDecoder().decode(base64.strip());
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) > 0) {
				if (out.size() + read > MAX_UNPACKED) {
					throw new IOException("The list unpacks to more than " + MAX_UNPACKED + " bytes");
				}
				out.write(buffer, 0, read);
			}
			return out.toString(StandardCharsets.UTF_8);
		}
	}
}
