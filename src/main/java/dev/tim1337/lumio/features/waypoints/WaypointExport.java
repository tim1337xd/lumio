package dev.tim1337.lumio.features.waypoints;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPOutputStream;

/**
 * Writes groups as a text to share: Lumio's own JSON (names, colors, order, areas and the Show mode), Skyblocker's list (gzipped Base64 with its
 * header, a route as an ordered group) or SkyHanni's (a ColeWeight list numbered in order). Nothing is sent: the text goes to the clipboard or a
 * file in the config folder.
 */
public final class WaypointExport {
	/** Skyblocker draws its points with this opacity. */
	private static final float SKYBLOCKER_ALPHA = 0.5f;
	private static final String UNKNOWN_ISLAND = "unknown";

	/** The formats a group can be written in. */
	public enum Format {
		LUMIO("Lumio"), SKYBLOCKER("Skyblocker"), SKYHANNI("SkyHanni");

		private final String label;

		Format(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private WaypointExport() {
	}

	/**
	 * @param areas    whether the areas go with the groups
	 * @param islandOf the other mod's island of an area, for Skyblocker ("crystal_hollows"), or null when it has none
	 */
	public static String write(List<WaypointGroup> groups, Format format, boolean areas, UnaryOperator<String> islandOf) {
		return switch (format) {
			case LUMIO -> lumio(groups, areas);
			case SKYBLOCKER -> skyblocker(groups, areas, islandOf);
			case SKYHANNI -> skyhanni(groups);
		};
	}

	private static String lumio(List<WaypointGroup> groups, boolean areas) {
		JsonObject root = WaypointJson.write(groups);
		if (!areas && root.get("groups") instanceof JsonArray array) {
			for (JsonElement group : array) {
				group.getAsJsonObject().remove("areas");
			}
		}
		return new Gson().toJson(root);
	}

	private static String skyblocker(List<WaypointGroup> groups, boolean areas, UnaryOperator<String> islandOf) {
		JsonArray array = new JsonArray();
		for (WaypointGroup group : groups) {
			JsonObject json = new JsonObject();
			json.addProperty("name", group.name());
			String island = areas && !group.areas().isEmpty() ? islandOf.apply(group.areas().getFirst()) : null;
			json.addProperty("island", island == null ? UNKNOWN_ISLAND : island);
			JsonArray points = new JsonArray();
			for (Waypoint point : group.points()) {
				JsonObject p = new JsonObject();
				JsonArray pos = new JsonArray();
				pos.add(point.x());
				pos.add(point.y());
				pos.add(point.z());
				p.add("pos", pos);
				p.addProperty("name", point.name());
				int rgb = group.rgbOf(point);
				JsonArray components = new JsonArray();
				components.add((rgb >> 16 & 0xFF) / 255f);
				components.add((rgb >> 8 & 0xFF) / 255f);
				components.add((rgb & 0xFF) / 255f);
				p.add("colorComponents", components);
				p.addProperty("alpha", SKYBLOCKER_ALPHA);
				p.addProperty("shouldRender", !point.hidden());
				points.add(p);
			}
			json.add("waypoints", points);
			json.addProperty("ordered", group.show() == WaypointGroup.Show.NEXT);
			json.addProperty("renderThroughWalls", group.has(WaypointGroup.Look.THROUGH_WALLS));
			array.add(json);
		}
		return WaypointImport.SKYBLOCKER + pack(new Gson().toJson(array));
	}

	/** SkyHanni's list: the points of all groups in order, each named by its number, as SkyHanni reads only numbered points. */
	private static String skyhanni(List<WaypointGroup> groups) {
		JsonArray array = new JsonArray();
		int number = 0;
		for (WaypointGroup group : groups) {
			for (Waypoint point : group.points()) {
				number++;
				JsonObject p = new JsonObject();
				p.addProperty("x", point.x());
				p.addProperty("y", point.y());
				p.addProperty("z", point.z());
				int rgb = group.rgbOf(point);
				p.addProperty("r", (rgb >> 16 & 0xFF) / 255.0);
				p.addProperty("g", (rgb >> 8 & 0xFF) / 255.0);
				p.addProperty("b", (rgb & 0xFF) / 255.0);
				JsonObject options = new JsonObject();
				options.addProperty("name", String.valueOf(number));
				p.add("options", options);
				array.add(p);
			}
		}
		return new Gson().toJson(array);
	}

	/** @return text gzipped and in Base64 */
	static String pack(String text) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
			gzip.write(text.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}
}
