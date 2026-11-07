package dev.tim1337.lumio.features.visual.screen.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tabs that do not fit go into More, the open one always stays in the row, and nothing leaves the room. */
class ChatTabFitTest {
	private static final int[] TABS = {20, 30, 30, 34, 40};
	private static final int MORE = 30;
	private static final int GAP = 2;

	private static int rowWidth(ChatTabFit.Fit fit) {
		int width = fit.more().length > 0 ? MORE : -GAP;
		for (int tab : fit.row()) {
			width += TABS[tab] + GAP;
		}
		return width;
	}

	@Test
	void allFit() {
		ChatTabFit.Fit fit = ChatTabFit.of(TABS, 0, ChatTabFit.needed(TABS, GAP), MORE, GAP);
		assertArrayEquals(new int[]{0, 1, 2, 3, 4}, fit.row());
		assertEquals(0, fit.more().length);
	}

	@Test
	void whatDoesNotFitGoesIntoMoreInItsOrder() {
		ChatTabFit.Fit fit = ChatTabFit.of(TABS, 0, 120, MORE, GAP);
		assertArrayEquals(new int[]{0, 1, 2}, fit.row());
		assertArrayEquals(new int[]{3, 4}, fit.more());
	}

	@Test
	void theOpenTabSwapsIn() {
		ChatTabFit.Fit fit = ChatTabFit.of(TABS, 4, 120, MORE, GAP);
		assertArrayEquals(new int[]{0, 4}, fit.row());
		assertArrayEquals(new int[]{1, 2, 3}, fit.more());
	}

	@Test
	void anOpenTabInTheRowChangesNothing() {
		ChatTabFit.Fit fit = ChatTabFit.of(TABS, 1, 120, MORE, GAP);
		assertArrayEquals(new int[]{0, 1, 2}, fit.row());
	}

	@Test
	void aWideOpenTabPushesOutAsManyAsItNeeds() {
		int[] wide = {20, 20, 20, 90};
		ChatTabFit.Fit fit = ChatTabFit.of(wide, 3, 130, MORE, GAP);
		assertArrayEquals(new int[]{3}, fit.row());
		assertArrayEquals(new int[]{0, 1, 2}, fit.more());
	}

	@Test
	void theOpenTabStaysEvenInATinyRow() {
		ChatTabFit.Fit fit = ChatTabFit.of(TABS, 3, 10, MORE, GAP);
		assertArrayEquals(new int[]{3}, fit.row());
		assertEquals(TABS.length - 1, fit.more().length);
	}

	@Test
	void anUnknownOpenTabCountsAsTheFirst() {
		ChatTabFit.Fit fit = ChatTabFit.of(TABS, -1, 80, MORE, GAP);
		assertEquals(0, fit.row()[0]);
	}

	@Test
	void everyTabIsSomewhereOnceAndNothingLeavesTheRoom() {
		for (int room = 72; room < 180; room++) {
			for (int open = 0; open < TABS.length; open++) {
				ChatTabFit.Fit fit = ChatTabFit.of(TABS, open, room, MORE, GAP);
				assertEquals(TABS.length, fit.row().length + fit.more().length);
				boolean[] seen = new boolean[TABS.length];
				for (int tab : fit.row()) {
					seen[tab] = true;
				}
				for (int tab : fit.more()) {
					assertTrue(!seen[tab]);
					seen[tab] = true;
				}
				assertTrue(rowWidth(fit) <= room, "room " + room + " open " + open);
			}
		}
	}
}
