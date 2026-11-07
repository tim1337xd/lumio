package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.InfoLine;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.InfoControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** A read-only row showing a piece of information that is recomputed every time it is drawn. It has no value and is not persisted. */
public class InfoSetting extends Setting<Void> implements InfoLine {
	private final Supplier<String> text;

	public InfoSetting(String key, Component label, Supplier<String> text) {
		super(key, label, null);
		this.text = text;
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
		return new InfoControl(text);
	}
}
