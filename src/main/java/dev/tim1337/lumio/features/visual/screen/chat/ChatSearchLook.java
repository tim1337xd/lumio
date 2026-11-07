package dev.tim1337.lumio.features.visual.screen.chat;

/**
 * How the chat's search looks between closed and open: a quiet button at the right of the tab row, from which the field grows until it takes the row, with only the active tab left beside it. Plain numbers, so it is unit tested.
 */
public final class ChatSearchLook {
	/** The way from closed to open, and back. */
	public static final float OPEN_SECONDS = 0.15f;
	/** The closed button: the tabs' glass, without a line. */
	public static final int BUTTON_FILL = 0x0FFFFFFF;
	/** The open field: white at 7 % on the chat's glass, with a line of white at 10 %. */
	public static final int FIELD_FILL = 0x12FFFFFF;
	public static final int FIELD_LINE = 0x1AFFFFFF;
	/** The focused field's line and its faint glow, as shares of the accent. */
	public static final float FOCUS_LINE = 0.5f;
	public static final float FOCUS_GLOW = 0.14f;
	/** The other tabs are gone once the field has come this far of the way. */
	private static final float TABS_GONE_AT = 0.6f;

	private ChatSearchLook() {
	}

	/** @return how far the search is open after a moment, from 0 to 1, at an even pace */
	public static float advance(float amount, boolean open, float seconds, boolean animated) {
		if (!animated) {
			return open ? 1f : 0f;
		}
		float step = seconds / OPEN_SECONDS;
		return open ? Math.min(1f, amount + step) : Math.max(0f, amount - step);
	}

	/** @return the way as it is seen: quick at first, then settling */
	public static float eased(float amount) {
		float rest = 1f - amount;
		return 1f - rest * rest * rest;
	}

	/**
	 * @param activeWidth how wide the active tab is, 0 where tabs are off
	 * @return where the field starts when it is open
	 */
	public static int openLeft(int barLeft, int activeWidth, int gap) {
		return activeWidth > 0 ? barLeft + activeWidth + gap : barLeft;
	}

	/** @return where the field starts now: from the button at the right end of the bar to its open place */
	public static int fieldLeft(int barRight, int button, int openLeft, float eased) {
		return Math.round(barRight - button + (openLeft - (barRight - button)) * eased);
	}

	/** @return where the active tab stands now: from its place in the row to the left end of the bar */
	public static int activeLeft(int rowLeft, int barLeft, float eased) {
		return Math.round(rowLeft + (barLeft - rowLeft) * eased);
	}

	/** @return how visible the other tabs are now */
	public static float otherTabs(float eased) {
		return Math.max(0f, 1f - eased / TABS_GONE_AT);
	}

	/** @return whether the other tabs can be clicked: not while any of the field is out */
	public static boolean tabsAnswer(boolean open, float eased) {
		return !open && eased < 0.01f;
	}
}
