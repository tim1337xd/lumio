package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.PairControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** One row with two settings side by side under one name, such as a style and its strength. It has no value; the two keep their own. */
public class PairSetting extends Setting<Void> {
	private final Setting<?> first;
	private final Setting<?> second;

	public PairSetting(String key, Component label, Setting<?> first, Setting<?> second) {
		super(key, label, null);
		this.first = first;
		this.second = second;
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
		return new PairControl(first.createControl(onChanged), second.createControl(onChanged));
	}
}
