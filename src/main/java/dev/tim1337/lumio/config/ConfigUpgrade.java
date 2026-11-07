package dev.tim1337.lumio.config;

import dev.tim1337.lumio.features.visual.world.SwingSpeed;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.Consumer;

/**
 * Brings saved settings (the settings file or a config) from an older version up to date before they are applied. Works on the saved JSON only
 * and never touches the features, so it runs without the game (and is unit tested). When the format changes, raise {@link #VERSION} and add a step for
 * it to {@link #STEPS}.
 */
public final class ConfigUpgrade {
	/**
	 * The version of the settings format, saved with the settings file and every config: a step runs only on settings older than the version
	 * that brought it, so it never changes what a player chose after it.
	 */
	public static final String VERSION_KEY = "_version";
	public static final int VERSION = 14;
	private static final String RADIANCE = "held_item_outline";
	private static final String AMBIENCE = "ambience";
	private static final String RADIANCE_MODE = "mode";

	/** What changed in each version of the format, oldest first. */
	private static final List<Step> STEPS = List.of(new Step(2, ConfigUpgrade::themeWithoutGlow), new Step(3, ConfigUpgrade::swingFactorToSpeed), new Step(4, ConfigUpgrade::blockOverlayRows), new Step(5, ConfigUpgrade::oneTrailsFeature), new Step(6, ConfigUpgrade::radianceRows), new Step(7, ConfigUpgrade::ambienceRows), new Step(8, ConfigUpgrade::skyStyles), new Step(9, ConfigUpgrade::noFlameSettings), new Step(10, ConfigUpgrade::skyStyleColors), new Step(11, ConfigUpgrade::dropLeftovers), new Step(12, ConfigUpgrade::trailStyles), new Step(13, ConfigUpgrade::radianceWithoutFlame), new Step(14, ConfigUpgrade::lightningWhen));

	/**
	 * The global HUD theme came in, and a new config has its glow on. Panels had no glow by default before it, so the theme of an older config starts without one: its panels then match the theme
	 * and show as Theme default, looking just as they did.
	 */
	private static void themeWithoutGlow(JsonObject root) {
		if (root.get("menu") instanceof JsonObject menu && !menu.has("hud_glow")) {
			menu.addProperty("hud_glow", false);
		}
	}

	/**
	 * The swing's speed was a factor (a swing took its length divided by it), and is an exponent now (its length times e to the minus it): the same pace, as far as the new range goes. Its key is new,
	 * so that the two are never taken for each other.
	 */
	private static void swingFactorToSpeed(JsonObject root) {
		if (!(root.get(HELD_ITEM) instanceof JsonObject item)) {
			return;
		}
		JsonElement old = item.remove(OLD_SWING_FACTOR);
		if (!item.has(SWING_SPEED) && old instanceof JsonPrimitive factor && factor.isNumber()) {
			item.addProperty(SWING_SPEED, SwingSpeed.fromOldFactor(factor.getAsFloat()));
		}
	}

	/**
	 * The block overlay's Fill style became the Fill row of its three styles, and where its colors come from a row of its own. An outline stays without a tint, a fill keeps its tint on the outline that remains, and the moving look, which took the
	 * menu's colors, becomes the rainbow; the others keep the color they had.
	 */
	static void blockOverlayRows(JsonObject root) {
		if (!(root.get(BLOCK_OVERLAY) instanceof JsonObject overlay)) {
			return;
		}
		String style = overlay.get(STYLE) instanceof JsonPrimitive saved ? saved.getAsString() : OUTLINE_STYLE;
		boolean accent = overlay.get(COLOR) instanceof JsonObject color && color.get(COLOR_MENU) instanceof JsonPrimitive menu && menu.isBoolean() && menu.getAsBoolean();
		overlay.addProperty(COLOR_SOURCE, SHADER_STYLE.equals(style) ? RAINBOW : accent ? ACCENT : CUSTOM);
		if (OUTLINE_STYLE.equals(style)) {
			overlay.addProperty(FILL, 0f);
		} else if (FILL_STYLE.equals(style)) {
			overlay.addProperty(STYLE, OUTLINE_STYLE);
		}
	}

