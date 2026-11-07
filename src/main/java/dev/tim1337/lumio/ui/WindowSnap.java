package dev.tim1337.lumio.ui;

/**
 * Where a dragged window of the menu snaps to: the screen's edges (with a margin), the screen's middle, and the edges of the other window, lined
 * up with them or beside them with a gap. Each axis snaps to the nearest place within the snap distance. Nothing is drawn for it: the window simply
 * settles there when it is dropped. Mutable and reused, so nothing is allocated while a window is dragged.
 */
final class WindowSnap {
	/** How close an edge must come to snap, the margin to the screen's edges and the gap beside the other window, in GUI pixels. */
	static final int DISTANCE = 8;

	/** The window's snapped left and top edges. */
	float left;
	float top;

	// the axis being worked out: its best position, and how far that is from where the window is
	private float best;
	private float bestDistance;

	/** No snapping: the window stays where it is dragged to (Shift held). */
	void free(float left, float top) {
		this.left = left;
		this.top = top;
	}

	/**
	 * Finds the snapped place of a window at the given place and size, in GUI pixels on the screen.
	 *
	 * @param other whether there is another window to snap to; its rectangle follows
	 */
	void find(float left, float top, float width, float height, int screenWidth, int screenHeight, boolean other, float otherLeft, float otherTop,
		float otherWidth, float otherHeight) {
		axis(left, width, screenWidth, other, otherLeft, otherWidth);
		this.left = best;
		axis(top, height, screenHeight, other, otherTop, otherHeight);
		this.top = best;
	}

	/** Works out one axis: the window's start and size on it, the screen's size, and the other window's start and size. */
	private void axis(float start, float size, int screen, boolean other, float otherStart, float otherSize) {
		best = start;
		bestDistance = DISTANCE;
		consider(start, DISTANCE);
		consider(start, screen - DISTANCE - size);
		consider(start, (screen - size) / 2f);
		if (other) {
			float otherEnd = otherStart + otherSize;
			// lined up with its edges
			consider(start, otherStart);
			consider(start, otherEnd - size);
			// beside it, with a gap
			consider(start, otherEnd + DISTANCE);
			consider(start, otherStart - DISTANCE - size);
		}
	}

	private void consider(float start, float place) {
		float distance = Math.abs(start - place);
		if (distance < bestDistance) {
			bestDistance = distance;
			best = place;
		}
	}
}
