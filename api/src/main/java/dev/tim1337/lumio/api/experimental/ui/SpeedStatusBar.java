package dev.tim1337.lumio.api.experimental.ui;

/** The value of the Speed status bar, from an add-on that knows it (SkyBlock's Speed): the bar takes its place among the others while it is shown. Kept here, and read by the bars every frame. */
public final class SpeedStatusBar {
	private static boolean shown;
	private static int value;
	private static int most;

	private SpeedStatusBar() {
	}

	/**
	 * Shows the bar.
	 * @param value the number on it
	 * @param most  what the bar is full at
	 */
	public static void show(int value, int most) {
		shown = true;
		SpeedStatusBar.value = value;
		SpeedStatusBar.most = most;
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
}
