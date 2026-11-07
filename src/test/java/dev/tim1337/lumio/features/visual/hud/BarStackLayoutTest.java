package dev.tim1337.lumio.features.visual.hud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stack of bars: saved text, rows of up to three bars, and where a dragged bar lands. */
class BarStackLayoutTest {
	private static final Set<String> IDS = Set.of("health", "mana", "food", "air", "vitality", "fuel", "xp");

	@Test
	void parseAndFormat() {
		List<List<String>> rows = BarStackLayout.parse("food,air;health,mana;xp", IDS);
		assertEquals(List.of(List.of("food", "air"), List.of("health", "mana"), List.of("xp")), rows);
		assertEquals("food,air;health,mana;xp", BarStackLayout.format(rows));
	}

	@Test
	void brokenTextIsCleanedUp() {
		List<List<String>> rows = BarStackLayout.parse(" health , health ;; nope ; mana,food,air,xp ", IDS);
		assertEquals(List.of(List.of("health"), List.of("mana", "food", "air")), rows);
		assertTrue(BarStackLayout.parse(null, IDS).isEmpty());
	}

	@Test
	void removingTheLastBarRemovesTheRow() {
		List<List<String>> rows = BarStackLayout.parse("health;mana;xp", IDS);
		BarStackLayout.remove(rows, "mana");
		assertEquals("health;xp", BarStackLayout.format(rows));
	}

	@Test
	void sharingARowAtEitherEnd() {
		List<List<String>> rows = BarStackLayout.parse("health;mana;xp", IDS);
		assertTrue(BarStackLayout.insertInRow(rows, 0, false, "mana"));
		assertEquals("health,mana;xp", BarStackLayout.format(rows));
		assertTrue(BarStackLayout.insertInRow(rows, 0, true, "food"));
		assertEquals("food,health,mana;xp", BarStackLayout.format(rows));
		// a full row takes no fourth bar
		assertFalse(BarStackLayout.insertInRow(rows, 0, true, "air"));
	}

	@Test
	void dropBetweenRowsAndOnEdges() {
		// two rows: 0..10 and 13..23, 200 wide from x 0; the edges reach 40
		float[] tops = {0f, 13f};
		float[] bottoms = {10f, 23f};
		boolean[] shares = {true, true};
		assertEquals(new BarStackLayout.Drop(0, false, false), BarStackLayout.dropAt(tops, bottoms, 2, 0f, 200f, shares, 100f, -6f, 40f));
		assertEquals(new BarStackLayout.Drop(1, false, false), BarStackLayout.dropAt(tops, bottoms, 2, 0f, 200f, shares, 100f, 11.5f, 40f));
		assertEquals(new BarStackLayout.Drop(2, false, false), BarStackLayout.dropAt(tops, bottoms, 2, 0f, 200f, shares, 100f, 30f, 40f));
		assertEquals(new BarStackLayout.Drop(1, true, true), BarStackLayout.dropAt(tops, bottoms, 2, 0f, 200f, shares, 10f, 18f, 40f));
		assertEquals(new BarStackLayout.Drop(1, true, false), BarStackLayout.dropAt(tops, bottoms, 2, 0f, 200f, shares, 190f, 18f, 40f));
		// a row that takes no other bar: between rows instead
		assertFalse(BarStackLayout.dropAt(tops, bottoms, 2, 0f, 200f, new boolean[]{true, false}, 190f, 18f, 40f).share());
	}

	@Test
	void applyMovesTheBar() {
		List<List<String>> rows = BarStackLayout.parse("food,air;health,mana;xp", IDS);
		// mana dragged to the top: the rows without it are food,air / health / xp, and it goes in before the first
		BarStackLayout.apply(rows, new BarStackLayout.Drop(0, false, false), "mana");
		assertEquals("mana;food,air;health;xp", BarStackLayout.format(rows));
		BarStackLayout.apply(rows, new BarStackLayout.Drop(1, true, false), "mana");
		assertEquals("food,air;health,mana;xp", BarStackLayout.format(rows));
	}

	@Test
	void sidesAreCleanedUpAndHoldThreeBars() {
		List<String> left = BarStackLayout.parseSide(" health , health, nope, mana,food,air ", IDS, List.of());
		assertEquals(List.of("health", "mana", "food"), left);
		assertEquals(List.of("xp"), BarStackLayout.parseSide("health,xp", IDS, left));
		assertTrue(BarStackLayout.parseSide(null, IDS, List.of()).isEmpty());
		assertTrue(BarStackLayout.parseSide("", IDS, List.of()).isEmpty());
		assertEquals("health,mana,food", BarStackLayout.formatSide(left));
	}

