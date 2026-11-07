package dev.tim1337.lumio.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The field of a window moves and scales with it, covers it whole, and keeps the screen's shape. */
class ColoredFieldSpaceTest {
	private static final float SCREEN_W = 1920f;
	private static final float SCREEN_H = 1080f;
	private static final float PRECISE = 0.01f;

	private static float[] field(float x, float y, float w, float h) {
		return ColoredFieldSpace.ofWindow(x, y, w, h, SCREEN_W, SCREEN_H);
	}

	private static float[] panel(float x, float y, float w, float h, float seconds) {
		float[] out = new float[4];
		ColoredFieldSpace.ofPanel(out, x, y, w, h, SCREEN_W, SCREEN_H, seconds);
		return out;
	}

	@Test
	void aPanelsFieldMovesWithThePanel() {
		float[] here = panel(100f, 200f, 220f, 120f, 3f);
		float[] there = panel(900f, 700f, 220f, 120f, 3f);
		assertEquals(here[0] + 800f, there[0], PRECISE);
		assertEquals(here[1] + 500f, there[1], PRECISE);
		assertEquals(here[2], there[2], PRECISE);
		assertEquals(here[3], there[3], PRECISE);
	}

	@Test
	void aPanelsFieldIsItsOwnAndLargerThanIt() {
		float[] field = panel(100f, 200f, 220f, 120f, 0f);
		assertTrue(field[2] >= 220f * ColoredFieldSpace.PANEL_ZOOM - PRECISE, "wider than the panel, so that colors can pass: " + field[2]);
		assertTrue(field[3] >= 120f, "as tall: " + field[3]);
		assertTrue(field[2] < SCREEN_W / 2f, "and no picture of the whole screen: " + field[2]);
		assertEquals(SCREEN_W / SCREEN_H, field[2] / field[3], PRECISE);
	}

	@Test
	void panelsOfDifferentWidthsShowDifferentPartsOfTheirRound() {
		float[] one = panel(100f, 200f, 220f, 120f, 5f);
		float[] other = panel(100f, 200f, 320f, 120f, 5f);
		float offsetOne = (one[0] - (100f + (220f - one[2]) / 2f)) / one[3];
		float offsetOther = (other[0] - (100f + (320f - other[2]) / 2f)) / other[3];
		assertTrue(Math.abs(offsetOne - offsetOther) > 0.01f, offsetOne + " and " + offsetOther);
	}

	@Test
	void aPanelsFieldMovesEveryFrameInSmallSteps() {
		float mostStep = 0f;
		float[] before = panel(100f, 200f, 220f, 120f, 0f);
		for (int frame = 1; frame < 60 * 60; frame++) {
			float[] now = panel(100f, 200f, 220f, 120f, frame / 60f);
			assertTrue(now[0] != before[0] || now[1] != before[1], "frame " + frame);
			mostStep = Math.max(mostStep, Math.max(Math.abs(now[0] - before[0]), Math.abs(now[1] - before[1])));
			before = now;
		}
		assertTrue(mostStep < 3f, "at 60 frames per second it moves less than 3 pixels a frame, but it was " + mostStep);
	}

	@Test
	void aPanelsFieldStaysNearThePanel() {
		for (int second = 0; second < 600; second++) {
			float[] field = panel(100f, 200f, 220f, 120f, second);
			assertTrue(Math.abs(field[0] + field[2] / 2f - 210f) < field[3], "second " + second);
			assertTrue(Math.abs(field[1] + field[3] / 2f - 260f) < field[3], "second " + second);
		}
	}

	@Test
	void aPanelsFieldDoesNotJumpWhenItsLongTimeWrapsAround() {
		float round = ColoredFieldSpace.PANEL_ROUND_SECONDS * 1000f;
		float[] before = panel(100f, 200f, 220f, 120f, round - 0.01f);
		float[] after = panel(100f, 200f, 220f, 120f, round + 0.01f);
		assertTrue(Math.abs(before[0] - after[0]) < 40f, before[0] + " to " + after[0]);
		assertTrue(Math.abs(before[1] - after[1]) < 40f, before[1] + " to " + after[1]);
	}

	@Test
	void movesWithTheWindow() {
		float[] left = field(100f, 200f, 900f, 700f);
		float[] right = field(600f, 90f, 900f, 700f);
		assertEquals(500f, right[0] - left[0], PRECISE);
		assertEquals(-110f, right[1] - left[1], PRECISE);
		assertEquals(left[2], right[2], PRECISE);
		assertEquals(left[3], right[3], PRECISE);
	}

	@Test
	void scalesWithTheWindow() {
		float[] normal = field(0f, 0f, 900f, 700f);
		float[] larger = field(0f, 0f, 1080f, 840f);
		assertEquals(1.2f, larger[2] / normal[2], PRECISE);
		assertEquals(1.2f, larger[3] / normal[3], PRECISE);
	}

	@Test
	void aPlaceInTheWindowKeepsItsColorWhenTheWindowMoves() {
		float[] one = field(100f, 200f, 900f, 700f);
		float[] two = field(733f, 41f, 900f, 700f);
		float atOne = (100f + 300f - one[0]) / one[2];
		float atTwo = (733f + 300f - two[0]) / two[2];
		assertEquals(atOne, atTwo, 0.0001f);
	}

	@Test
	void coversTheWindowWhole() {
		for (float[] window : new float[][]{{0f, 0f, 900f, 700f}, {0f, 0f, 1800f, 300f}, {0f, 0f, 300f, 1000f}}) {
			float[] f = field(window[0], window[1], window[2], window[3]);
			assertTrue(f[0] <= window[0] + PRECISE && f[1] <= window[1] + PRECISE);
			assertTrue(f[0] + f[2] >= window[0] + window[2] - PRECISE && f[1] + f[3] >= window[1] + window[3] - PRECISE);
		}
	}

	@Test
	void keepsTheScreensShape() {
		float[] f = field(40f, 40f, 700f, 650f);
		assertEquals(SCREEN_W / SCREEN_H, f[2] / f[3], PRECISE);
	}

	@Test
	void aWindowAsLargeAsTheScreenIsTheScreen() {
		float[] f = field(0f, 0f, SCREEN_W, SCREEN_H);
		assertEquals(0f, f[0], PRECISE);
		assertEquals(0f, f[1], PRECISE);
		assertEquals(SCREEN_W, f[2], PRECISE);
		assertEquals(SCREEN_H, f[3], PRECISE);
	}
}
