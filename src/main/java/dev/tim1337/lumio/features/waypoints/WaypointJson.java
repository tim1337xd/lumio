package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.waypoint.WaypointColors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Groups as JSON: the saved files and Lumio's own export. Read from files and the clipboard, so everything is checked: numbers are bounded, names
 * cut, unknown values fall back to the defaults, and a broken group or point is left out instead of failing the rest.
 */
public final class WaypointJson {
	static final int FORMAT = 1;
	/** Marks Lumio's export, so the import can tell it from other JSON. */
	static final String MARK = "lumio_waypoints";
	/** Block positions beyond these are not from a world. */
	static final int MAX_HORIZONTAL = 30_000_000;
	static final int MIN_Y = -2048;
	static final int MAX_Y = 4096;
	private static final int MAX_GROUPS = 500;
	/** The key that marked a built-in group of an add-on in older files. */
	private static final String BUILT_IN_KEY = "built_in";
	private static final int MAX_AREAS = 32;
	private static final int MAX_KEY = 64;
	private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");
	private static final Pattern ICON = Pattern.compile("[a-z_]{1,24}");

	private WaypointJson() {
	}

	/** @return the groups as Lumio's JSON, with the mark that tells the import what it is */
	public static JsonObject write(List<WaypointGroup> groups) {
		JsonObject root = new JsonObject();
		root.addProperty(MARK, FORMAT);
		JsonArray array = new JsonArray();
		for (WaypointGroup group : groups) {
			array.add(write(group));
		}
		root.add("groups", array);
		return root;
	}

	static JsonObject write(WaypointGroup group) {
		JsonObject json = new JsonObject();
		json.addProperty("id", group.id());
		json.addProperty("name", group.name());
		json.addProperty("icon", group.icon());
		json.addProperty("color", hex(group.rgb()));
		json.addProperty("enabled", group.enabled());
		JsonArray areas = new JsonArray();
		group.areas().forEach(areas::add);
		json.add("areas", areas);
		json.addProperty("show", group.show().name().toLowerCase(Locale.ROOT));
		json.addProperty("count", group.nextCount());
		json.addProperty("label", group.label().name().toLowerCase(Locale.ROOT));
		json.addProperty("label_background", group.labelBackground());
		json.addProperty("restart_in_lobby", group.restartInNewLobby());
		json.addProperty("label_size", group.labelSize());
		json.addProperty("advance", group.advance().name().toLowerCase(Locale.ROOT));
		json.addProperty("reach", group.reach());
		JsonArray look = new JsonArray();
		for (WaypointGroup.Look part : WaypointGroup.Look.values()) {
			if (group.has(part)) {
				look.add(part.name().toLowerCase(Locale.ROOT));
			}
		}
		json.add("look", look);
		json.addProperty("map", group.map().name().toLowerCase(Locale.ROOT));
		json.addProperty("next", group.next());
		if (!group.source().isEmpty()) {
			json.addProperty("source", group.source());
		}
		JsonArray points = new JsonArray();
		for (Waypoint point : group.points()) {
			JsonObject p = new JsonObject();
			p.addProperty("x", point.x());
			p.addProperty("y", point.y());
			p.addProperty("z", point.z());
			p.addProperty("name", point.name());
			if (point.rgb() != 0) {
				p.addProperty("color", hex(point.rgb()));
			}
			if (point.hidden()) {
				p.addProperty("hidden", true);
			}
			points.add(p);
		}
		json.add("points", points);
		return json;
	}

	/**
	 * Reads groups.
	 *
	 * @param newId gives a group its id: it gets the saved one, or null when there is none or it is not usable, so a file keeps its ids and an
	 *              import can make new ones
	 */
	public static List<WaypointGroup> read(JsonObject root, Function<String, String> newId) {
		List<WaypointGroup> groups = new ArrayList<>();
		if (root == null || !(root.get("groups") instanceof JsonArray array)) {
			return groups;
		}
		for (JsonElement element : array) {
			if (groups.size() >= MAX_GROUPS) {
				break;
			}
			// a group an add-on once brought along (the fairy souls) kept no points of its own and is left out
			if (element instanceof JsonObject json && !json.has(BUILT_IN_KEY)) {
				try {
					groups.add(readGroup(json, newId));
				} catch (RuntimeException e) {
					// a broken group is left out; the others are kept
				}
			}
		}
		return groups;
	}

