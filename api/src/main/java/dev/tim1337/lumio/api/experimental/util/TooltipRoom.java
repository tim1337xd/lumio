package dev.tim1337.lumio.api.experimental.util;

/**
 * Places an item's tooltip keeps clear of. Tooltips are drawn over everything of the mod and its add-ons; what must stay usable under the pointer's
 * tooltip, such as a search bar, says so here each frame while it is drawn, and the tooltip moves away from it.
 */
public final class TooltipRoom {
	private static final int MOST = 8;
	private static final int VALUES = 4;
	private static final int[] BOXES = new int[MOST * VALUES];
	private static int count;

	private TooltipRoom() {
	}

	/** Keeps tooltips off a box of this frame, in GUI pixels. */
	public static void keepClear(int x, int y, int width, int height) {
		if (count < MOST && width > 0 && height > 0) {
			int at = count++ * VALUES;
			BOXES[at] = x;
			BOXES[at + 1] = y;
			BOXES[at + 2] = width;
			BOXES[at + 3] = height;
		}
	}

	/** @return how many boxes this frame named */
	public static int count() {
		return count;
	}

	/** @return a value of a box: {@code 0} its left, {@code 1} its top, {@code 2} its width, {@code 3} its height */
	public static int value(int box, int value) {
		return BOXES[box * VALUES + value];
	}

	/** A new frame: the boxes of the last are gone. */
	public static void clear() {
		count = 0;
	}
}
