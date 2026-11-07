package dev.tim1337.lumio.render;

/** Which panes' glows reach into each other: where they meet, they make one soft glow instead of adding up. Plain numbers, so it is unit tested. */
final class GlowNeighbors {
	/** How many neighbors of a pane the shader looks at. */
	static final int MOST = 4;
	/** The number that stands for no neighbor. */
	static final int NONE = 255;
	private static final int VALUES_PER_RECT = 4;

	private GlowNeighbors() {
	}

	/**
	 * @param rects  left, bottom, right and top of what each pane's glow reaches, or all zero for a pane without a glow here
	 * @param count  how many panes there are
	 * @param pane   the pane to find the neighbors of
	 * @param found  receives up to {@link #MOST} neighbors, and {@link #NONE} for the rest
	 */
	static void find(float[] rects, int count, int pane, int[] found) {
		int at = 0;
		int mine = pane * VALUES_PER_RECT;
		boolean hasGlow = rects[mine + 2] > rects[mine];
		for (int other = 0; hasGlow && other < count && at < MOST; other++) {
			int theirs = other * VALUES_PER_RECT;
			if (other != pane && rects[theirs + 2] > rects[theirs] && rects[theirs] < rects[mine + 2] && rects[mine] < rects[theirs + 2]
				&& rects[theirs + 1] < rects[mine + 3] && rects[mine + 1] < rects[theirs + 3]) {
				found[at++] = other;
			}
		}
		while (at < MOST) {
			found[at++] = NONE;
		}
	}
}
