package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ColorControl;
import dev.tim1337.lumio.ui.controls.ColorSwatchControl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** An RGB color stored as 0xRRGGBB, edited with a color picker and a hex field. */
public class ColorSetting extends Setting<Integer> implements ColorChoice {
	public ColorSetting(String key, Component label, int defaultRgb) {
		super(key, label, defaultRgb & 0xFFFFFF);
	}

	private boolean hasOpacity;
	private float opacity = 1f;

	/** Adds an opacity slider to the picker; the opacity is stored with the color. */
	public ColorSetting withOpacity(float defaultOpacity) {
		this.hasOpacity = true;
		this.opacity = defaultOpacity;
		this.defaultOpacity = defaultOpacity;
		return this;
	}

	private float defaultOpacity = 1f;

	public boolean hasOpacity() {
		return hasOpacity;
	}

	public float opacity() {
		return opacity;
	}

	public void setOpacity(float value) {
		this.opacity = Math.max(0f, Math.min(1f, value));
	}

	/** @return the color as 0xAARRGGBB, including the opacity */
	public int argb() {
		return (Math.round(opacity * 255f) << 24) | (get() & 0xFFFFFF);
	}

	private boolean followsMenu;
	/** Whether the menu's accent color is used instead of the picked one (a checkbox in the color picker). */
	private boolean useMenu;
	/** Whether a fresh config starts with the menu's color: the HUD's elements, so they all follow it. */
	private boolean menuByDefault;

	/** Whether "the menu's color" is the accent as chosen, not the one the theme makes of it: for colors the theme's accent itself comes from. */
	private boolean followsChosen;
	/** The preset colors of the picker; null for the usual ones. */
	private int[] presets;

	/** The menu's color here is the accent as chosen; see {@link #followsChosen}. */
	public ColorSetting followsBaseMenu() {
		this.followsMenu = true;
		this.followsChosen = true;
		return this;
	}

	/** Gives the picker its own preset colors. */
	public ColorSetting withPresets(int[] colors) {
		this.presets = colors;
		return this;
	}

	/** @return the picker's own preset colors, or null for the usual ones */
	public int[] presets() {
		return presets;
	}

	/** Makes this color follow the menu's accent color while the menu is set to color everything. */
	public ColorSetting followsMenu() {
		this.followsMenu = true;
		return this;
	}

	/** Makes the menu's color this color's default, not only a choice: a fresh config and a reset use it. */
	public ColorSetting menuByDefault() {
		this.followsMenu = true;
		this.menuByDefault = true;
		this.useMenu = true;
		return this;
	}

	/** @return whether the menu's color is this color's default */
	public boolean isMenuByDefault() {
		return menuByDefault;
	}

	public boolean canUseMenu() {
		return followsMenu;
	}

	public boolean usesMenu() {
		return followsMenu && useMenu;
	}

	public void setUsesMenu(boolean value) {
		this.useMenu = value;
		notifyChanged();
	}

	/** @return the picked color, also while the menu's is used */
	public int rawRgb() {
		return super.get();
	}

	@Override
	public void reset() {
		super.reset();
		useMenu = menuByDefault;
		opacity = defaultOpacity;
	}

	@Override
	public boolean isDefault() {
		return super.isDefault() && useMenu == menuByDefault && opacity == defaultOpacity;
	}

	@Override
	public Integer get() {
		if (followsMenu && useMenu) {
			return followsChosen ? MenuFeature.chosenAccentRgb() : MenuFeature.accentRgb();
		}
		return super.get();
	}

	@Override
	public void set(Integer value) {
		super.set(value & 0xFFFFFF);
	}

	@Override
	public String valueText() {
		return toHex(get());
	}

	@Override
	public int valueSwatch() {
		return get();
	}

	public static String toHex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb);
	}

	/** @return the parsed color, or -1 if the text is not a complete hex color */
	public static int parseHex(String text) {
		String digits = text.startsWith("#") ? text.substring(1) : text;
		if (digits.length() != 6) {
			return -1;
		}
		try {
			return Integer.parseInt(digits, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	@Override
	public JsonElement save() {
		if (!followsMenu && !hasOpacity) {
			return new JsonPrimitive(toHex(super.get()));
		}
		JsonObject object = new JsonObject();
		object.addProperty("color", toHex(super.get()));
		if (followsMenu) {
			object.addProperty("menu", useMenu);
		}
		if (hasOpacity) {
			object.addProperty("opacity", opacity);
		}
		return object;
	}

	@Override
	public void load(JsonElement json) {
		if (json == null) {
			return;
		}
		JsonElement color = json;
		if (json.isJsonObject()) {
			JsonObject object = json.getAsJsonObject();
			color = object.get("color");
			if (object.has("menu") && object.get("menu").isJsonPrimitive()) {
				useMenu = object.get("menu").getAsBoolean();
			}
			if (object.has("opacity") && object.get("opacity").isJsonPrimitive()) {
				setOpacity(object.get("opacity").getAsFloat());
			}
		}
		if (color != null && color.isJsonPrimitive()) {
			int rgb = parseHex(color.getAsString());
			if (rgb >= 0) {
				set(rgb);
			}
		}
	}
	/** Name of the row this color shares with the colors next to it in its group, or null for a row of its own. */
	private String rowLabel;

	/** Shows this color as a chip in one row with the following colors of the same row, such as "Colors: Mining, Ready". */
	public ColorSetting inRow(String label) {
		this.rowLabel = label;
		return this;
	}

	/** @return the name of the row of colors this one is part of, or null */
	public String rowLabel() {
		return rowLabel;
	}

	/** @return the full color picker for this color, which the swatch in the menu opens as a popup */
	public ColorControl createPicker(Runnable onChanged) {
		return new ColorControl(this, onChanged);
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new ColorSwatchControl(this, null, onChanged);
	}
}
