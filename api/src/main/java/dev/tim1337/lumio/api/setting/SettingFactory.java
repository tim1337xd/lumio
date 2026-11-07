package dev.tim1337.lumio.api.setting;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Makes settings for an add-on's features, the same kinds the main mod's features have. Get it from {@link
 * dev.tim1337.lumio.api.AddonContext#settings()} or, inside a feature, from its {@code settings()}. A key is unique within its feature.
 */
public interface SettingFactory {
	Toggle toggle(String key, Component label, boolean defaultValue);

	Toggle toggle(String key, Component label, boolean defaultValue, Toggle.Style style);

	<E extends Enum<E>> Choice<E> choice(String key, Component label, E defaultValue, Function<E, Component> names);

	<E extends Enum<E>> Choice<E> choice(String key, Component label, E defaultValue, Function<E, Component> names, Choice.Style style);

	/** @param decimals how many decimals the value field shows */
	Slider slider(String key, Component label, float defaultValue, float min, float max, int decimals);

	/**
	 * A choice that saves nothing itself: it is read from and written to other settings, such as one row for a switch and an amount that go together.
	 *
	 * @param read  what it shows now
	 * @param write what a pick does
	 */
	<E extends Enum<E>> Choice<E> derivedChoice(String key, Component label, E defaultValue, Function<E, Component> names, Choice.Style style, Supplier<E> read, Consumer<E> write);

	/** @param defaultRgb the color as 0xRRGGBB */
	ColorChoice color(String key, Component label, int defaultRgb);

	/** @param destructive whether the button is red, for actions that cannot be undone */
	Action action(String key, Component label, Component buttonText, boolean destructive, Runnable action);

	/** @param secret whether it is masked, never shared and never logged (tokens) */
	TextInput text(String key, Component label, String defaultValue, boolean secret);

	MultiToggle multi(String key, Component label, List<? extends Toggle> options);

	OrderedChoice ordered(String key, Component label, List<OrderedChoice.Entry> entries);

	InfoLine info(String key, Component label, Supplier<String> text);
}
