package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.Control;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

/** The whole Waypoints page as one row. It has no value of its own: the groups are kept in files of their own ({@link WaypointStore}). */
final class WaypointsPageSetting extends Setting<Void> {
	private final WaypointsFeature feature;

	WaypointsPageSetting(String key, WaypointsFeature feature) {
		super(key, Component.literal("Waypoints"), null);
		this.feature = feature;
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
		return new WaypointsPage(feature);
	}
}
