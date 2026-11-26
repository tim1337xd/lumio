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

	/** A key that acts only while one menu is open, with the menu's name as the player knows it ("Loadouts"). */
	void addMenuKey(KeyMapping mapping, String menu);

	void summary(String text);

	/** A short badge after the feature's name in the feature lists; none by default. */
	default void badge(String text) {
	}

	/** Words the menu's search also finds the feature by. */
	default void searchWords(String words) {
	}

	/** Names the group the feature is listed under on a long page. */
	default void pageGroup(String name) {
	}

	/** Flags the feature as work in progress, for the tag in the feature lists. */
	default void workInProgress() {
	}

	/** The settings shown on the feature's card on a page of cards; none by default. */
	default void cardSettings(java.util.List<? extends dev.tim1337.lumio.api.setting.AddonSetting<?>> settings) {
	}

	void addResetButton();

	/** @return the feature's settings as the main mod saves them */
	JsonObject saveSettings();

	void loadSettings(JsonObject json);
}
