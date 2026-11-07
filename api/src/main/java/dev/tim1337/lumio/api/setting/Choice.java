package dev.tim1337.lumio.api.setting;

import net.minecraft.network.chat.Component;

import java.util.List;

/** One of the constants of an enum. */
public interface Choice<E extends Enum<E>> extends AddonSetting<E> {
	/** How the choices are drawn. */
	enum Style {
		/** Side by side in a segmented bar, for a few short choices. */
		SEGMENTED,
		/** A list of radio buttons, for choices that need some room. */
		RADIO,
		/** A dropdown menu, for many choices. */
		DROPDOWN
	}

	/** @return the choices, in their order */
	List<E> choices();

	/** @return the name of a choice as the menu shows it */
	Component nameOf(E choice);
}
