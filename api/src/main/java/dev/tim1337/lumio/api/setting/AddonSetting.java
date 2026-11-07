package dev.tim1337.lumio.api.setting;

import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * A setting of a feature: a value with a key (its name in the settings file), a label, and when it is shown. Made through a
 * {@link SettingFactory}; the menu draws its row.
 *
 * @param <T> the type of its value ({@link Void} for rows without one, such as a button)
 */
public interface AddonSetting<T> {
	/** @return the key it is saved under */
	String key();

	Component label();

	/** @return the label as plain text */
	String labelText();

	T get();

	void set(T value);

	/** Sets it back to its default. */
	void reset();

	/** Sets the gray line under its label; null or blank for none. */
	void setDescription(String description);

	/** Shows it only while the condition holds. */
	void visibleWhen(BooleanSupplier condition);

	/** Shows it indented under a switch, only while that switch is on. */
	void under(Toggle parent);

	/** Shows it indented under another setting, only while the condition holds. */
	void under(AddonSetting<?> parent, BooleanSupplier condition);

	/** Keeps it out of the list of recent changes on the Home page. */
	void hideFromRecent();
}
