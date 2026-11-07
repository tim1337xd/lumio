package dev.tim1337.lumio.api.experimental.ui;

import dev.tim1337.lumio.api.spi.Backend;

/** The mod's notifications at the top of the screen, in the menu's look. */
public final class Toasts {
	private Toasts() {
	}

	/** Shows a notification with a title and a line of text. */
	public static void info(String title, String text) {
		Backend.get().toast(title, text);
	}

	/** Shows a short notification with an icon, for the given seconds. */
	public static void brief(String icon, String title, String text, float seconds) {
		Backend.get().briefToast(icon, title, text, seconds);
	}
}
