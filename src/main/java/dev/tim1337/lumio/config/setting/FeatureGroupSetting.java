package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.FeatureListControl;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.List;

/** A group of features on a feature list page, shown as rows on one card. It has no value; the features keep their own. */
public class FeatureGroupSetting extends Setting<Void> {
	private final List<Feature> features;

	public FeatureGroupSetting(String key, Component label, List<Feature> features) {
		super(key, label, null);
		this.features = features;
	}

	public List<Feature> features() {
		return features;
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
		return new FeatureListControl(features, onChanged);
	}
}
