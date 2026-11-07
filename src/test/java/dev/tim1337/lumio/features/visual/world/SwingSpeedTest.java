package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwingSpeedTest {
	private static final float EXACT = 0.01f;

	@Test
	void theGamesLengthWithEffects() {
		assertEquals(6f, SwingSpeed.withEffects(6, false, 0, false, 0), EXACT);
		assertEquals(4f, SwingSpeed.withEffects(6, true, 1, false, 0), EXACT, "Haste II");
		assertEquals(5f, SwingSpeed.withEffects(6, true, 0, false, 0), EXACT, "Haste I");
		assertEquals(8f, SwingSpeed.withEffects(6, false, 0, true, 0), EXACT, "Mining Fatigue I");
		assertEquals(12f, SwingSpeed.withEffects(6, false, 0, true, 2), EXACT, "Mining Fatigue III");
		assertEquals(4f, SwingSpeed.withEffects(6, true, 1, true, 2), EXACT, "Haste goes first, as in the game");
	}

	@Test
	void speedIsAnExponent() {
		assertEquals(6f, SwingSpeed.ticks(6f, 0f), EXACT);
		assertEquals(2.21f, SwingSpeed.ticks(6f, 1f), EXACT);
		assertEquals(44.33f, SwingSpeed.ticks(6f, -2f), EXACT);
	}

	@Test
	void neverBelowOneTick() {
		assertEquals(1f, SwingSpeed.ticks(1f, 1f), EXACT);
		assertEquals(1f, SwingSpeed.ticks(0f, 0f), EXACT, "a high Haste brings the game's length to nothing");
		assertEquals(1f, SwingSpeed.ticks(-3f, 0f), EXACT);
	}

	@Test
	void theDefaultIsTheGames() {
		assertTrue(SwingSpeed.isGame(SwingSpeed.GAME));
		assertFalse(SwingSpeed.isGame(0.01f));
		assertEquals(1f, SwingSpeed.factor(SwingSpeed.GAME), EXACT);
	}

	@Test
	void anOldFactorKeepsItsPace() {
		assertEquals(0f, SwingSpeed.fromOldFactor(1f), EXACT);
		assertEquals(6f / 0.57f, SwingSpeed.ticks(6f, SwingSpeed.fromOldFactor(0.57f)), EXACT);
		assertEquals(6f / 2f, SwingSpeed.ticks(6f, SwingSpeed.fromOldFactor(2f)), EXACT);
		assertEquals(SwingSpeed.SLOWEST, SwingSpeed.fromOldFactor(0.05f), EXACT, "the new range is smaller");
		assertEquals(SwingSpeed.FASTEST, SwingSpeed.fromOldFactor(3f), EXACT);
		assertEquals(0f, SwingSpeed.fromOldFactor(0f), EXACT, "nonsense in a config");
		assertEquals(0f, SwingSpeed.fromOldFactor(Float.NaN), EXACT);
	}
}
