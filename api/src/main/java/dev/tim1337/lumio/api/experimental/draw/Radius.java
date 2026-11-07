package dev.tim1337.lumio.api.experimental.draw;

/**
 * Corner radii in menu pixels: the few sizes everything is drawn with, so elements of the same kind are equally rounded. Shapes with fully
 * rounded ends (a switch, a chip, a bar) use {@link #pill}.
 */
public final class Radius {
	private Radius() {
	}

	/** Thin elements: lines and bars a few pixels high, the menu arrow. */
	public static final float XXS = 2f;
	/** Small marks: text selection, small chips and swatches, checkboxes. */
	public static final float XS = 4f;
	/** Controls: buttons, fields, dropdowns, segmented controls, slots. */
	public static final float SM = 6f;
	/** Rows, list items, popup menus. */
	public static final float MD = 8f;
	/** Cards (settings groups). */
	public static final float LG = 10f;
	/** HUD panels, notifications, option cards. */
	public static final float XL = 12f;
	/** Windows and large panels. */
	public static final float XXL = 14f;
	/** A large floating window. */
	public static final float WINDOW = 22f;

	/** @return the radius that fully rounds both ends of a shape of the given height */
	public static float pill(float height) {
		return height / 2f;
	}
}