	static WaypointGroup readGroup(JsonObject json, Function<String, String> newId) {
		String savedId = string(json, "id", "");
		String id = newId.apply(savedId.isEmpty() || savedId.length() > MAX_KEY || !ID.matcher(savedId).matches() ? null : savedId);
		WaypointGroup group = new WaypointGroup(id, string(json, "name", "Waypoints"), color(json, "color", WaypointColors.DEFAULT));
		String icon = string(json, "icon", "pin");
		group.icon(ICON.matcher(icon).matches() ? icon : "pin");
		group.enabled(bool(json, "enabled", true));
		if (json.get("areas") instanceof JsonArray areas) {
			for (JsonElement area : areas) {
				if (group.areas().size() < MAX_AREAS && area instanceof JsonPrimitive primitive && primitive.isString()) {
					String key = primitive.getAsString();
					if (!key.isEmpty() && key.length() <= MAX_KEY && !group.areas().contains(key)) {
						group.areas().add(key);
					}
				}
			}
		}
		group.show(choice(json, "show", WaypointGroup.Show.values(), WaypointGroup.Show.ALL));
		// before counts, Nearest showed three
		group.nextCount(integer(json, "count", group.show() == WaypointGroup.Show.NEAREST ? WaypointGroup.MOST_SHOWN : WaypointGroup.DEFAULT_SHOWN, 1,
			WaypointGroup.MOST_SHOWN));
		group.labelBackground(bool(json, "label_background", false));
		group.restartInNewLobby(bool(json, "restart_in_lobby", false));
		group.labelSize(number(json, "label_size") ? json.get("label_size").getAsFloat() : WaypointGroup.DEFAULT_LABEL_SIZE);
		group.advance(choice(json, "advance", WaypointGroup.Advance.values(), WaypointGroup.Advance.REACHED));
		group.reach(integer(json, "reach", RouteProgress.DEFAULT_REACH, RouteProgress.MIN_REACH, RouteProgress.MAX_REACH));
		if (json.get("look") instanceof JsonArray look) {
			int bits = 0;
			boolean name = false;
			boolean distance = false;
			for (JsonElement part : look) {
				if (part instanceof JsonPrimitive primitive && primitive.isString()) {
					name |= primitive.getAsString().equals("name");
					distance |= primitive.getAsString().equals("distance");
					for (WaypointGroup.Look each : WaypointGroup.Look.values()) {
						if (each.name().equalsIgnoreCase(primitive.getAsString())) {
							bits |= each.bit();
						}
					}
				}
			}
			if (!json.has("label")) {
				// older files: the name and the distance were parts of the look, and the block had a glow where it now has its box
				bits |= WaypointGroup.Look.BLOCK.bit();
				group.label(name && distance ? WaypointGroup.Label.NAME_DISTANCE : name || distance ? WaypointGroup.Label.NAME : WaypointGroup.Label.OFF);
			}
			group.lookBits(bits);
		}
		if (json.has("label")) {
			group.label(choice(json, "label", WaypointGroup.Label.values(), WaypointGroup.Label.NAME));
		}
		group.map(choice(json, "map", WaypointGroup.MapMode.values(), WaypointGroup.MapMode.OFF));
		group.source(string(json, "source", ""));
		if (json.get("points") instanceof JsonArray points) {
			for (JsonElement element : points) {
				if (group.points().size() >= WaypointGroup.MAX_POINTS) {
					break;
				}
				Waypoint point = element instanceof JsonObject p ? point(p) : null;
				if (point != null) {
					group.points().add(point);
				}
			}
		}
		group.next(integer(json, "next", 0, 0, WaypointGroup.MAX_POINTS));
		return group;
	}

	private static Waypoint point(JsonObject json) {
		if (!number(json, "x") || !number(json, "y") || !number(json, "z")) {
			return null;
		}
		double x = json.get("x").getAsDouble();
		double y = json.get("y").getAsDouble();
		double z = json.get("z").getAsDouble();
		if (!inWorld(x, y, z)) {
			return null;
		}
		Waypoint point = new Waypoint((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), string(json, "name", ""));
		point.rgb(color(json, "color", 0));
		point.hidden(bool(json, "hidden", false));
		return point;
	}

	/** @return whether a position can be a block of a world */
	static boolean inWorld(double x, double y, double z) {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Math.abs(x) <= MAX_HORIZONTAL && Math.abs(z) <= MAX_HORIZONTAL && y >= MIN_Y
			&& y <= MAX_Y;
	}

	static String hex(int rgb) {
		String digits = Integer.toHexString(rgb & 0xFFFFFF).toUpperCase(Locale.ROOT);
		return "#" + "0".repeat(6 - digits.length()) + digits;
	}

	/** @return the color of "#RRGGBB" or "RRGGBB", or the fallback */
	static int parseColor(String text, int fallback) {
		String hex = text.startsWith("#") ? text.substring(1) : text;
		if (hex.length() != 6) {
			return fallback;
		}
		try {
			return Integer.parseInt(hex, 16);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int color(JsonObject json, String key, int fallback) {
		return parseColor(string(json, key, ""), fallback);
	}

	private static boolean number(JsonObject json, String key) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isNumber();
	}

	static String string(JsonObject json, String key, String fallback) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : fallback;
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
	}

	private static int integer(JsonObject json, String key, int fallback, int min, int max) {
		if (!number(json, key)) {
			return fallback;
		}
		double value = json.get(key).getAsDouble();
		return Double.isFinite(value) ? (int) Math.max(min, Math.min(max, value)) : fallback;
	}

	private static <E extends Enum<E>> E choice(JsonObject json, String key, E[] values, E fallback) {
		String text = string(json, key, "");
		for (E value : values) {
			if (value.name().equalsIgnoreCase(text)) {
				return value;
			}
		}
		return fallback;
	}
}
