package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemHeightTest {
	private static final int SLOT = 0;
	private static final int OTHER_SLOT = 1;
	private static final float EXACT = 0.0001f;

	@Test
	void aRewrittenItemTakesThePlaceWithoutADrop() {
		ItemHeight height = new ItemHeight();
		height.changedInPlace(SLOT, true, true);
		assertTrue(height.changedInPlace(SLOT, false, true));
	}

	@Test
	void anotherKindOfItemKeepsItsDrop() {
		ItemHeight height = new ItemHeight();
		height.changedInPlace(SLOT, true, true);
		assertFalse(height.changedInPlace(SLOT, false, false));
	}

	@Test
	void theSameKindInAnotherSlotKeepsItsDrop() {
		ItemHeight height = new ItemHeight();
		height.changedInPlace(SLOT, true, true);
		assertFalse(height.changedInPlace(OTHER_SLOT, false, true));
	}

	@Test
	void afterAHitTheItemStaysAtRest() {
		ItemHeight height = new ItemHeight();
		assertEquals(ItemHeight.REST, height.after(true, 0.1f, false, false), EXACT);
	}

	@Test
	void withTheDipTheGamesMotionRuns() {
		ItemHeight height = new ItemHeight();
		assertEquals(ItemHeight.GAME, height.after(true, 0.1f, false, true), EXACT);
	}

	@Test
	void aChangeOfItemDropsAndRisesAsInTheGame() {
		ItemHeight height = new ItemHeight();
		assertEquals(ItemHeight.GAME, height.after(false, 0.1f, false, false), EXACT, "the old one goes down");
		assertEquals(ItemHeight.GAME, height.after(true, 0.2f, false, false), EXACT, "the new one rises");
		assertEquals(ItemHeight.GAME, height.after(true, 0.9f, false, false), EXACT);
		assertEquals(ItemHeight.REST, height.after(true, 1f, false, false), EXACT, "and stands");
		assertEquals(ItemHeight.REST, height.after(true, 0.1f, false, false), EXACT, "the next hit is no change");
	}

	@Test
	void busyHandsStayTheGames() {
		ItemHeight height = new ItemHeight();
		assertEquals(ItemHeight.GAME, height.after(true, 1f, true, false), EXACT);
	}

	@Test
	void whileItChangesARewriteIsNoShortcut() {
		ItemHeight height = new ItemHeight();
		height.changedInPlace(SLOT, true, true);
		height.after(false, 0.1f, false, false);
		assertFalse(height.changedInPlace(SLOT, false, true));
	}
}
