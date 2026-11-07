package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.spi.Backend;

/** The few settings of the main mod an add-on needs to look and behave like it, read-only. */
public final class MainSettings {
	private MainSettings() {
	}

	/** @return whether things move in the menus and panels (the menu's Animations switch) */
	public static boolean animations() {
		return Backend.get().animations();
	}

	/**
	 * @return always true: there is no such setting in Lumio any more
	 * @deprecated an add-on with moments of its own keeps its own switch; removed in the next major version of the API
	 */
	@Deprecated
	public static boolean celebrations() {
		return true;
	}

	/** @return how much windows and switches overshoot, from 0 to 1 */
	public static float bounce() {
		return Backend.get().bounce();
	}

	/** @return the menu's accent color as 0xRRGGBB */
	public static int accentRgb() {
		return Backend.get().accentRgb();
	}

	/** @return where the info bar was last drawn (x, y, width, height in GUI pixels), or null while it is off or not shown; the array is shared */
	public static int[] infoBarBounds() {
		return Backend.get().infoBarBounds();
	}

	/** @return whether the main mod restyles the inventory screen (its sections are spaced out) */
	public static boolean inventoryStyled() {
		return Backend.get().inventoryStyled();
	}
}
