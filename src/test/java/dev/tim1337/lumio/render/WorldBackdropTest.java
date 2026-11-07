package dev.tim1337.lumio.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldBackdropTest {
	@Test
	void theCornerIsRound() {
		int radius = 10;
		assertTrue(WorldBackdrop.cornerInset(radius, 0) >= 5, "the first row starts well inside");
		assertTrue(WorldBackdrop.cornerInset(radius, radius - 1) <= 1, "the last one nearly at the edge");
		for (int row = 1; row < radius; row++) {
			assertTrue(WorldBackdrop.cornerInset(radius, row) <= WorldBackdrop.cornerInset(radius, row - 1), "row " + row);
		}
	}

	@Test
	void noRadiusNoInset() {
		assertEquals(0, WorldBackdrop.cornerInset(0, 0));
	}
}
