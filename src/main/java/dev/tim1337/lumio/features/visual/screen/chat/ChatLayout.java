package dev.tim1337.lumio.features.visual.screen.chat;

/**
 * Where everything sits in the open chat, from the outside in: the panel, a row's padding, the time and head columns, the text, and the room kept after it. Across, in the chat's own pixels (one is half a
 * pixel of the mockup at GUI scale 2, as the game's text is); up and down on the screen.
 */
public final class ChatLayout {
	/** What the panel has around the text width the Width setting names: 320 is a panel of 340, as in the mockup. */
	public static final int PANEL_EXTRA = 20;
	/** From the panel's side to a row. */
	public static final int ROW_INSET = 2;
	/** Inside a row, before and after what it holds; the stripe lies in the first. */
	public static final int PAD_LEFT = 6;
	public static final int PAD_RIGHT = 5;
	public static final int COLUMN_GAP = 4;
	public static final int HEAD = 8;

	/** The top bar with the tabs, with its divider under it. */
	public static final int BAR = 21;
	/** Between the bar and the first line, and between the last line and the input. */
	public static final int LIST_TOP = 4;
	public static final int LIST_BOTTOM = 3;
	public static final int INPUT_HEIGHT = 18;
	public static final int INPUT_SIDE = 5;
	public static final int INPUT_BOTTOM = 5;
	/** From where the lines end to where the panel ends. */
	public static final int BELOW_LINES = LIST_BOTTOM + INPUT_HEIGHT + INPUT_BOTTOM;
	/** A panel keeps this far from the screen's sides. */
	public static final int SCREEN_MARGIN = 2;
	/** How far the list of suggestions keeps from the panel's sides (its own panel reaches a little further out), and how far in from the list's left the game draws an entry. */
	public static final int SUGGESTIONS_SIDE = INPUT_SIDE;
	private static final int SUGGESTION_TEXT_INSET = 1;
	private static final int LEAST_TEXT = 40;
	private static final double GAME_LINE = 9.0;
	/** The game cuts its row height to whole pixels: a little over keeps it on the wanted one. */
	private static final double ROW_ROUNDING = 0.01;
	/** The game's own place for what a command takes: its lowest line starts this far above the screen's end, and each line is this high. */
	private static final int USAGE_FROM_BOTTOM = 27;
	private static final int USAGE_LINE = 12;

	private ChatLayout() {
	}

	/**
	 * @return the value of the game's line spacing option that gives rows of a height: while it draws, the game works its rows out from the option itself, not from its method for the line's height, and so the text
	 *         would stay apart from all that goes by that method
	 */
	public static double gameSpacing(int rowHeight) {
		return (rowHeight + ROW_ROUNDING) / GAME_LINE - 1.0;
	}

	/** @return where the time column starts */
	public static int timeLeft() {
		return ROW_INSET + PAD_LEFT;
	}

	/** @return where the head column starts: after the time, if there is one */
	public static int headLeft(int timeWidth) {
		return timeLeft() + (timeWidth > 0 ? timeWidth + COLUMN_GAP : 0);
	}

	/** @return where the text starts: the head's column stays on lines with no player, so the text lines up */
	public static int textLeft(int timeWidth, boolean heads) {
		return headLeft(timeWidth) + (heads ? HEAD + COLUMN_GAP : 0);
	}

	/** @return where a row's content ends */
	public static int contentRight(int panelWidth) {
		return panelWidth - ROW_INSET - PAD_RIGHT;
	}

	/** @return how wide the game may wrap a message's text, so all that is added to the line stays inside the panel */
	public static int wrapWidth(int panelWidth, int timeWidth, boolean heads) {
		return Math.max(LEAST_TEXT, contentRight(panelWidth) - textLeft(timeWidth, heads));
	}

	/** @return how wide a panel a line of a width needs, with its columns and the same padding as at any width */
	public static int panelFor(int lineWidth, int timeWidth, boolean heads) {
		return textLeft(timeWidth, heads) + lineWidth + PAD_RIGHT + ROW_INSET;
	}

	/** Which hint the input has room for: the longest that fits beside what is typed. */
	public enum Hint {
		CHANNEL, SUGGESTIONS, TAB, NONE
	}

	/** @return which hint fits in a room, from the longest down */
	public static Hint hintFor(int room, int channelWidth, int suggestionsWidth, int tabWidth) {
		if (channelWidth <= room) {
			return Hint.CHANNEL;
		}
		if (suggestionsWidth <= room) {
			return Hint.SUGGESTIONS;
		}
		return tabWidth <= room ? Hint.TAB : Hint.NONE;
	}

	/** @return where something is along an axis, kept on the screen; if it is larger than the screen, its start stays in view */
	public static int keepOnScreen(int wanted, int size, int screen) {
		return Math.max(SCREEN_MARGIN, Math.min(wanted, screen - SCREEN_MARGIN - size));
	}

	/** @return where a list of a height opens beside the input: above it, or below it if only that fits */
	public static int listTop(int height, int inputTop, int inputBottom, int screenHeight) {
		int above = inputTop - 1 - height;
		int below = inputBottom + 1;
		return above >= 0 || below + height > screenHeight ? Math.max(0, above) : below;
	}

	/** @return how wide the list of suggestions is: as wide as its entries, at most as wide as the panel and the screen allow */
	public static int suggestionsWidth(int width, int panelLeft, int panelWidth, int screenWidth) {
		return Math.max(1, Math.min(width, suggestionsRight(panelLeft, panelWidth, screenWidth) - suggestionsLeftmost(panelLeft)));
	}

	/**
	 * @param textLeft where the input's text starts: the list's entries line up with it
	 * @return where the list of suggestions starts across: at the input's text, inside the panel and on the screen, never left of the panel
	 */
	public static int suggestionsLeft(int textLeft, int width, int panelLeft, int panelWidth, int screenWidth) {
		int right = suggestionsRight(panelLeft, panelWidth, screenWidth);
		return Math.max(suggestionsLeftmost(panelLeft), Math.min(textLeft - SUGGESTION_TEXT_INSET, right - width));
	}

	private static int suggestionsLeftmost(int panelLeft) {
		return Math.max(panelLeft + SUGGESTIONS_SIDE, SCREEN_MARGIN);
	}

	private static int suggestionsRight(int panelLeft, int panelWidth, int screenWidth) {
		return Math.min(panelLeft + panelWidth - SUGGESTIONS_SIDE, screenWidth - SCREEN_MARGIN);
	}

	/** @return how far the lines of what a command takes move from the game's place, to lie above the input or below it if only that fits */
	public static int usageShift(int lines, int inputTop, int inputBottom, int screenHeight) {
		int gameTop = screenHeight - USAGE_FROM_BOTTOM - USAGE_LINE * (lines - 1);
		int height = USAGE_LINE * lines;
		return listTop(height, inputTop, inputBottom, screenHeight) - gameTop;
	}
}
