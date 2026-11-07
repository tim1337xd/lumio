package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.config.Feature;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** A switch that toggles another feature, so it can be reached from a page other than the feature's own. The value lives in that feature. */
public class FeatureSwitchSetting extends BooleanSetting {
	private final Supplier<Feature> feature;

	public FeatureSwitchSetting(String key, Component label, Supplier<Feature> feature) {
		super(key, label, false);
		this.feature = feature;
	}

	@Override
	public Boolean get() {
		Feature target = feature.get();
		return target.children().isEmpty() ? target.isEnabled() : target.children().stream().anyMatch(Feature::isEnabled);
	}

	@Override
	public void set(Boolean value) {
		feature.get().setEnabled(value);
	}

	@Override
	public void reset() {
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}
}