	/** The trails' styles became Silk, Stardust, Comet, Lightning, Helix and Echo, in the accent or a custom color, with no look per source; swings are Radiance's Echo now. */
	static void trailStyles(JsonObject root) {
		if (!(root.get(TRAILS) instanceof JsonObject trails)) {
			return;
		}
		if (trails.get(STYLE_KEY) instanceof JsonPrimitive saved) {
			trails.addProperty(STYLE_KEY, switch (saved.getAsString()) {
				case "SPARKS" -> "STARDUST";
				case "COMET" -> "COMET";
				case "GHOST" -> "ECHO";
				default -> "SILK";
			});
		}
		if (trails.get(COLOR_SOURCE) instanceof JsonPrimitive saved && "RARITY".equals(saved.getAsString())) {
			trails.addProperty(COLOR_SOURCE, ACCENT);
		}
		for (String own : new String[]{"arrow_style", "pearl_style"}) {
			if (trails.get(own) instanceof JsonPrimitive saved) {
				trails.addProperty(own, switch (saved.getAsString()) {
					case "RIBBON" -> "SILK";
					case "SPARKS" -> "STARDUST";
					case "COMET" -> "COMET";
					case "GHOST" -> "ECHO";
					default -> "SAME";
				});
			}
		}
		for (String own : new String[]{"arrow_colors", "pearl_colors"}) {
			if (trails.get(own) instanceof JsonPrimitive saved && "RARITY".equals(saved.getAsString())) {
				trails.addProperty(own, ACCENT);
			}
		}
		drop(root, TRAILS, "on_swings", "swing_style", "swing_colors", "swing_color", "swing_count", "swing_width");
	}

	/**
	 * What was left of the old versions of the six features goes: read once by the steps before, and dropped from the file here. An ambience style that no longer exists becomes Fireflies; the painted
	 * skies go over to Auto.
	 */
	static void dropLeftovers(JsonObject root) {
		drop(root, "held_item_transform", "z", "cooldown_dip", "hand_size", "hand_x", "hand_y", "hand_z", "affects");
		drop(root, TRAILS, "on_self", "stars_around", "star_color", "star_count", "swing_copies", "swing_count", "swing_width");
		drop(root, RADIANCE, "body_light", "glow_smoothness", "glow_look", "show_in");
		drop(root, AMBIENCE, "snow_color", "snow_look", "snow_everywhere", "embers", "ember_color", "ember_amount", "distance", "fog", "indoors", "wind", "wind_streaks", "wind_where", "wind_streak_amount",
			"light_shafts", "ground_mist", "falling_leaves", "water_glints", "island_mood", "in_known_world");
		if (root.get("sky") instanceof JsonObject sky && sky.get("mode") instanceof JsonPrimitive mode && "ALWAYS".equals(mode.getAsString())) {
			sky.addProperty("when", "ALWAYS");
		}
		drop(root, "sky", "painted", "mode", "cycle_source", "also_show", "nether_sky", "color", "animation_speed", "lightning", "lightning_all", "lightning_brightness", "lightning_color", "lightning_bolts",
			"lightning_branches", "sync_music", "sync_strength");
		if (root.get(AMBIENCE) instanceof JsonObject ambience && !(ambience.get(RADIANCE_MODE) instanceof JsonPrimitive saved && "RAIN".equals(saved.getAsString()))) {
			ambience.addProperty(RADIANCE_MODE, "FIREFLIES");
		}
	}

	private static void drop(JsonObject root, String feature, String... keys) {
		if (root.get(feature) instanceof JsonObject settings) {
			for (String key : keys) {
				settings.remove(key);
			}
		}
	}

	/**
	 * Every sky style got its own colors. The color there was goes to the style it was used for, and the others start at Natural; the aurora's named palette colors become custom ones, and green,
	 * the first of them, is its natural color. The sky's old default color, which nobody chose, stays natural.
	 */
	static void skyStyleColors(JsonObject root) {
		if (!(root.get("sky") instanceof JsonObject sky)) {
			return;
		}
		String style = sky.get("style") instanceof JsonPrimitive saved ? saved.getAsString() : "AUTO";
		String palette = sky.remove("palette") instanceof JsonPrimitive saved ? saved.getAsString() : "GREEN";
		float stormColor = sky.remove("storm_color") instanceof JsonPrimitive saved && saved.isNumber() ? saved.getAsFloat() : 0f;
		boolean accent = sky.get(COLOR) instanceof JsonObject color && color.get(COLOR_MENU) instanceof JsonPrimitive menu && menu.isBoolean() && menu.getAsBoolean();
		String hex = sky.get(COLOR) instanceof JsonObject color && color.get(COLOR) instanceof JsonPrimitive value ? value.getAsString() : "#9A4DFF";
		boolean chosen = !hex.equalsIgnoreCase("#9A4DFF");
		switch (style) {
			case "AURORA" -> {
				String named = switch (palette) {
					case "TEAL" -> "#2FD5C0";
					case "PINK" -> "#FF6FB5";
					case "ICE" -> "#6FB4FF";
					case "CUSTOM" -> hex;
					default -> null;
				};
				if (accent) {
					sky.addProperty("aurora_colors", ACCENT);
				} else if (named != null) {
					sky.addProperty("aurora_colors", CUSTOM);
					sky.addProperty("aurora_color_1", named);
				}
			}
			case "STARS" -> styleColor(sky, "star", accent, chosen, hex);
			case "CLOUDS" -> {
				if (stormColor > 0f) {
					styleColor(sky, "cloud", accent, chosen, hex);
				}
			}
			default -> { }
		}
	}

