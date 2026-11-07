package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The fancy swing's shape: wind-up, arc, follow-through. */
class FancySwingTest {
	@Test
	void startsAndEndsAtRest() {
		for (int value = 0; value < FancySwing.VALUES; value++) {
			assertEquals(0f, FancySwing.value(value, 0f), 0.0001f);
			assertEquals(0f, FancySwing.value(value, 1f), 0.0001f);
		}
	}

	@Test
	void drawsBackFirst() {
		assertTrue(FancySwing.value(FancySwing.TURN_SIDE, 0.2f) > 0f, "the item tips back");
		assertTrue(FancySwing.value(FancySwing.ACROSS, 0.2f) > 0f, "and moves away from where it swings to");
		assertTrue(FancySwing.value(FancySwing.UP, 0.2f) > 0f, "and up");
	}

	@Test
	void thenGoesThroughTheArcAndPastIt() {
		float arc = FancySwing.value(FancySwing.TURN_SIDE, 0.5f);
		float through = FancySwing.value(FancySwing.TURN_SIDE, 0.7f);
		assertTrue(arc < -45f, "well forward");
		assertTrue(through < arc, "and further still in the follow-through");
	}

	@Test
	void neverJerks() {
		float steps = 400f;
		for (int value = 0; value < FancySwing.VALUES; value++) {
			float biggest = 0f;
			for (int i = 1; i <= steps; i++) {
				biggest = Math.max(biggest, Math.abs(FancySwing.value(value, i / steps) - FancySwing.value(value, (i - 1) / steps)));
			}
			assertTrue(biggest < (value < 3 ? 0.01f : 2f), "value " + value + " jumps by " + biggest);
		}
	}

	@Test
	void staysInTheFirstRange() {
		assertEquals(0f, FancySwing.value(FancySwing.TURN_SIDE, -1f), 0.0001f);
		assertEquals(0f, FancySwing.value(FancySwing.TURN_SIDE, 2f), 0.0001f);
	}
}
