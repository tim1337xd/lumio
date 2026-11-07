package dev.tim1337.lumio.features.visual.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The item's drop after a hit into the air runs as fast as the swing, and only the look changes. */
class SwingDipTest {
	private static final float DELAY = 20f;
	private static final float EXACT = 0.001f;

	/** @return the game's timer some ticks after a hit */
	private static float real(int ticks) {
		return Math.min(1f, (ticks + 1) / DELAY);
	}

	@Test
	void theGamesSpeedLeavesTheGamesTimer() {
		SwingDip dip = new SwingDip();
		assertEquals(-1f, dip.step(0.05f, true, 1f, DELAY));
		assertEquals(-1f, dip.step(0.1f, true, 1f, DELAY));
	}

	@Test
	void atHalfSpeedItTakesTwiceAsLong() {
		SwingDip dip = new SwingDip();
		dip.step(1f, false, 0.5f, DELAY);
		float first = dip.step(real(0), true, 0.5f, DELAY);
		assertEquals(real(0), first, EXACT);
		float last = first;
		for (int tick = 1; tick <= 20; tick++) {
			last = dip.step(real(tick), true, 0.5f, DELAY);
			assertEquals(Math.min(1f, real(0) + tick * 0.5f / DELAY), last, EXACT, "tick " + tick);
		}
		assertTrue(last < 1f, "the game's timer ran out long ago, the look's has not");
	}

	@Test
	void atASlowSwingTheItemDoesNotRiseEarly() {
		SwingDip dip = new SwingDip();
		dip.step(1f, false, 0.25f, DELAY);
		dip.step(real(0), true, 0.25f, DELAY);
		float timer = 0f;
		for (int tick = 1; tick <= 12; tick++) {
			timer = dip.step(real(tick), true, 0.25f, DELAY);
		}
		assertTrue(timer < real(12) / 2f, "the look's timer " + timer + " against the game's " + real(12));
	}

	@Test
	void atAFastSwingItEndsWithoutDroppingAgain() {
		SwingDip dip = new SwingDip();
		dip.step(1f, false, 3f, DELAY);
		dip.step(real(0), true, 3f, DELAY);
		for (int tick = 1; tick < DELAY; tick++) {
			float timer = dip.step(real(tick), true, 3f, DELAY);
			if (tick >= 7) {
				assertEquals(1f, timer, EXACT, "stays up until the game's timer is done too, at tick " + tick);
			}
		}
	}

	@Test
	void afterItIsOverTheGamesTimerAppliesAgain() {
		SwingDip dip = new SwingDip();
		dip.step(1f, false, 2f, DELAY);
		dip.step(real(0), true, 2f, DELAY);
		for (int tick = 1; tick <= DELAY; tick++) {
			dip.step(real(tick), true, 2f, DELAY);
		}
		assertEquals(-1f, dip.step(1f, false, 2f, DELAY));
	}

	@Test
	void switchingItemsStaysTheGames() {
		SwingDip dip = new SwingDip();
		dip.step(1f, false, 0.25f, DELAY);
		assertEquals(-1f, dip.step(real(0), false, 0.25f, DELAY), "a reset without a swing");
		assertEquals(-1f, dip.step(real(1), false, 0.25f, DELAY));
	}

	@Test
	void anotherHitStartsItAgain() {
		SwingDip dip = new SwingDip();
		dip.step(1f, false, 0.5f, DELAY);
		dip.step(real(0), true, 0.5f, DELAY);
		dip.step(real(1), true, 0.5f, DELAY);
		assertEquals(real(0), dip.step(real(0), true, 0.5f, DELAY), EXACT);
	}

	@Test
	void theItemMovesAsInTheGameOnlySlower() {
		assertEquals(0.216f, SwingDip.height(0.2f, 0.6f, 1f), EXACT);
		assertEquals(0.6f, SwingDip.height(1f, 0f, 1f), EXACT, "the game's drop in one tick");
		assertEquals(0.9f, SwingDip.height(1f, 0f, 0.25f), EXACT, "a quarter of it");
	}
}