	private static void styleColor(JsonObject sky, String style, boolean accent, boolean chosen, String hex) {
		if (accent) {
			sky.addProperty(style + "_colors", ACCENT);
		} else if (chosen) {
			sky.addProperty(style + "_colors", CUSTOM);
			sky.addProperty(style + "_color", hex);
		}
	}

	/** The sky's lightning switch became a choice: on is With rain, as it was, and off is Off. */
	static void lightningWhen(JsonObject root) {
		if (root.get("sky") instanceof JsonObject sky && sky.remove("storm_lightning") instanceof JsonPrimitive on && on.isBoolean()) {
			sky.addProperty("lightning_when", on.getAsBoolean() ? "WITH_RAIN" : "OFF");
		}
	}

	/** Radiance has no Flame style any more: who chose it gets Echo. */
	static void radianceWithoutFlame(JsonObject root) {
		if (root.get(RADIANCE) instanceof JsonObject radiance && radiance.get(RADIANCE_MODE) instanceof JsonPrimitive mode && "FLAME".equals(mode.getAsString())) {
			radiance.addProperty(RADIANCE_MODE, "ECHO");
		}
	}

	/** Radiance's flames have no settings of their own any more: the shared color, strength and width decide. */
	static void noFlameSettings(JsonObject root) {
		if (root.get(RADIANCE) instanceof JsonObject radiance) {
			for (String key : List.of("flame_speed", "flame_turbulence", "embers", "wrap_flames", "flame_layers", "advanced_flames", "flame_origin", "flame_height")) {
				radiance.remove(key);
			}
		}
	}

	/**
	 * The sky's themes became the styles Auto, Clouds, Aurora and Stars. The storm is the clouds now, heavy ones; the northern lights are the aurora, the falling stars the stars, and the skies that are
	 * pictures stay, as the painted sky under Advanced. Volumetric or not is no choice any more: all clouds are.
	 */
	static void skyStyles(JsonObject root) {
		if (!(root.get("sky") instanceof JsonObject sky)) {
			return;
		}
		String theme = sky.remove("theme") instanceof JsonPrimitive saved ? saved.getAsString() : "NEBULA";
		sky.remove("volumetric_clouds");
		switch (theme) {
			case "STORM" -> {
				sky.addProperty("style", "CLOUDS");
				sky.addProperty("cloud_cover", 0.9f);
			}
			case "NORTHERN_LIGHTS" -> sky.addProperty("style", "AURORA");
			case "FALLING_STARS" -> sky.addProperty("style", "STARS");
			case "DEEP_SPACE", "FIRE" -> sky.addProperty("painted", theme);
			default -> sky.addProperty("painted", "NEBULA");
		}
	}

	/**
	 * The ambience's styles became Auto, Fireflies, Snow and Rain, and the petals went. Those who had snow or fireflies keep them wherever and whenever they had them, a blizzard keeps its look, embers
	 * became a switch of their own, petals went over to leaves, and None, which was only there for the wind, stays without particles.
	 */
	static void ambienceRows(JsonObject root) {
		if (!(root.get(AMBIENCE) instanceof JsonObject ambience)) {
			return;
		}
		String mode = ambience.get(RADIANCE_MODE) instanceof JsonPrimitive saved ? saved.getAsString() : "SNOWFALL";
		ambience.remove("petal_color");
		switch (mode) {
			case "SNOWFALL" -> ambience.addProperty("snow_everywhere", true);
			case "BLIZZARD" -> {
				ambience.addProperty("snow_everywhere", true);
				ambience.addProperty("snow_look", "BLIZZARD");
			}
			case "FIREFLIES" -> ambience.addProperty("fireflies_anywhere", true);
			case "FIRE" -> ambience.addProperty("embers", true);
			case "SAKURA" -> ambience.addProperty("falling_leaves", true);
			case "NONE" -> {
				ambience.addProperty("intensity", 0f);
				for (String extra : List.of("light_shafts", "ground_mist", "falling_leaves", "water_glints", "island_mood")) {
					ambience.addProperty(extra, false);
				}
				ambience.addProperty("fog", 0f);
			}
			default -> { }
		}
		ambience.addProperty(RADIANCE_MODE, switch (mode) {
			case "SNOWFALL", "BLIZZARD" -> "SNOW";
			case "FIREFLIES" -> "FIREFLIES";
			default -> "AUTO";
		});
	}

