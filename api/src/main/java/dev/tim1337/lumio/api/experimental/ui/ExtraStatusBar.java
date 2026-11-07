package dev.tim1337.lumio.api.experimental.ui;

/** One more status bar from an add-on, such as SkyBlock's arrows: it takes its place in the stack above the hotbar or beside it, like the others. Kept here, and read by the bars every frame. */
public final class ExtraStatusBar {
	private static boolean shown;
	private static int value;
	private static int most;
	private static String name = "";
	private static int numberRgb;

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
