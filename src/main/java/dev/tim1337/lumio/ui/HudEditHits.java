package dev.tim1337.lumio.ui;

/**
 * The clickable regions of one part of the HUD editor (toolbar, card, list, menu), refilled every frame while it is drawn, so drawing and
 * clicking always agree. Kept in fixed arrays, so refilling them allocates nothing.
 */
final class HudEditHits {
	private static final int MOST = 96;
	private final int[] ids = new int[MOST];
	private final int[] xs = new int[MOST];
	private final int[] ys = new int[MOST];
	private final int[] widths = new int[MOST];
	private final int[] heights = new int[MOST];
	private int count;

	void clear() {
		count = 0;
	}

	void add(int id, int x, int y, int width, int height) {
		if (count < MOST) {
			ids[count] = id;
			xs[count] = x;
			ys[count] = y;
			widths[count] = width;
			heights[count] = height;
			count++;
		}
	}

	/** @return the region under the mouse, the one added last when they overlap, or -1 */
	int at(double mouseX, double mouseY) {
		for (int i = count - 1; i >= 0; i--) {
			if (mouseX >= xs[i] && mouseX < xs[i] + widths[i] && mouseY >= ys[i] && mouseY < ys[i] + heights[i]) {
				return ids[i];
			}
		}
		return -1;
	}

	/** @return the left edge of a region from the last frame, or -1 if it was not drawn */
	int left(int id) {
		for (int i = 0; i < count; i++) {
			if (ids[i] == id) {
				return xs[i];
			}
		}
		return -1;
	}

	/** @return the width of a region from the last frame, or 0 */
	int width(int id) {
		for (int i = 0; i < count; i++) {
			if (ids[i] == id) {
				return widths[i];
			}
		}
		return 0;
	}
}
