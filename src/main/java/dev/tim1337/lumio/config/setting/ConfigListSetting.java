package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ConfigListControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** The list of saved configs with their actions. It has no value of its own; every config is a separate file. */
public class ConfigListSetting extends Setting<Void> {
	public ConfigListSetting(String key, Component label) {
		super(key, label, null);
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
		return new ConfigListControl(onChanged);
	}
}
