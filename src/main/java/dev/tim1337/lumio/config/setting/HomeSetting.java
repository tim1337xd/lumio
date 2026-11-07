package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.HomeControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** The content of the home page: status, the features for where the player is, the song and the recent changes. It has no value. */
public class HomeSetting extends Setting<Void> {
	public HomeSetting() {
		super("home", Component.literal("Home"), null);
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
		return new HomeControl(onChanged);
	}
}
