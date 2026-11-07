package dev.tim1337.lumio.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rows that cut the menu's light to its window: one row ends exactly where the next begins, and nothing shows past the curve. */
class RoundedRowsTest {
	private static final float LEFT = 150f;
	private static final float TOP = 1f;
	private static final float RIGHT = 806f;
	private static final float BOTTOM = 497f;
	private static final float RADIUS = 14f;
	private static final float CLOSE = 0.01f;

	private static float[] window() {
		return RoundedRows.edges(LEFT, TOP, RIGHT, BOTTOM, 0f, 0f, RIGHT, BOTTOM, RADIUS);
	}

	@Test
	void theRowsCoverTheWholeHeightWithoutGapOrOverlap() {
		float[] edges = window();
		assertEquals(TOP, edges[0], CLOSE);
		assertEquals(BOTTOM, edges[edges.length - RoundedRows.STRIDE], CLOSE);
		for (int i = RoundedRows.STRIDE; i < edges.length; i += RoundedRows.STRIDE) {
			assertTrue(edges[i] > edges[i - RoundedRows.STRIDE], "the edges go down, each its own height");
		}
	}

	@Test
	void noRowGoesPastTheCurve() {
		float[] edges = window();
		for (int i = 0; i < edges.length; i += RoundedRows.STRIDE) {
			float y = edges[i];
			float right = edges[i + 2];
			float intoCorner = Math.max(0f, RADIUS - Math.min(y, BOTTOM - y));
			float cornerX = right - (RIGHT - RADIUS);
			if (cornerX > 0f) {
				assertTrue(cornerX * cornerX + intoCorner * intoCorner <= RADIUS * RADIUS + 0.05f, "past the curve at " + y);
			}
		}
	}

	@Test
	void theSidesAreStraightBetweenTheCorners() {
		float[] edges = window();
		for (int i = 0; i < edges.length; i += RoundedRows.STRIDE) {
			float y = edges[i];
			assertEquals(LEFT, edges[i + 1], CLOSE, "the left side stays where the sidebar ends");
			if (y >= RADIUS && y <= BOTTOM - RADIUS) {
				assertEquals(RIGHT, edges[i + 2], CLOSE);
			}
		}
	}

	@Test
	void aWindowWithoutASidebarGetsItsLeftCornersToo() {
		float[] edges = RoundedRows.edges(0f, 20f, RIGHT, BOTTOM, 0f, 0f, RIGHT, BOTTOM, RADIUS);
		// under the title bar nothing is cut at the top; the bottom-left corner is
		assertEquals(0f, edges[1], CLOSE);
		assertTrue(edges[edges.length - RoundedRows.STRIDE + 1] > 0f);
	}

	@Test
	void nothingLeftIsEmpty() {
		assertEquals(0, RoundedRows.edges(10f, 50f, 20f, 40f, 0f, 0f, 100f, 100f, RADIUS).length);
	}
}
