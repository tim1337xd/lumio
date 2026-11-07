package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.FeatureRowControl;

import com.google.gson.JsonElement;

/** A row of the feature list: the name, an enable switch and a button that opens the settings. It has no value of its own. */
public class FeatureRowSetting extends Setting<Void> {
	private final Feature feature;
	private final boolean editable;

	public FeatureRowSetting(Feature feature, boolean editable) {
		super("row_" + feature.id(), feature.name(), null);
		this.feature = feature;
		this.editable = editable;
	}

	public Feature feature() {
		return feature;
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
		return new FeatureRowControl(feature, editable, onChanged);
	}
}
