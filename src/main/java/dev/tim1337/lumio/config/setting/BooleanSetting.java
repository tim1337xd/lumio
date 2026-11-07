package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.CheckboxControl;
import dev.tim1337.lumio.ui.controls.SwitchControl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;

/** An on/off setting, shown as a switch or a checkbox. */
public class BooleanSetting extends Setting<Boolean> implements Toggle {
	/** How the setting is displayed. */
	public enum Style {
		/** A switch to the right of the name. */
		SWITCH,
		/** A checkbox, as used in multi-select lists. */
		CHECKBOX
	}

	private final Style style;

	public BooleanSetting(String key, Component label, boolean defaultValue) {
		this(key, label, defaultValue, Style.SWITCH);
	}

	public BooleanSetting(String key, Component label, boolean defaultValue, Style style) {
		super(key, label, defaultValue);
		this.style = style;
	}

	@Override
	public String valueText() {
		return get() ? "On" : "Off";
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(get());
	}

	@Override
	public void load(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			set(json.getAsBoolean());
		}
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return style == Style.SWITCH ? new SwitchControl(this, onChanged) : new CheckboxControl(this, onChanged);
	}
}
