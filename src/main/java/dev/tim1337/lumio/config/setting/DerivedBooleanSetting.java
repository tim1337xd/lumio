package dev.tim1337.lumio.config.setting;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** A switch that shows another switch in a second place: it stores nothing, the one it stands for does. */
public class DerivedBooleanSetting extends BooleanSetting {
	private final BooleanSetting original;

	public DerivedBooleanSetting(String key, Component label, BooleanSetting original) {
		super(key, label, false);
		this.original = original;
	}

	@Override
	public Boolean get() {
		return original.get();
	}

	@Override
	public void set(Boolean value) {
		original.set(value);
	}

	@Override
	public void reset() {
		// the switch it stands for is reset itself
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
}