	/**
	 * Radiance's styles became Glow, Echo and Flame. The outline goes to Glow, the rim light to Glow with its own look under Advanced, and fire is now Flame. Where its color comes from became a row,
	 * the hand a row of its own, and the item kinds a choice of all, weapons or the ones chosen.
	 */
	static void radianceRows(JsonObject root) {
		if (!(root.get(RADIANCE) instanceof JsonObject radiance)) {
			return;
		}
		String mode = radiance.get(RADIANCE_MODE) instanceof JsonPrimitive saved ? saved.getAsString() : "OUTLINE";
		if ("NEON".equals(mode)) {
			radiance.addProperty("glow_look", "RIM");
		}
		radiance.addProperty(RADIANCE_MODE, "FIRE".equals(mode) ? "FLAME" : "GLOW");
		boolean accent = false;
		if (radiance.get(COLOR) instanceof JsonObject color) {
			accent = color.get(COLOR_MENU) instanceof JsonPrimitive menu && menu.isBoolean() && menu.getAsBoolean();
			color.remove(COLOR_MENU);
		}
		radiance.addProperty(COLOR_SOURCE, accent ? ACCENT : CUSTOM);
		radiance.remove("preview");
		if (radiance.get("show_in") instanceof JsonObject showIn && showIn.remove("arm_first_person") instanceof JsonPrimitive arm) {
			radiance.add("arm_first_person", arm);
		}
		radiance.addProperty("shows_on", radianceShowsOn(radiance.get("affects") instanceof JsonObject kinds ? kinds : new JsonObject()));
	}

	private static String radianceShowsOn(JsonObject kinds) {
		int on = 0;
		for (String kind : List.of("for_tools", "for_weapons", "for_blocks", "for_food", "for_other")) {
			if (!(kinds.get(kind) instanceof JsonPrimitive saved) || saved.getAsBoolean()) {
				on++;
			}
		}
		boolean weapons = !(kinds.get("for_weapons") instanceof JsonPrimitive saved) || saved.getAsBoolean();
		return on == 5 ? "ALL" : on == 1 && weapons ? "WEAPONS" : "CHOSEN";
	}

