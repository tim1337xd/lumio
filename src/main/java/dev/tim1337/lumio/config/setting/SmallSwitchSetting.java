package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.SmallSwitchControl;

import com.google.gson.JsonElement;

/** A switch shown small, with its name, inside a row of another setting. It has no value of its own: it stands for the switch it shows. */
public class SmallSwitchSetting extends Setting<Void> {
	private final BooleanSetting target;

	public SmallSwitchSetting(BooleanSetting target) {
		super(target.key() + "_small", target.label(), null);
		this.target = target;
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
		return new SmallSwitchControl(target, onChanged);
	}
}
