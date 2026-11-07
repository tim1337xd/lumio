package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ChipsInfoControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** A row of chips that only shows what is on elsewhere, such as the lines picked in a sheet. It has no value. */
public class ChipsInfoSetting extends Setting<Void> {
	private final Supplier<List<String>> chips;

	public ChipsInfoSetting(String key, Supplier<List<String>> chips) {
		super(key, Component.literal(key), null);
		this.chips = chips;
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
		return new ChipsInfoControl(chips);
	}
}
