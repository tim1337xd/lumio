package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ChipToggleControl;
import dev.tim1337.lumio.ui.controls.MultiDropdownControl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A multi-select list. Every choice is a checkbox setting of its own; they are persisted together under this setting's key.
 */
public class MultiSelectSetting extends Setting<Void> implements MultiToggle {
	private final List<BooleanSetting> options;
	/** Whether the choices are shown as pills that switch on and off, all in view, instead of a dropdown. */
	private boolean chips;
	/** Whether the choices are settings that persist themselves elsewhere, so this one only shows them and saves nothing. */
	private boolean viewOnly;

	public MultiSelectSetting(String key, Component label, List<BooleanSetting> options) {
		super(key, label, null);
		this.options = options;
	}

	/** Shows the choices as pills that switch on and off, for a few short choices all worth seeing at once. */
	public MultiSelectSetting asChips() {
		chips = true;
		return this;
	}

	/** Shows switches that are settings of their own, saved where they are; this setting saves nothing itself. */
	public MultiSelectSetting viewOnly() {
		viewOnly = true;
		return this;
	}

	public List<BooleanSetting> options() {
		return options;
	}

	@Override
	public void reset() {
		options.forEach(Setting::reset);
	}

	@Override
	public boolean isDefault() {
		for (BooleanSetting option : options) {
			if (!option.isDefault()) {
				return false;
			}
		}
		return true;
	}

	/** @return the names of the chosen options, or "None" */
	@Override
	public String valueText() {
		StringBuilder text = new StringBuilder();
		for (BooleanSetting option : options) {
			if (option.get()) {
				text.append(text.isEmpty() ? "" : ", ").append(option.labelText());
			}
		}
		return text.isEmpty() ? "None" : text.toString();
	}

	@Override
	public JsonElement save() {
		if (viewOnly) {
			return null;
		}
		JsonObject json = new JsonObject();
		for (BooleanSetting option : options) {
			json.add(option.key(), option.save());
		}
		return json;
	}

	@Override
	public void load(JsonElement json) {
		if (viewOnly) {
			return;
		}
		if (json != null && json.isJsonObject()) {
			for (BooleanSetting option : options) {
				option.load(json.getAsJsonObject().get(option.key()));
			}
		}
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return chips ? new ChipToggleControl(this, onChanged) : new MultiDropdownControl(this, onChanged);
	}
}
