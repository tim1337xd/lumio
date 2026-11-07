package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ModuleKeysControl;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;

/**
 * Every key of the mod and its add-ons in one list, on the Keybinds page: each can be set again in its row, conflicts are marked, and the keys
 * without a key are folded away. The keys stay where the game keeps them (see {@link KeySetting}); nothing of this is in the mod's config.
 */
public class ModuleKeysSetting extends Setting<Void> {
	public ModuleKeysSetting(String id, Component label) {
		super(id, label, null);
	}

	@Override
	public void reset() {
	}

	@Override
	public boolean isDefault() {
		return true;
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
		// the conflicts are worked out when the menu opens and when a key changes (see KeySetting)
		return new ModuleKeysControl(onChanged);
	}
}
