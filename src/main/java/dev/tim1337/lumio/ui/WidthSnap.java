package dev.tim1337.lumio.ui;

/**
 * Soft snapping of a width dragged in the HUD editor: near one of a few useful widths (the hotbar's, half of it, the default) it holds there, a
 * little further it lets go. Shift drags freely.
 */
public final class WidthSnap {
	/** How near a useful width the dragged width has to be to hold there, in GUI pixels on screen. */
	public static final float REACH = 5f;

	private WidthSnap() {
	}

	/**
	 * @param reach how near counts, in the same units as the widths
	 * @return the nearest of the stops within reach, or the width itself; NaN stops are ignored
	 */
	public static float snap(float width, float reach, float... stops) {
		float best = width;
		float nearest = reach;
		for (float stop : stops) {
			if (!Float.isNaN(stop) && Math.abs(width - stop) <= nearest) {
				nearest = Math.abs(width - stop);
				best = stop;
			}
		}
		return best;
	}

	/** @return a width as the editor labels it while dragging: "214 px" */
	public static String label(float width) {
		return Math.round(width) + " px";
	}
}