	@Test
	void aBarOnASideLeavesTheStack() {
		List<List<String>> rows = BarStackLayout.parse("fuel,vitality;food,air;health,mana;xp", IDS);
		BarStackLayout.withSides(rows, List.of("health", "food"), List.of("mana", "xp"), true, true, "xp");
		assertEquals("fuel,vitality;air", BarStackLayout.format(rows));
	}

	@Test
	void aSideWithoutRoomGoesOnTopOfTheStack() {
		List<List<String>> rows = BarStackLayout.parse("food,air;health,mana;xp", IDS);
		BarStackLayout.withSides(rows, List.of("health"), List.of("mana", "xp"), true, false, "xp");
		// experience keeps a row of its own
		assertEquals("mana;xp;food,air", BarStackLayout.format(rows));
		rows = BarStackLayout.parse("food,air", IDS);
		BarStackLayout.withSides(rows, List.of("health"), List.of("mana"), false, false, "xp");
		assertEquals("health;mana;food,air", BarStackLayout.format(rows));
	}

	@Test
	void theNearestSpotTakesTheBar() {
		float[] left = {10f, 100f, 80f, 20f};
		float[] right = {300f, 100f, 80f, 20f};
		float[] above = {100f, 60f, 190f, 40f};
		float reach = 30f;
		assertEquals(BarStackLayout.LEFT, BarStackLayout.nearestSide(50f, 110f, left, right, above, reach));
		assertEquals(BarStackLayout.RIGHT, BarStackLayout.nearestSide(340f, 90f, left, right, above, reach));
		// too far from both, and nearer the stack than a side
		assertEquals(BarStackLayout.NO_SIDE, BarStackLayout.nearestSide(50f, 10f, left, right, above, reach));
		assertEquals(BarStackLayout.NO_SIDE, BarStackLayout.nearestSide(200f, 80f, left, right, above, reach));
		assertEquals(BarStackLayout.NO_SIDE, BarStackLayout.nearestSide(110f, 95f, left, right, above, reach));
		// a full side takes nothing
		assertEquals(BarStackLayout.NO_SIDE, BarStackLayout.nearestSide(50f, 110f, null, right, above, reach));
	}

	@Test
	void aBarLandsBetweenTheBarsOfASide() {
		float[] middles = {100f, 120f, 0f};
		assertEquals(0, BarStackLayout.sideIndexAt(middles, 2, 90f));
		assertEquals(1, BarStackLayout.sideIndexAt(middles, 2, 110f));
		assertEquals(2, BarStackLayout.sideIndexAt(middles, 2, 130f));
		assertEquals(0, BarStackLayout.sideIndexAt(middles, 0, 130f));
	}

	@Test
	void aSpotStartsRightBesideTheHotbar() {
		// a hotbar of 182 at 100, with 8 of room at size 1
		assertEquals(290f, BarStackLayout.sideX(BarStackLayout.RIGHT, 100f, 182f, 0f, 8f, 84f), 0.001f);
		assertEquals(8f, BarStackLayout.sideX(BarStackLayout.LEFT, 100f, 182f, 0f, 8f, 84f), 0.001f);
	}

	@Test
	void theSpotsMoveAndGrowWithTheHotbar() {
		float scale = 1.5f;
		float right = BarStackLayout.sideX(BarStackLayout.RIGHT, 300f, 182f * scale, 0f, 8f * scale, 84f * scale);
		assertEquals(300f + 273f + 12f, right, 0.001f);
		float left = BarStackLayout.sideX(BarStackLayout.LEFT, 300f, 182f * scale, 0f, 8f * scale, 84f * scale);
		assertEquals(300f - 12f - 126f, left, 0.001f);
	}

	@Test
	void theOffhandKeepsItsPlaceBetweenHotbarAndBars() {
		assertEquals(100f + 182f + 20f + 8f, BarStackLayout.sideX(BarStackLayout.RIGHT, 100f, 182f, 20f, 8f, 84f), 0.001f);
	}

	@Test
	void barsAreCenteredOnTheHotbarAndStayOnTheScreen() {
		assertEquals(190f, BarStackLayout.sideTop(200f, 20f, 480f, 2f), 0.001f);
		assertEquals(448f, BarStackLayout.sideTop(470f, 30f, 480f, 2f), 0.001f);
	}
}
