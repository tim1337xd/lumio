package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The largest open area of the view, found on the grid of rays: the biggest rectangle of open cells, the one near the middle first. */
class WorldLyricOpenSkyTest {
	private static final int COLUMNS = 8;
	private static final int ROWS = 6;

	/** @return a grid from rows of '#' (blocked) and '.' (open), top row first */
	private static boolean[] grid(String... rows) {
		boolean[] open = new boolean[COLUMNS * ROWS];
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLUMNS; c++) {
				open[r * COLUMNS + c] = rows[r].charAt(c) == '.';
			}
		}
		return open;
	}

	@Test
	void nothingOpenIsNoArea() {
		int[] out = new int[4];
		assertFalse(WorldLyricOpenSky.largest(grid("########", "########", "########", "########", "########", "########"), COLUMNS, ROWS, out));
	}

	@Test
	void theLargestRectangleIsFound() {
		int[] out = new int[4];
		// an island in the middle, open sky and void all around it, the open part below the horizon too
		assertTrue(WorldLyricOpenSky.largest(grid(
			"..##....",
			"..##....",
			"..##....",
			"..##....",
			"########",
			"########"), COLUMNS, ROWS, out));
		assertArrayEquals(new int[] {4, 0, 7, 3}, out);
	}

	@Test
	void nearTheMiddleWinsAnEqualArea() {
		int[] out = new int[4];
		// two areas of eight cells: the one at the corner and the one around the middle
		assertTrue(WorldLyricOpenSky.largest(grid(
			"######..",
			"######..",
			"##....##",
			"##....##",
			"########",
			"########"), COLUMNS, ROWS, out));
		assertArrayEquals(new int[] {2, 2, 5, 3}, out);
	}

	@Test
	void cellsMapToTheScreen() {
		for (int c = 0; c < WorldLyricOpenSky.COLUMNS; c++) {
			assertTrue(WorldLyricOpenSky.column(WorldLyricOpenSky.screenX(c)) == c);
		}
		for (int r = 0; r < WorldLyricOpenSky.ROWS; r++) {
			assertTrue(WorldLyricOpenSky.row(WorldLyricOpenSky.screenY(r)) == r);
		}
	}

	@Test
	void theSkyNeverCountsAsBusyOnlySurfacesDo() {
		int cells = WorldLyricOpenSky.COLUMNS * WorldLyricOpenSky.ROWS;
		boolean[] open = new boolean[cells];
		float[] light = new float[cells];
		java.util.Arrays.fill(open, true);
		java.util.Arrays.fill(light, 0.02f);
		float[] out = new float[4];
		// the whole screen: all sky, one calm tone however a sky style draws on it
		assertTrue(WorldLyricOpenSky.backdrop(open, light, -1.0, 1.0, 1.0, -1.0, out));
		assertEquals(0.02f, out[0], 1e-6f);
		assertEquals(0f, out[1], "the sky is never busy");
		// a checkerboard of bright and dark surfaces is busy
		for (int i = 0; i < cells; i++) {
			open[i] = false;
			light[i] = (i / WorldLyricOpenSky.COLUMNS + i) % 2 == 0 ? 0.1f : 0.7f;
		}
		WorldLyricOpenSky.backdrop(open, light, -1.0, 1.0, 1.0, -1.0, out);
		assertTrue(out[1] > 0.25f, "busy surfaces");
		assertEquals(0.1f, out[2], 1e-6f, "the darkest cell");
		assertEquals(0.7f, out[3], 1e-6f, "the brightest cell");
		// a small box takes in every cell it touches, even partly: a box inside one cell still has that cell
		assertTrue(WorldLyricOpenSky.backdrop(open, light, 0.01, 0.02, 0.02, 0.01, out));
	}

	@Test
	void surfacesAndTheSkyAreAsBrightAsTheyLook() {
		// grass by day, its top face: lighter than leaves seen from the side, both far darker at night
		float grass = WorldLyricOpenSky.surfaceLight(0x7FB238, 1f, 1f);
		float leaves = WorldLyricOpenSky.surfaceLight(0x007C00, 0.8f, 1f);
		assertTrue(grass > leaves, "grass " + grass + " and leaves " + leaves);
		assertTrue(leaves < 0.1f, "dark leaves: " + leaves);
		assertTrue(WorldLyricOpenSky.surfaceLight(0x7FB238, 1f, 0.27f) < 0.03f, "grass by night");
		// the sky has the fog's color at the horizon and its own color high up
		int sky = 0x78A7FF;
		int fog = 0xC0D8FF;
		assertEquals(WorldLyricOpenSky.lightness(fog), WorldLyricOpenSky.skyLight(sky, fog, 0.0), 1e-3f);
		assertEquals(WorldLyricOpenSky.lightness(sky), WorldLyricOpenSky.skyLight(sky, fog, 1.0), 1e-3f);
		assertTrue(WorldLyricOpenSky.skyLight(sky, fog, 0.3) > WorldLyricOpenSky.lightness(sky), "between the two a little above the horizon");
	}
}
