package dev.tim1337.lumio.features.visual.screen.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nothing added to a line leaves the panel, whatever the columns are, and the input and its lists stay on the screen. */
class ChatLayoutTest {
	private static final int PANEL = 340;
	private static final int TIME = 17;

	@Test
	void aFullLineEndsAtThePaddingAtEveryWidth() {
		for (int panel = 140; panel <= 660; panel += 10) {
			int wrap = ChatLayout.wrapWidth(panel, TIME, true);
			assertEquals(panel - ChatLayout.ROW_INSET - ChatLayout.PAD_RIGHT, ChatLayout.textLeft(TIME, true) + wrap, "panel " + panel);
		}
	}

	@Test
	void theHintShortensAndThenHides() {
		assertEquals(ChatLayout.Hint.CHANNEL, ChatLayout.hintFor(100, 90, 70, 10));
		assertEquals(ChatLayout.Hint.SUGGESTIONS, ChatLayout.hintFor(80, 90, 70, 10));
		assertEquals(ChatLayout.Hint.TAB, ChatLayout.hintFor(20, 90, 70, 10));
		assertEquals(ChatLayout.Hint.NONE, ChatLayout.hintFor(5, 90, 70, 10));
	}

	@Test
	void theGameDrawsRowsOfTheWantedHeight() {
		for (int height = 8; height <= 20; height++) {
			assertEquals(height, (int) (9.0 * (ChatLayout.gameSpacing(height) + 1.0)));
		}
	}

	@Test
	void headsOffDropTheColumnAndItsGap() {
		assertEquals(ChatLayout.HEAD + ChatLayout.COLUMN_GAP, ChatLayout.textLeft(TIME, true) - ChatLayout.textLeft(TIME, false));
	}

	@Test
	void noTimeDropsItsColumnAndGap() {
		assertEquals(ChatLayout.timeLeft(), ChatLayout.headLeft(0));
		assertEquals(ChatLayout.timeLeft() + TIME + ChatLayout.COLUMN_GAP, ChatLayout.headLeft(TIME));
	}

	@Test
	void aVeryNarrowPanelStillWrapsSomewhere() {
		assertEquals(40, ChatLayout.wrapWidth(50, TIME, true));
	}

	@Test
	void thePanelStaysOnTheScreen() {
		assertEquals(ChatLayout.SCREEN_MARGIN, ChatLayout.keepOnScreen(-500, PANEL, 960));
		assertEquals(960 - ChatLayout.SCREEN_MARGIN - PANEL, ChatLayout.keepOnScreen(5000, PANEL, 960));
		assertEquals(100, ChatLayout.keepOnScreen(100, PANEL, 960));
		assertEquals(ChatLayout.SCREEN_MARGIN, ChatLayout.keepOnScreen(100, 2000, 960), "too large: its start stays in view");
	}

	@Test
	void suggestionsOpenAboveIfTheyFitAndBelowOtherwise() {
		assertEquals(500 - 1 - 120, ChatLayout.listTop(120, 500, 518, 540));
		assertEquals(40 + 18 + 1, ChatLayout.listTop(120, 40, 58, 540), "no room above");
		assertEquals(0, ChatLayout.listTop(600, 40, 58, 540), "fits nowhere: from the top");
	}

	@Test
	void whatACommandTakesMovesWithTheInput() {
		int screen = 540;
		int shift = ChatLayout.usageShift(1, 480, 498, screen);
		assertEquals(480 - 1 - 12, screen - 27 + shift, "its line lies right above the input");
		int low = ChatLayout.usageShift(2, 4, 22, screen);
		assertEquals(23, screen - 27 - 12 + low, "at the top of the screen it opens below the input");
	}

	@Test
	void suggestionsLineUpWithTheInputText() {
		// a panel at 40 of 300 wide on a 960 screen, the input's text at 51: the list's entries start where the text does
		int width = ChatLayout.suggestionsWidth(120, 40, 300, 960);
		assertEquals(120, width);
		assertEquals(50, ChatLayout.suggestionsLeft(51, width, 40, 300, 960));
	}

	@Test
	void suggestionsStayInsideThePanel() {
		// never left of the panel, even where the game would put them (at the screen's start)
		int width = ChatLayout.suggestionsWidth(120, 40, 300, 960);
		assertEquals(40 + ChatLayout.SUGGESTIONS_SIDE, ChatLayout.suggestionsLeft(0, width, 40, 300, 960));
		// a long list near the panel's right moves left to fit, and one wider than the panel gets the panel's width
		assertEquals(40 + 300 - ChatLayout.SUGGESTIONS_SIDE - 120, ChatLayout.suggestionsLeft(300, width, 40, 300, 960));
		int wide = ChatLayout.suggestionsWidth(500, 40, 300, 960);
		assertEquals(300 - 2 * ChatLayout.SUGGESTIONS_SIDE, wide);
		assertEquals(40 + ChatLayout.SUGGESTIONS_SIDE, ChatLayout.suggestionsLeft(51, wide, 40, 300, 960));
	}

	@Test
	void suggestionsStayOnASmallScreen() {
		// at GUI scale 4 in a small window the panel can reach past the screen's right: the list stays on the screen
		int width = ChatLayout.suggestionsWidth(200, 4, 300, 213);
		assertTrue(4 + ChatLayout.SUGGESTIONS_SIDE + width <= 213 - ChatLayout.SCREEN_MARGIN);
		int left = ChatLayout.suggestionsLeft(15, width, 4, 300, 213);
		assertTrue(left >= 4 + ChatLayout.SUGGESTIONS_SIDE && left + width <= 213 - ChatLayout.SCREEN_MARGIN);
	}
}
