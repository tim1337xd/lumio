package dev.tim1337.lumio.config.setting;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.controls.ColorDotsControl;

import net.minecraft.network.chat.Component;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A choice of colors shown as dots in a row (such as Accent, Cover, White and a custom one): each option is a dot in its color right now, the
 * chosen one has a ring. The custom option is a "+" until it is chosen; its color is a setting of its own, edited in a row under this one.
 */
public class ColorDotsSetting<E extends Enum<E>> extends EnumSetting<E> {
	private final ToIntFunction<E> color;
	private final E custom;

	/**
	 * @param color  the color of an option right now (an accent or a cover color changes)
	 * @param custom the option for the custom color, or null
	 */
	public ColorDotsSetting(String key, Component label, E defaultValue, Function<E, Component> displayName, ToIntFunction<E> color, E custom) {
		super(key, label, defaultValue, displayName, Style.SEGMENTED);
		this.color = color;
		this.custom = custom;
	}

	/** @return the color of an option right now, as ARGB */
	public int colorOf(E option) {
		return color.applyAsInt(option);
	}

	public E custom() {
		return custom;
	}

	@Override
	public Control createControl(Runnable onChanged) {
		return new ColorDotsControl<>(this, onChanged);
	}
}
