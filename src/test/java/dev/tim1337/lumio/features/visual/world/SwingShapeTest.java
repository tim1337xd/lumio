package dev.tim1337.lumio.features.visual.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How much of the game's movement an item of a size makes. */
class SwingShapeTest {
	private static final float CLOSE = 0.0001f;

	@Test
	void aSmallItemMovesLessWithALimit() {
		assertEquals(0.4f, SwingShape.movementShare(0.1f), CLOSE);
		assertEquals(0.4f, SwingShape.movementShare(0.15f), CLOSE);
		assertEquals(0.4f, SwingShape.movementShare(0.4f), CLOSE);
		assertEquals(0.5f, SwingShape.movementShare(0.5f), CLOSE);
		assertEquals(0.99f, SwingShape.movementShare(0.99f), CLOSE);
	}

	@Test
	void fromTheGamesSizeUpNothingChanges() {
		assertEquals(1f, SwingShape.movementShare(1f));
		assertEquals(1f, SwingShape.movementShare(1.5f));
		assertEquals(1f, SwingShape.movementShare(3f));
	}
}
