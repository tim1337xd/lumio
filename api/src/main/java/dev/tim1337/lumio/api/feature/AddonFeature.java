package dev.tim1337.lumio.api.feature;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.SettingFactory;
import dev.tim1337.lumio.api.spi.Backend;
import dev.tim1337.lumio.api.spi.FeatureHandle;

import com.google.gson.JsonObject;

import net.minecraft.client.KeyMapping;

import java.util.function.Function;

/**
 * The base class of an add-on's features: a feature with an id, a name, an on/off switch, settings and its own page in the menu, saved in the
 * add-on's settings file. Make the settings with {@link #settings()} and add them with {@code add}; register the feature with
 * {@link dev.tim1337.lumio.api.AddonContext#registerFeature}.
 */
public abstract class AddonFeature implements FeatureView {
	private final String id;
	private final String nameText;
	/** The main mod's side of this feature. */
	final FeatureHandle handle;

	/** A feature that can be switched off. */
	protected AddonFeature(String id, String name, String description, boolean enabledByDefault) {
		this(id, name, description, enabledByDefault, true);
	}

	/** @param hasSwitch whether the feature can be turned off; the switch is shown at the top of its page */
	protected AddonFeature(String id, String name, String description, boolean enabledByDefault, boolean hasSwitch) {
		this(id, name, owner -> Backend.get().features().feature(owner, id, name, description, enabledByDefault, hasSwitch));
	}

	/** For the kinds of features the API has (panels): their own handle. */
	AddonFeature(String id, String name, Function<AddonFeature, ? extends FeatureHandle> handle) {
		this.id = id;
		this.nameText = name;
		this.handle = handle.apply(this);
	}

	@Override
	public final String id() {
		return id;
	}

	@Override
	public final String nameText() {
		return nameText;
	}

	/** @return whether it is switched on (and its add-on's master switch, if it has one, too) */
	@Override
	public final boolean isEnabled() {
		return handle.isEnabled();
	}

	public final void setEnabled(boolean on) {
		handle.setEnabled(on);
	}

	/** @return the factory to make this feature's settings with */
	protected final SettingFactory settings() {
		return Backend.get().settings();
	}

	/** Adds a setting to the feature's page. @return the setting */
	protected final <S extends AddonSetting<?>> S add(S setting) {
		handle.add(setting, null, null);
		return setting;
	}

	/** @param section the title of the group on the feature's page the setting belongs to */
	protected final <S extends AddonSetting<?>> S add(S setting, String section) {
		handle.add(setting, section, null);
		return setting;
	}

	/** @param description a short line under the setting's name, only where the name alone would be confusing */
	protected final <S extends AddonSetting<?>> S add(S setting, String section, String description) {
		handle.add(setting, section, description);
		return setting;
	}

	/** Sets the color of the feature's icon tile, as 0xRRGGBB. */
	protected final void iconRgb(int rgb) {
		handle.iconRgb(rgb);
	}

	/** Sets the feature's icon, one of the mod's icons by name. */
	protected final void iconName(String name) {
		handle.iconName(name);
	}

	/** Makes another feature a part of this one: its settings show on this feature's page, and it is on while this one is. */
	protected final void addChild(AddonFeature child) {
		handle.addChild(child);
	}

	/** Lists a key of the feature on its page, in its Keys group, where the player can change it. */
	protected final void addKey(KeyMapping mapping, KeyPlace place) {
		handle.addKey(mapping, place);
	}

	/** Sets the one line about the feature in the feature lists; without it they show its description. */
	protected final void summary(String text) {
		handle.summary(text);
	}

	/** Adds a button at the end of the page that sets every setting of the feature back to its default. */
	protected final void addResetButton() {
		handle.addResetButton();
	}

	/** @return the feature's saved state; override to save more, starting from {@code super.save()} */
	public JsonObject save() {
		return handle.saveSettings();
	}

	/** Reads the feature's saved state; override to read more, calling {@code super.load(json)} */
	public void load(JsonObject json) {
		handle.loadSettings(json);
	}

	/** @return whether the feature's settings open in a window of their own rather than on a page */
	public boolean ownWindow() {
		return false;
	}

	/** @return whether the feature's page shows its groups as tabs even when there are few */
	public boolean alwaysTabbed() {
		return false;
	}

	/** @return whether the feature has a live preview; the menu shows a feature's settings in its pane and draws no preview for them at present, so this has no effect */
	public boolean hasPreview() {
		return false;
	}

	/** Draws the preview, on its background, in menu pixels; every frame while the page is shown, so it should not allocate. */
	public void drawPreview(Canvas ui, int x, int y, int width, int height) {
	}

	/** @return the line under the preview */
	public String previewNote() {
		return "Updates live while you change the settings.";
	}
}
