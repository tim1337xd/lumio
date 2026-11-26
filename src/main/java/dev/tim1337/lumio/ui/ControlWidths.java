package dev.tim1337.lumio.ui;

/**
 * The widths of the controls at the right of the settings rows (rule of 2026-11-24): on one page every segmented choice, dropdown, key field,
 * value box and slider has the same width and the same right edge. One standard width for normal rows; a page whose texts really do not fit it
 * takes the one wider width for all of them, never a width per row. Plain logic, tested without the game.
 */
public final class ControlWidths {
	/** The standard width, and the wider one, in GUI pixels. */
	public static final int STANDARD = 180;
	public static final int WIDE = 240;

	private ControlWidths() {
	}

	/**
	 * @param widestNeed the most any of the page's controls needs to show its texts whole
	 * @return the width every control of the page gets: the standard one, or the wider one when a control needs more
	 */
	public static int forPage(int widestNeed) {
		return widestNeed <= STANDARD ? STANDARD : WIDE;
	}
}
