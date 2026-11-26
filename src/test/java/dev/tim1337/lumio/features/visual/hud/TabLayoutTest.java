package dev.tim1337.lumio.features.visual.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Players in columns of equal length, cards in columns of about the same, no larger than the room and no empty areas. */
class TabLayoutTest {
	private static final TabLayout.Sizes SIZES = new TabLayout.Sizes(10, 118, 15, 3, 13, 10, 107, 5, 0);
	private static final int WIDE = 1700;
	private static final int TALL = 950;
	private static final int[] NONE = {};
	private static final int[] FEW = {60, 40};
	private static final int[] MANY = {95, 75, 45, 45, 100, 60, 80, 45};

	private static TabLayout.Plan plan(int players, int[] cards) {
		return TabLayout.plan(players, cards, SIZES, WIDE, TALL, 0, 0);
	}

	@Test
	void theColumnSplit() {
		int[][] expected = {{5, 1, 5}, {12, 1, 12}, {13, 2, 7}, {20, 2, 10}, {22, 2, 11}, {30, 3, 10}, {40, 4, 10}, {80, 7, 12}};
		for (int[] row : expected) {
			TabLayout.Plan plan = plan(row[0], NONE);
			assertEquals(row[1], plan.columns(), row[0] + " players");
			assertEquals(row[2], plan.rows(), row[0] + " players");
			assertEquals(row[2], plan.visibleRows(), row[0] + " players need no scroll");
		}
	}

	@Test
	void columnsOfEqualLength() {
		for (int players = 1; players <= 80; players++) {
			TabLayout.Plan plan = plan(players, NONE);
			assertTrue(plan.rows() <= TabLayout.MOST_ROWS, players + " players");
			assertTrue(plan.columns() * plan.rows() - players < plan.columns(), players + " players: columns differ by at most one");
		}
	}

	@Test
	void noPlayersIsStillAPlan() {
		assertEquals(1, plan(0, NONE).columns());
	}

	@Test
	void cardsSitBesideThePlayersAndAreNoLongerThanThem() {
		for (int players : new int[]{20, 40, 80}) {
			TabLayout.Plan plan = plan(players, MANY);
			assertFalse(plan.cardsBelow(), players + " players");
			int playersHeight = SIZES.section() + plan.rows() * (SIZES.cellHeight() + SIZES.cellGap()) - SIZES.cellGap();
			assertTrue(plan.cardsHeight() <= playersHeight + 60, players + " players: cards end about where the players end");
		}
	}

	@Test
	void fewCardsTakeOneColumn() {
		assertEquals(1, plan(20, FEW).cardColumns());
	}

	@Test
	void theFewestCardColumnsThatFit() {
		TabLayout.Plan plan = plan(20, new int[]{95, 75, 45, 45, 100});
		assertEquals(3, plan.cardColumns(), "with three they end about where the players end: more would leave an empty area");
	}

	@Test
	void fiveAndFewCardsHaveAFullWindow() {
		TabLayout.Plan plan = plan(5, FEW);
		assertEquals(1, plan.columns());
		assertEquals(SIZES.section() + 60 + 5 + 40, plan.cardsHeight());
	}

	@Test
	void aCardsColumnComesFromItsSlotNotFromHeights() {
		// "shortest column first" put the fourth card under the second; by slot each card has its own column, whatever the heights
		TabLayout.Masonry masonry = TabLayout.masonry(new int[]{100, 40, 40, 40}, 2, 5);
		assertEquals(0, masonry.column()[0]);
		assertEquals(1, masonry.column()[1]);
		assertEquals(0, masonry.column()[2]);
		assertEquals(105, masonry.top()[2]);
		assertEquals(1, masonry.column()[3]);
		assertEquals(45, masonry.top()[3]);
		assertEquals(145, masonry.height());
	}

