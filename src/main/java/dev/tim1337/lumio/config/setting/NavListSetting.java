package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.NavListControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The rows of a page's overview, each opening a sub-page: an icon tile, its name, what is set there in gray on the right and a chevron, in one
 * card, with a line under it. It has no value; the rows read what they show from the settings.
 */
public class NavListSetting extends Setting<Void> {
	/**
	 * One row.
	 *
	 * @param value what is set, read now and then while shown
	 * @param dot   the color of a status dot before the value, read with it; negative for none, null for a row without one
	 * @param open  opens the row's sub-page
	 */
	public record Item(String icon, int rgb, String title, Supplier<String> value, IntSupplier dot, Runnable open) {
	}

	private final List<Item> items;
	private final String note;

	/** @param note the line under the rows, or null */
	public NavListSetting(String key, Component label, List<Item> items, String note) {
		super(key, label, null);
		this.items = items;
		this.note = note;
	}

	public List<Item> items() {
		return items;
	}

	public String note() {
		return note;
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
		return new NavListControl(this);
	}
}
