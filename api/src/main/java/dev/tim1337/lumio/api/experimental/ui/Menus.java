package dev.tim1337.lumio.api.experimental.ui;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.spi.Backend;

/** The Lumio menu, for an add-on's own screens. */
public final class Menus {
	private Menus() {
	}

	/** Opens the menu directly on a feature's settings, over the screen now open; Esc or the back button returns to that screen as it was. */
	public static void openSettings(AddonFeature feature) {
		Backend.get().openSettings(feature);
	}

	/** Opens the menu on its Keybinds page, for a "Set a key" of an add-on's own. */
	public static void openKeybinds() {
		Backend.get().openKeybinds();
	}
}