	@Test
	void cardsStayInTheirColumnsWhenCardsAppearDisappearGrowAndShrink() {
		// Area 0, Profile 1, Pet 2, Dungeons 3, Essence 4, Party 6: three columns
		int[] slots = {0, 1, 2, 3, 4, 6};
		int[] heights = {40, 60, 30, 50, 30, 80};
		TabLayout.Masonry all = TabLayout.slotted(heights, slots, 3, 5);
		assertEquals(0, all.column()[0]);
		assertEquals(1, all.column()[1]);
		assertEquals(2, all.column()[2]);
		assertEquals(0, all.column()[3]);
		assertEquals(1, all.column()[4]);
		assertEquals(0, all.column()[5]);
		// Dungeons is gone (another island): Party moves up in its column, never to another one
		TabLayout.Masonry without = TabLayout.slotted(new int[]{40, 60, 30, 30, 80}, new int[]{0, 1, 2, 4, 6}, 3, 5);
		assertEquals(0, without.column()[4]);
		assertEquals(45, without.top()[4]);
		assertEquals(1, without.column()[3]);
		// a card grows: only the cards below it in its column move
		int[] grown = heights.clone();
		grown[0] = 90;
		TabLayout.Masonry after = TabLayout.slotted(grown, slots, 3, 5);
		for (int i = 0; i < slots.length; i++) {
			assertEquals(all.column()[i], after.column()[i]);
		}
		assertEquals(all.top()[3] + 50, after.top()[3]);
		assertEquals(all.top()[5] + 50, after.top()[5]);
		assertEquals(all.top()[1], after.top()[1]);
		assertEquals(all.top()[4], after.top()[4]);
		assertEquals(all.top()[2], after.top()[2]);
		// it shrinks again: back where they were
		TabLayout.Masonry back = TabLayout.slotted(heights, slots, 3, 5);
		assertEquals(all.top()[5], back.top()[5]);
		// a card that appears later (RNG Meter, slot 5) takes its own place and moves no other card to another column
		TabLayout.Masonry more = TabLayout.slotted(new int[]{40, 60, 30, 50, 30, 80, 20}, new int[]{0, 1, 2, 3, 4, 6, 5}, 3, 5);
		assertEquals(2, more.column()[6]);
		for (int i = 0; i < slots.length; i++) {
			assertEquals(all.column()[i], more.column()[i]);
			assertEquals(all.top()[i], more.top()[i]);
		}
		// the order the cards are read in does not matter
		TabLayout.Masonry shuffled = TabLayout.slotted(new int[]{80, 30, 40}, new int[]{6, 4, 0}, 3, 5);
		assertEquals(0, shuffled.column()[0]);
		assertEquals(45, shuffled.top()[0]);
		assertEquals(0, shuffled.top()[2]);
	}

	@Test
	void theCardColumnsStayWhileTheScreenDoes() {
		int[] slots = TabLayout.inOrder(MANY.length);
		TabLayout.Plan first = TabLayout.plan(20, MANY, slots, SIZES, WIDE, TALL, 0, 0, 0);
		assertFalse(first.cardsBelow());
		// the cards got much taller: with the columns kept, none is added or taken away
		int[] taller = MANY.clone();
		for (int i = 0; i < taller.length; i++) {
			taller[i] *= 3;
		}
		TabLayout.Plan kept = TabLayout.plan(20, taller, slots, SIZES, WIDE, TALL, 0, 0, first.cardColumns());
		assertEquals(first.cardColumns(), kept.cardColumns());
	}

	@Test
	void cardsWithoutAPlaceGetOneAfterTheKnownOnesAndKeepIt() {
		TabCardSlots slots = new TabCardSlots();
		assertEquals(2, slots.slot("Pet", 2));
		int mystery = slots.slot("Mystery", -1);
		int other = slots.slot("Other", -1);
		assertEquals(TabCardSlots.UNKNOWN_FROM, mystery);
		assertEquals(TabCardSlots.UNKNOWN_FROM + 1, other);
		// seen again, in another order, after other cards came and went: the same places
		assertEquals(other, slots.slot("Other", -1));
		assertEquals(mystery, slots.slot("Mystery", -1));
		// another world counts anew
		slots.reset();
		assertEquals(TabCardSlots.UNKNOWN_FROM, slots.slot("Other", -1));
	}

