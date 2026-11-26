package dev.tim1337.lumio.api.experimental.ui;

import dev.tim1337.lumio.api.feature.AddonPanel;

import java.util.function.BooleanSupplier;

/** One more status bar from an add-on, such as SkyBlock's arrows: it takes its place in the stack above the hotbar or beside it, like the others. Kept here, and read by the bars every frame. */
public final class ExtraStatusBar {
	private static boolean shown;
	private static int value;
	private static int most;
	private static String name = "";
	private static int numberRgb;
	private static AddonPanel panel;
	private static BooleanSupplier asBar = () -> false;

	private ExtraStatusBar() {
	}

	/**
	 * Shows the bar.
	 * @param value     the number on it
	 * @param most      what the bar is full at
	 * @param name      what the number is of, after it
	 * @param numberRgb the number's color as 0xRRGGBB, or 0 for the usual
	 */
	public static void show(int value, int most, String name, int numberRgb) {
		shown = true;
		ExtraStatusBar.value = value;
		ExtraStatusBar.most = most;
		ExtraStatusBar.name = name;
		ExtraStatusBar.numberRgb = numberRgb;
	}

	/**
	 * Says that the bar and a panel of the add-on are the same thing in two styles (the arrows as a bar or as a panel of text): the HUD
	 * editor lists them as one entry, which selects the bar while it is used and the panel otherwise.
	 *
	 * @param asBar whether the bar is the style used now
	 */
	public static void standsFor(AddonPanel panel, BooleanSupplier asBar) {
		ExtraStatusBar.panel = panel;
		ExtraStatusBar.asBar = asBar;
	}

	/** @return the panel the bar is another style of, or null */
	public static AddonPanel panel() {
		return panel;
	}

	/** @return whether the bar is the style used now, rather than the panel */
	public static boolean asBar() {
		return panel != null && asBar.getAsBoolean();
	}

	public static void hide() {
		shown = false;
	}

	public static boolean shown() {
		return shown;
	}

	public static int value() {
		return value;
	}

	public static int most() {
		return most;
	}

	public static String name() {
		return name;
	}

	public static int numberRgb() {
		return numberRgb;
	}
}
