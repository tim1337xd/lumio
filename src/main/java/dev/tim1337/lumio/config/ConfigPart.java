package dev.tim1337.lumio.config;

import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.features.visual.hud.HotbarFeature;
import dev.tim1337.lumio.features.visual.hud.StatusBarsHudFeature;
import dev.tim1337.lumio.features.visual.world.HeldItemTransformFeature;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.EditableHud;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The parts a config consists of, so that a config can be loaded partially: features (enabled state and all settings), colors, and the HUD
 * layout (positions and sizes). Every setting belongs to exactly one part.
 */
public enum ConfigPart {
	FEATURES("features", "Features and their settings"),
	COLORS("colors", "Colors and theme"),
	LAYOUT("layout", "HUD positions and sizes");

	/** Setting keys that store the position, size or per-element look (depth effect) of a HUD element. */
	private static final Set<String> LAYOUT_KEYS = Set.of("x", "y", "scale", "x_offset", "y_offset", "top", "window_x", "window_y", "name_dx", "name_dy", "name_scale",
		"chat_size", "panel_depth", "see_through", "anchor_x", "anchor_y");
	/** The status bars store a position and size per bar: health_x, mana_scale, ... */
	private static final Pattern BAR_LAYOUT = Pattern.compile("^[a-z]+_(x|y|scale)$");
	/** Menu setting keys that are part of the look: the theme, the accent and the depth effect of the HUD. */
	private static final Set<String> MENU_COLOR_KEYS = Set.of("theme", "accent", "hud_depth", "hud_accent", "hud_background", "hud_see_through", "hud_glow", "hud_glow_soft", "hud_size",
		"hud_width_mode", "hud_width", "hud_line_spacing", "hud_title", "hud_status");

	public final String key;
	public final String label;

	ConfigPart(String key, String label) {
		this.key = key;
		this.label = label;
	}

	public static Set<ConfigPart> all() {
		return EnumSet.allOf(ConfigPart.class);
	}

	/**
	 * @return how a HUD position or size scales with the screen: 'x' for a horizontal distance, 'y' for a vertical one, 's' for a size, or 0 for
	 *         neither. Positions are distances from an edge (or the center) in GUI units, of which there are fewer on small screens or large GUI scales.
	 */
	public static char axisOf(String key) {
		if (key.equals("x") || key.equals("x_offset") || key.equals("window_x") || key.equals("name_dx") || key.endsWith("_x")) {
			return 'x';
		}
		if (key.equals("y") || key.equals("y_offset") || key.equals("top") || key.equals("window_y") || key.equals("name_dy") || key.endsWith("_y")) {
			return 'y';
		}
		if (key.equals("scale") || key.equals("name_scale") || key.endsWith("_scale")) {
			return 's';
		}
		return 0;
	}

	/** @return the part a feature setting belongs to, based on its key and type */
	public static ConfigPart of(Feature feature, String key, Setting<?> setting) {
		if (setting instanceof ColorSetting || (feature instanceof MenuFeature && MENU_COLOR_KEYS.contains(key))) {
			return COLORS;
		}
		// HUD element positions and sizes; the held item transform is excluded, its "position" is how the item is held
		boolean hud = (feature instanceof EditableHud || feature instanceof HotbarFeature || feature instanceof StatusBarsHudFeature) && !(feature instanceof HeldItemTransformFeature);
		if (hud && (LAYOUT_KEYS.contains(key) || feature instanceof StatusBarsHudFeature && BAR_LAYOUT.matcher(key).matches())) {
			return LAYOUT;
		}
		// the enabled state and every other setting
		return FEATURES;
	}
}