	@Test
	void anEntryWithoutADisplayNameIsNoInfoLine() {
		// an NPC the server lists for a moment under a random name ("9jcuiw4ny4") has none; the info columns' lines all have one
		assertFalse(TabListHudFeature.infoLine(false));
		assertTrue(TabListHudFeature.infoLine(true));
	}

	@Test
	void cardsKeepTheirOrderInTheirColumn() {
		int[] cards = {30, 30, 30, 30, 30, 30};
		TabLayout.Masonry masonry = TabLayout.masonry(cards, 2, 5);
		for (int i = 2; i < cards.length; i++) {
			assertTrue(masonry.top()[i] >= masonry.top()[i - 2]);
		}
	}

	@Test
	void onANarrowScreenTheCardsGoBelow() {
		TabLayout.Plan plan = TabLayout.plan(10, MANY, SIZES, 240, TALL, 0, 0);
		assertTrue(plan.cardsBelow());
		assertTrue(plan.contentWidth() <= 240 - 2 * SIZES.pad());
	}

	@Test
	void pastTheRoomThePlayerRowsScroll() {
		TabLayout.Plan plan = TabLayout.plan(80, NONE, SIZES, 300, 300, 0, 0);
		assertEquals(2, plan.columns(), "as many as the width has");
		assertEquals(40, plan.rows());
		assertTrue(plan.visibleRows() < plan.rows());
		assertTrue(plan.bodyHeight() + 2 * SIZES.pad() <= 300);
	}

	@Test
	void onASmallScreenItIsDrawnSmallerBeforeItScrolls() {
		TabLayout.Sizes withChrome = new TabLayout.Sizes(10, 118, 15, 3, 13, 10, 107, 5, 90);
		TabLayout.Plan plan = TabLayout.planSmallest(20, MANY, withChrome, 432, 243);
		assertEquals(plan.rows(), plan.visibleRows(), "no scrolling while smaller is enough");
		assertEquals(2, plan.columns());
		assertEquals(10, plan.rows());
	}

	@Test
	void atTheSmallestSizeTheRowsScroll() {
		TabLayout.Plan plan = TabLayout.planSmallest(80, NONE, SIZES, 150, 150);
		assertTrue(plan.visibleRows() < plan.rows());
	}

	@Test
	void neverWiderThanTheRoom() {
		for (int width : new int[]{240, 400, 640, 864, 1700}) {
			for (int players : new int[]{5, 12, 20, 40, 80}) {
				for (int[] cards : new int[][]{NONE, FEW, MANY}) {
					TabLayout.Plan plan = TabLayout.plan(players, cards, SIZES, width, TALL, 0, 0);
					assertTrue(plan.contentWidth() + 2 * SIZES.pad() <= width, players + " players, " + cards.length + " cards, room " + width + ": " + plan);
				}
			}
		}
	}

	@Test
	void openItDoesNotJump() {
		TabLayout.Plan first = plan(20, MANY);
		TabLayout.Plan someoneJoined = TabLayout.plan(21, MANY, SIZES, WIDE, TALL, first.columns(), first.rows());
		assertEquals(first.columns(), someoneJoined.columns());
		assertEquals(first.rows() + 1, someoneJoined.rows());
		TabLayout.Plan someoneLeft = TabLayout.plan(15, MANY, SIZES, WIDE, TALL, first.columns(), first.rows());
		assertEquals(first.columns(), someoneLeft.columns());
		assertEquals(first.rows(), someoneLeft.rows(), "the empty place stays");
	}
}
