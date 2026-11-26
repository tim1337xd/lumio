package dev.tim1337.lumio.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sky quality Auto: how it steps down while measuring and later, and what it remembers. */
class SkyAutoQualityTest {
	private static final double BUDGET = SkyAutoQuality.BUDGET_MILLIS;

	@Test
	void aSkyWithinTheBudgetKeepsHigh() {
		SkyAutoQuality.Decision decision = SkyAutoQuality.decide(2, false, BUDGET * 0.5);
		assertEquals(2, decision.level());
		assertTrue(decision.settled());
	}

	@Test
	void measuringStepsDownOneLevelPerWindowUntilItFits() {
		SkyAutoQuality.Decision first = SkyAutoQuality.decide(2, false, BUDGET * 3);
		assertEquals(1, first.level());
		assertFalse(first.settled(), "Medium is measured next");
		SkyAutoQuality.Decision second = SkyAutoQuality.decide(1, false, BUDGET * 2);
		assertEquals(0, second.level());
		assertTrue(second.settled(), "Low is the last level");
		assertEquals(0, SkyAutoQuality.decide(0, true, BUDGET * 10).level(), "never below Low");
	}

	@Test
	void settledItLowersOnlyWhenWellOverTheBudget() {
		assertEquals(2, SkyAutoQuality.decide(2, true, BUDGET * 1.2).level(), "a little over is kept once settled");
		SkyAutoQuality.Decision lowered = SkyAutoQuality.decide(2, true, BUDGET * SkyAutoQuality.LATER_FACTOR * 1.1);
		assertEquals(1, lowered.level());
		assertTrue(lowered.settled());
	}

	@Test
	void theMemoryIsKeptOnlyForTheSameGraphics() {
		int mark = 0x9a3f01c2;
		String memory = SkyAutoQuality.memory(1, mark);
		assertEquals(1, SkyAutoQuality.savedLevel(memory, mark));
		assertEquals(-1, SkyAutoQuality.savedLevel(memory, mark + 1), "other graphics measure again");
		assertEquals(-1, SkyAutoQuality.savedLevel("", mark));
		assertEquals(-1, SkyAutoQuality.savedLevel("7:" + Integer.toHexString(mark), mark));
		assertEquals(-1, SkyAutoQuality.savedLevel("1:zz", mark));
	}
}
