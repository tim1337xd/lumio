package dev.tim1337.lumio.api.spi;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.AddonSetting;

import com.google.gson.JsonObject;

import net.minecraft.client.KeyMapping;

/** The main mod's side of an {@link AddonFeature}: the feature as the main mod keeps it. */
public interface FeatureHandle {
	boolean isEnabled();

	void setEnabled(boolean on);

	/** Adds a setting to the feature's page; section and description may be null. */
	void add(AddonSetting<?> setting, String section, String description);

	void iconRgb(int rgb);

	void iconName(String name);

	void addChild(AddonFeature child);

	void addKey(KeyMapping mapping, KeyPlace place);

	void summary(String text);

	void addResetButton();

	/** @return the feature's settings as the main mod saves them */
	JsonObject saveSettings();

	void loadSettings(JsonObject json);
}
