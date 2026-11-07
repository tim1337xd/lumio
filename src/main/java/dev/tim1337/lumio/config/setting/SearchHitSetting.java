package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.SearchHitControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * A search result: the name and location of the matching setting; clicking it navigates there. It has no value of its own. When the match can be
 * toggled (a feature, or a boolean setting), the result shows the switch so it can be toggled without leaving the search.
 */
public class SearchHitSetting extends Setting<Void> {
	private final String path;
	private final Runnable open;
	private final BooleanSupplier state;
	private final Runnable flip;
	private String icon;
	private int iconRgb;

	public SearchHitSetting(String key, Component label, String path, Runnable open) {
		this(key, label, path, open, null, null);
	}

	/**
	 * @param state whether the match is currently on, or null if it cannot be toggled
	 * @param flip  toggles the match
	 */
	public SearchHitSetting(String key, Component label, String path, Runnable open, BooleanSupplier state, Runnable flip) {
		super(key, label, null);
		this.path = path;
		this.open = open;
		this.state = state;
		this.flip = flip;
	}

	/** Shows an icon on a tile in its color in front of the result, such as the icon of the page it lives on. */
	public SearchHitSetting withIcon(String name, int rgb) {
		icon = name;
		iconRgb = rgb;
		return this;
	}

	/** @return the icon in front of the result, or null */
	public String icon() {
		return icon;
	}

	public int iconRgb() {
		return iconRgb;
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new SearchHitControl(path, open, state, flip, onChanged);
	}
}
