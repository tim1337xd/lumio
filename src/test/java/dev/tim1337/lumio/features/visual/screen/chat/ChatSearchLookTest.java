package dev.tim1337.lumio.features.visual.screen.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The search's look when closed, open and in between. */
class ChatSearchLookTest {
	private static final int BAR_LEFT = 10;
	private static final int BAR_RIGHT = 300;
	private static final int BUTTON = 13;
	private static final int ACTIVE = 40;
	private static final int GAP = 2;

	@Test
	void takesAboutASixthOfASecondEachWay() {
		float amount = 0f;
		for (int i = 0; i < 3; i++) {
			amount = ChatSearchLook.advance(amount, true, 0.05f, true);
		}
		assertEquals(1f, amount, 0.001f, "open after 150 ms");
		for (int i = 0; i < 3; i++) {
			amount = ChatSearchLook.advance(amount, false, 0.05f, true);
		}
		assertEquals(0f, amount, 0.001f, "and closed after 150 ms");
		assertEquals(1f, ChatSearchLook.advance(0f, true, 0.001f, false), "without animations at once");
		assertEquals(0f, ChatSearchLook.advance(1f, false, 0.001f, false));
	}

	@Test
	void closedIsTheButtonAndOpenTakesTheRow() {
		int open = ChatSearchLook.openLeft(BAR_LEFT, ACTIVE, GAP);
		assertEquals(BAR_RIGHT - BUTTON, ChatSearchLook.fieldLeft(BAR_RIGHT, BUTTON, open, 0f), "closed: just the button");
		assertEquals(BAR_LEFT + ACTIVE + GAP, ChatSearchLook.fieldLeft(BAR_RIGHT, BUTTON, open, 1f), "open: all but the active tab");
		assertEquals(BAR_LEFT, ChatSearchLook.openLeft(BAR_LEFT, 0, GAP), "without tabs all of the row");
	}

	@Test
	void theFieldGrowsOutOfTheIconSteadily() {
		int open = ChatSearchLook.openLeft(BAR_LEFT, ACTIVE, GAP);
		int last = Integer.MAX_VALUE;
		for (float amount = 0f; amount <= 1f; amount += 0.1f) {
			int left = ChatSearchLook.fieldLeft(BAR_RIGHT, BUTTON, open, ChatSearchLook.eased(amount));
			assertTrue(left <= last, "never back");
			last = left;
		}
		assertTrue(ChatSearchLook.eased(0.5f) > 0.5f, "quick first, then settling");
	}

	@Test
	void onlyTheActiveTabStays() {
		assertEquals(120, ChatSearchLook.activeLeft(120, BAR_LEFT, 0f), "closed: at its place in the row");
		assertEquals(BAR_LEFT, ChatSearchLook.activeLeft(120, BAR_LEFT, 1f), "open: at the left");
		assertEquals(1f, ChatSearchLook.otherTabs(0f));
		assertEquals(0f, ChatSearchLook.otherTabs(0.6f), 0.001f);
		assertEquals(0f, ChatSearchLook.otherTabs(1f));
		assertTrue(ChatSearchLook.tabsAnswer(false, 0f));
		assertFalse(ChatSearchLook.tabsAnswer(true, 0f), "not once it opens");
		assertFalse(ChatSearchLook.tabsAnswer(false, 0.5f), "nor while it closes");
	}

	@Test
	void itIsTheChatsGlassWithAThinLine() {
		assertEquals(0x12, ChatSearchLook.FIELD_FILL >>> 24, "white at 7 %");
		assertEquals(0x1A, ChatSearchLook.FIELD_LINE >>> 24, "white at 10 %");
		assertEquals(0xFFFFFF, ChatSearchLook.FIELD_FILL & 0xFFFFFF);
		assertEquals(0.5f, ChatSearchLook.FOCUS_LINE);
	}
}
