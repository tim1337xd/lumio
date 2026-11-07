package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.config.RecentChanges;
import dev.tim1337.lumio.ui.Control;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A single user-configurable value of a feature. A setting knows how to persist itself and how to build the control
 * that edits it, so the settings screen never needs to know which settings exist.
 */
public abstract class Setting<T> implements AddonSetting<T> {
	private final String key;
	private Component label;
	private T defaultValue;
	private T value;
	private BooleanSupplier visibility = () -> true;
	private BooleanSupplier disabled = () -> false;
	private String section;
	/** Short plain-language explanation shown under the name, or null when the name is self-explanatory. */
	private String description;

	protected Setting(String key, Component label, T defaultValue) {
		this.key = key;
		this.label = label;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String key() {
		return key;
	}

	public Component label() {
		return label;
	}

	/** Gives the setting another name on its page; what is saved stays under its key. */
	public void rename(String name) {
		label = Component.literal(name);
		labelText = name;
	}

	private String labelText;

	/** @return the label as plain text, built once: menus draw it every frame */
	public String labelText() {
		if (labelText == null) {
			labelText = label.getString();
		}
		return labelText;
	}

	/** @return the explanation shown under the name, or null */
	public String description() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description == null || description.isBlank() ? null : description;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		// before the value changes, so the list of recent changes knows the old value
		RecentChanges.changing(this);
		this.value = value;
		notifyChanged();
	}

	/** Called after every change the player or the game makes through {@link #set}. */
	private Runnable whenSet;

	/** Runs something after every change of the value; one listener, for what depends on the setting. */
	public void whenSet(Runnable listener) {
		whenSet = listener;
	}

	/** Tells the listener of a change made other than through {@link #set}. */
	protected final void notifyChanged() {
		if (whenSet != null) {
			whenSet.run();
		}
	}

	/** Puts a value in without it counting as a change: no recent change, no listener. For values copied from elsewhere. */
	public void setQuietly(T value) {
		this.value = value;
	}

	/** @return the value as a short text, for the list of recent changes on the home page; null when it has none (lists, keys) */
	public String valueText() {
		return null;
	}

	/** @return the color shown before {@link #valueText} as 0xRRGGBB, or -1 for none */
	public int valueSwatch() {
		return -1;
	}

	private boolean listedInRecent = true;

	/** Keeps the setting off the recent changes on the home page, for values the menu keeps by itself, such as where its window is. */
	public void hideFromRecent() {
		listedInRecent = false;
	}

	/** @return whether a change of the setting is listed among the recent changes */
	public boolean listedInRecent() {
		return listedInRecent;
	}

	public void reset() {
		this.value = defaultValue;
	}

	/** @return whether the setting has its default value; settings that are not persisted (buttons, information) always count as default */
	public boolean isDefault() {
		return save() == null || Objects.equals(value, defaultValue);
	}

	/** Shows the setting in the settings screen only while the condition is true. */
	public void visibleWhen(BooleanSupplier condition) {
		this.visibility = condition;
	}

	/** The setting this one is a sub-option of, or null. */
	private Setting<?> parent;

	/** Makes this a sub-option of a switch: shown indented under it, in its card, while the switch is shown and on. */
	public void under(Toggle parent) {
		under(parent, parent::get);
	}

	/** Makes this a sub-option of another setting: shown indented under it, in its card, while that one is shown and the condition is true. */
	public void under(AddonSetting<?> parent, BooleanSupplier condition) {
		// every setting is one of the main mod's, also those an add-on made through the API
		Setting<?> own = (Setting<?>) parent;
		this.parent = own;
		this.visibility = () -> own.isVisible() && condition.getAsBoolean();
	}

	/** @return the setting this one is a sub-option of, or null */
	public Setting<?> parent() {
		return parent;
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	/** Greys out the setting in the settings screen while the condition is true. */
	public void disabledWhen(BooleanSupplier condition) {
		this.disabled = condition;
	}

	public boolean isDisabled() {
		return disabled.getAsBoolean();
	}

	private String disabledNote;

	/** Greys out the setting while the condition is true, with a short note on why. */
	public void disabledWhen(BooleanSupplier condition, String note) {
		this.disabled = condition;
		this.disabledNote = note;
	}

	/** @return the short note on why it is greyed out, or null for the usual one */
	public String disabledNote() {
		return disabledNote;
	}

	private boolean editorOnly;

	public boolean isEditorOnly() {
		return editorOnly;
	}

	/** @return title of the settings group this setting belongs to, or null for the first, untitled group */
	public String section() {
		return section;
	}

	public void setSection(String section) {
		this.section = section;
	}

	/**
	 * @return the value to persist, or null if the setting is not persisted (e.g. a button)
	 */
	public abstract JsonElement save();

	/** Must tolerate malformed input and leave the current value untouched in that case. */
	public abstract void load(JsonElement json);

	/**
	 * Builds the control that edits this setting.
	 *
	 * @param onChanged to be called whenever the value was changed through the control
	 */
	public abstract Control createControl(Runnable onChanged);

	/** Changes the default and the current value: for settings shared by many features (the glow of every panel) that only some enable by default. */
	public void setDefault(T value) {
		this.defaultValue = value;
		this.value = value;
	}
}
