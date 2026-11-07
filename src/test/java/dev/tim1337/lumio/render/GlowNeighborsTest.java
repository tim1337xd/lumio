package dev.tim1337.lumio.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Two panes whose glows reach into each other find each other; others do not. */
class GlowNeighborsTest {
	private static final int NONE = GlowNeighbors.NONE;

	private static int[] of(float[] rects, int pane) {
		int[] found = new int[GlowNeighbors.MOST];
		GlowNeighbors.find(rects, rects.length / 4, pane, found);
		return found;
	}

	@Test
	void panesApartHaveNoNeighbors() {
		float[] rects = {0, 0, 10, 10, 20, 0, 30, 10};
		assertArrayEquals(new int[]{NONE, NONE, NONE, NONE}, of(rects, 0));
	}

	@Test
	void panesWhoseGlowsMeetAreNeighbors() {
		float[] rects = {0, 0, 10, 10, 8, 0, 20, 10};
		assertArrayEquals(new int[]{1, NONE, NONE, NONE}, of(rects, 0));
		assertArrayEquals(new int[]{0, NONE, NONE, NONE}, of(rects, 1));
	}

	@Test
	void aPaneWithoutAGlowHereIsNoNeighbor() {
		float[] rects = {0, 0, 10, 10, 0, 0, 0, 0};
		assertArrayEquals(new int[]{NONE, NONE, NONE, NONE}, of(rects, 0));
		assertArrayEquals(new int[]{NONE, NONE, NONE, NONE}, of(rects, 1));
	}

	@Test
	void itKeepsToTheMostTheShaderLooksAt() {
		float[] rects = new float[6 * 4];
		for (int i = 0; i < 6; i++) {
			rects[i * 4 + 2] = 10;
			rects[i * 4 + 3] = 10;
		}
		assertArrayEquals(new int[]{1, 2, 3, 4}, of(rects, 0));
	}

	@Test
	void panesThatOnlyTouchAreNotNeighbors() {
		float[] rects = {0, 0, 10, 10, 10, 0, 20, 10};
		assertArrayEquals(new int[]{NONE, NONE, NONE, NONE}, of(rects, 0));
	}
}
