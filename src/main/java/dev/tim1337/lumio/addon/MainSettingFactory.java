package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.InfoLine;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.OrderedChoice;
import dev.tim1337.lumio.api.setting.SettingFactory;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.InfoSetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.config.setting.OrderedListSetting;
import dev.tim1337.lumio.config.setting.StringSetting;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Makes the add-ons' settings: the main mod's own setting classes behind the API's interfaces, so they look and save the same. */
public final class MainSettingFactory implements SettingFactory {
	public static final MainSettingFactory INSTANCE = new MainSettingFactory();

	private MainSettingFactory() {
	}

	@Override
	public Toggle toggle(String key, Component label, boolean defaultValue) {
		return new BooleanSetting(key, label, defaultValue);
	}

	@Override
	public Toggle toggle(String key, Component label, boolean defaultValue, Toggle.Style style) {
		return new BooleanSetting(key, label, defaultValue, style == Toggle.Style.CHECKBOX ? BooleanSetting.Style.CHECKBOX : BooleanSetting.Style.SWITCH);
	}

	@Override
	public <E extends Enum<E>> Choice<E> choice(String key, Component label, E defaultValue, Function<E, Component> names) {
		return new EnumSetting<>(key, label, defaultValue, names);
	}

	private static EnumSetting.Style styleOf(Choice.Style style) {
		return switch (style) {
			case SEGMENTED -> EnumSetting.Style.SEGMENTED;
			case RADIO -> EnumSetting.Style.RADIO;
			case DROPDOWN -> EnumSetting.Style.DROPDOWN;
		};
	}

	@Override
	public <E extends Enum<E>> Choice<E> choice(String key, Component label, E defaultValue, Function<E, Component> names, Choice.Style style) {
		return new EnumSetting<>(key, label, defaultValue, names, styleOf(style));
	}

	@Override
	public <E extends Enum<E>> Choice<E> derivedChoice(String key, Component label, E defaultValue, Function<E, Component> names, Choice.Style style, Supplier<E> read, Consumer<E> write) {
		return new DerivedEnumSetting<>(key, label, defaultValue, names, styleOf(style), read, write);
	}

	@Override
	public Slider slider(String key, Component label, float defaultValue, float min, float max, int decimals) {
		return new FloatSetting(key, label, defaultValue, min, max, decimals);
	}

	@Override
	public ColorChoice color(String key, Component label, int defaultRgb) {
		return new ColorSetting(key, label, defaultRgb);
	}

	@Override
	public Action action(String key, Component label, Component buttonText, boolean destructive, Runnable action) {
		return new ButtonSetting(key, label, buttonText, destructive, action);
	}

	@Override
	public TextInput text(String key, Component label, String defaultValue, boolean secret) {
		return new StringSetting(key, label, defaultValue, secret);
	}

	@Override
	public MultiToggle multi(String key, Component label, List<? extends Toggle> options) {
		List<BooleanSetting> own = new ArrayList<>(options.size());
		for (Toggle option : options) {
			own.add((BooleanSetting) option);
		}
		return new MultiSelectSetting(key, label, own);
	}

	@Override
	public OrderedChoice ordered(String key, Component label, List<OrderedChoice.Entry> entries) {
		List<OrderedListSetting.Option> options = new ArrayList<>(entries.size());
		for (OrderedChoice.Entry entry : entries) {
			options.add(new OrderedListSetting.Option(entry.id(), entry.title(), entry.icon(), entry.onByDefault(), entry.repeatable()));
		}
		return new OrderedListSetting(key, label, options);
	}

	@Override
	public InfoLine info(String key, Component label, Supplier<String> text) {
		return new InfoSetting(key, label, text);
	}
}
