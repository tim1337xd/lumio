package dev.tim1337.lumio.api.experimental.ui;

import dev.tim1337.lumio.api.spi.Backend;

/** The menu's sounds, at the volume the player set (silent while the menu's sounds are off). */
public final class UiSounds {
	private UiSounds() {
	}

	/** The click of a button. */
	public static void click() {
		Backend.get().click();
	}
}
