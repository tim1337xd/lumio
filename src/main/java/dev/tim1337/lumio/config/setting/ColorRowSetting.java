package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ColorRowControl;
import dev.tim1337.lumio.ui.controls.ColorSwatchControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Colors of a group that share one row in the menu ({@link ColorSetting#inRow}). The menu creates it when laying out a page; it has no value,
 * the colors keep their own, and it is a sub-option of whatever the first color is a sub-option of.
 */
public class ColorRowSetting extends Setting<Void> {
	private final List<ColorSetting> colors = new ArrayList<>();

	public ColorRowSetting(String label, ColorSetting first) {
		super("color_row_" + first.key(), Component.literal(label), null);
		colors.add(first);
	}

	public List<ColorSetting> colors() {
		return colors;
	}

	@Override
	public Setting<?> parent() {
		return colors.getFirst().parent();
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}

	/** Creates the row's control from the chips the menu keeps per color, so an open picker survives a rebuild of the page. */
	public Control createControl(List<ColorSwatchControl> chips) {
		return new ColorRowControl(chips);
	}

	@Override
	public Control createControl(Runnable onChanged) {
		List<ColorSwatchControl> chips = new ArrayList<>();
		for (ColorSetting color : colors) {
			chips.add(new ColorSwatchControl(color, color.labelText(), onChanged));
		}
		return new ColorRowControl(chips);
	}
}