	/**
	 * The trails behind arrows and the trail behind the swung item became one feature, with four looks. The old looks go to the nearest new one, the color of the chosen look becomes the page's, and what only the swing or the pearl had moves to their
	 * own settings under Advanced, so that each looks as near as can be to what it was.
	 */
	static void oneTrailsFeature(JsonObject root) {
		JsonObject trails = root.get(TRAILS) instanceof JsonObject saved ? saved : null;
		JsonObject swing = root.get(SWING_TRAIL) instanceof JsonObject saved ? saved : null;
		root.remove(SWING_TRAIL);
		if (trails == null && swing == null) {
			return;
		}
		if (trails == null) {
			trails = new JsonObject();
			trails.addProperty(ENABLED, false);
			root.add(TRAILS, trails);
		}
		if (trails.remove("shots") instanceof JsonObject shots) {
			shots.entrySet().forEach(entry -> root.get(TRAILS).getAsJsonObject().add(entry.getKey(), entry.getValue()));
		}
		String style = trails.get(STYLE_KEY) instanceof JsonPrimitive saved ? saved.getAsString() : "FIRE";
		JsonElement chosen = trails.get(switch (style) {
			case "LIGHTNING" -> "lightning_color";
			case "ELECTRIC" -> "electric_color";
			case "STARS" -> "star_color";
			default -> "fire_color";
		});
		if (chosen instanceof JsonObject color) {
			boolean accent = color.get(COLOR_MENU) instanceof JsonPrimitive menu && menu.isBoolean() && menu.getAsBoolean();
			trails.addProperty(COLOR_SOURCE, accent ? ACCENT : CUSTOM);
			JsonObject own = color.deepCopy();
			own.remove(COLOR_MENU);
			trails.add(COLOR, own);
		}
		if (trails.has(STYLE_KEY)) {
			trails.addProperty(STYLE_KEY, switch (style) {
				case "FIRE" -> "COMET";
				case "STARS" -> "SPARKS";
				default -> "RIBBON";
			});
		}
		if ("STARS".equals(style) && trails.get("on_self") instanceof JsonPrimitive self && self.isBoolean() && self.getAsBoolean()) {
			trails.addProperty("on_self", false);
			trails.addProperty("stars_around", true);
		}
		if (trails.get("pearl_color") instanceof JsonObject pearl) {
			boolean accent = pearl.get(COLOR_MENU) instanceof JsonPrimitive menu && menu.isBoolean() && menu.getAsBoolean();
			trails.addProperty("pearl_colors", accent ? ACCENT : CUSTOM);
			pearl.remove(COLOR_MENU);
		}
		trails.remove("fire_color");
		trails.remove("lightning_color");
		trails.remove("electric_color");
		if (swing == null) {
			trails.addProperty("on_swings", false);
			return;
		}
		boolean swings = swing.get(ENABLED) instanceof JsonPrimitive on && on.isBoolean() && on.getAsBoolean();
		boolean shots = trails.get(ENABLED) instanceof JsonPrimitive on && on.isBoolean() && on.getAsBoolean();
		trails.addProperty("on_swings", swings);
		if (swings && !shots) {
			// the swing's trail was on alone: the feature comes on for it, and only for it
			trails.addProperty(ENABLED, true);
			trails.addProperty("on_arrows", false);
			trails.addProperty("on_pearls", false);
			trails.addProperty("on_self", false);
		}
		String look = swing.get("look") instanceof JsonPrimitive saved ? saved.getAsString() : "SMEAR";
		trails.addProperty("swing_style", "STREAKS".equals(look) ? "RIBBON" : "GHOST");
		trails.addProperty("swing_copies", "OUTLINES".equals(look) ? "OUTLINES" : "SMEAR");
		trails.addProperty("swing_colors", CUSTOM);
		if (swing.has(COLOR)) {
			trails.add("swing_color", swing.get(COLOR));
		}
		if (swing.has("ghosts")) {
			trails.add("swing_count", swing.get("ghosts"));
		}
		if (swing.has("width")) {
			trails.add("swing_width", swing.get("width"));
		}
		if (swing.has("after")) {
			trails.add("fade_time", swing.get("after"));
		}
	}

	private static final String TRAILS = "arrow_trails";
	private static final String SWING_TRAIL = "swing_trail";
	private static final String ENABLED = "enabled";
	private static final String STYLE_KEY = "style";

	private static final String BLOCK_OVERLAY = "block_overlay";
	private static final String STYLE = "mode";
	private static final String OUTLINE_STYLE = "OUTLINE";
	private static final String FILL_STYLE = "FILL";
	private static final String SHADER_STYLE = "SHADER";
	private static final String COLOR = "color";
	private static final String COLOR_MENU = "menu";
	private static final String COLOR_SOURCE = "color_source";
	private static final String FILL = "fill_opacity";
	private static final String RAINBOW = "RAINBOW";
	private static final String ACCENT = "ACCENT";
	private static final String CUSTOM = "CUSTOM";

	private static final String HELD_ITEM = "held_item_transform";
	private static final String OLD_SWING_FACTOR = "swing_speed";
	private static final String SWING_SPEED = "swing_rate";

	/** One change of the format: what brings settings older than this version up to it. */
	record Step(int version, Consumer<JsonObject> apply) {
	}

	private ConfigUpgrade() {
	}

	/** Brings saved settings up to date. */
	public static void upgrade(JsonObject root) {
		run(root, STEPS);
	}

	/** Runs the steps newer than the version the settings were saved in, in order. */
	static void run(JsonObject root, List<Step> steps) {
		int saved = versionOf(root);
		for (Step step : steps) {
			if (saved < step.version()) {
				step.apply().accept(root);
			}
		}
	}

	/** @return the version of the settings format a saved object was written in, 0 for one without a version */
	static int versionOf(JsonObject root) {
		try {
			return root.get(VERSION_KEY) instanceof JsonPrimitive version && version.isNumber() ? Math.max(0, version.getAsInt()) : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
