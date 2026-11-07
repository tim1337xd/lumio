package dev.tim1337.lumio.ui.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A row of swatches fits beside its name, on its own line, or wraps, and never passes the edge or gets smaller. */
class SwatchRowTest {
	private static final int COUNT = 9;
	private static final int CELL = 24;
	private static final int LINE = 24;

	@Test
	void fitsOnOneLineWhenThereIsRoom() {
		assertEquals(216, SwatchRow.fullWidth(COUNT, CELL));
		assertEquals(1, SwatchRow.lines(COUNT, 216, CELL));
		assertEquals(1, SwatchRow.lines(COUNT, 400, CELL));
	}

	@Test
	void wrapsWhenALineIsTooNarrow() {
		assertEquals(2, SwatchRow.lines(COUNT, 215, CELL));
		assertEquals(2, SwatchRow.lines(COUNT, 120, CELL));
		assertEquals(3, SwatchRow.lines(COUNT, 72, CELL));
	}

	@Test
	void everyCellStaysInsideItsWidthAtItsSize() {
		for (int width = CELL; width < 300; width++) {
			for (int i = 0; i < COUNT; i++) {
				assertTrue(SwatchRow.cellX(i, width, CELL) + CELL <= width, "width " + width + " cell " + i);
				assertTrue(SwatchRow.line(i, width, CELL) < SwatchRow.lines(COUNT, width, CELL));
			}
		}
	}

	@Test
	void aRowTooNarrowForOneCellStillHasOneOnALine() {
		assertEquals(1, SwatchRow.perLine(10, CELL));
		assertEquals(COUNT, SwatchRow.lines(COUNT, 10, CELL));
	}

	@Test
	void aPlaceFindsTheCellThatIsThere() {
		int width = 120;
		for (int i = 0; i < COUNT; i++) {
			int x = SwatchRow.cellX(i, width, CELL) + CELL / 2;
			int y = SwatchRow.line(i, width, CELL) * LINE + LINE / 2;
			assertEquals(i, SwatchRow.cellAt(x, y, COUNT, width, CELL, LINE));
		}
	}

	@Test
	void aPlaceBesideTheCellsFindsNone() {
		assertEquals(-1, SwatchRow.cellAt(-1, 5, COUNT, 120, CELL, LINE));
		assertEquals(-1, SwatchRow.cellAt(5, -1, COUNT, 120, CELL, LINE));
		assertEquals(-1, SwatchRow.cellAt(119, LINE + 5, COUNT, 120, CELL, LINE));
		assertEquals(-1, SwatchRow.cellAt(5, 5 * LINE, COUNT, 120, CELL, LINE));
	}
}
