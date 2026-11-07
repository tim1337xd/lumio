package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.NoteControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** A short note on a settings page, such as what a feature does on servers: an info icon and secondary text that wraps. Not persisted. */
public class NoteSetting extends Setting<Void> {
	private final String text;

	public NoteSetting(String key, String text) {
		super(key, Component.literal(text), null);
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
		return new NoteControl(text);
	}
}
