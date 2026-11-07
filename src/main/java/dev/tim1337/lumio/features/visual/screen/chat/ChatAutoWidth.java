package dev.tim1337.lumio.features.visual.screen.chat;

import com.google.gson.JsonObject;

/**
 * The width of the chat in its Auto mode, on the screen: as wide as its longest shown line wants, at least what the tab row needs and at most the Max width. It grows at once, so a line is never cut, and shrinks slowly a
 * few seconds after a wider line has gone, so it does not jump with every message. Lines are wrapped for the Max width, so all this never wraps them anew. Plain numbers, so it is unit tested.
 */
public final class ChatAutoWidth {
	/** How long the width stays after the last line that needed it. */
	static final long HOLD_MILLIS = 4_000L;
	/** The shrinking: how much of what is left goes per second, and the least it moves, so it comes to its end. */
	private static final float SHRINK_RATE = 1f;
	private static final float LEAST_SHRINK_PER_SECOND = 20f;
	private static final float MILLIS_PER_SECOND = 1000f;
	private static final String WIDTH_KEY = "width";
	private static final String MODE_KEY = "width_mode";
	private static final String FIXED = "FIXED";

	private float shown = -1f;
	private long neededAt;
	private long lastAt;

	/**
	 * @return how wide the chat wants to be
	 * @param lines the longest line, with the panel's columns and padding
	 * @param tabs  what the tab row needs
	 * @param least the narrowest it gets, for the input
	 * @param most  the Max width, which goes before all else
	 */
	public static int wanted(int lines, int tabs, int least, int most) {
		return Math.min(most, Math.max(lines, Math.max(tabs, least)));
	}

	/** @return how wide the chat is now, in whole pixels, so its lines fit also while it shrinks; before the first update, -1 */
	public int width() {
		return shown < 0f ? -1 : (int) Math.ceil(shown);
	}

	/**
	 * Follows what the chat wants to be.
	 * @param wanted   see {@link #wanted}
	 * @param animated whether it shrinks smoothly; if not, in one step, also after the wait
	 */
	public void update(int wanted, long nowMillis, boolean animated) {
		float seconds = Math.max(0L, nowMillis - lastAt) / MILLIS_PER_SECOND;
		lastAt = nowMillis;
		if (wanted >= shown) {
			shown = wanted;
			neededAt = nowMillis;
		} else if (nowMillis - neededAt >= HOLD_MILLIS) {
			float step = Math.max(LEAST_SHRINK_PER_SECOND * seconds, (shown - wanted) * (1f - (float) Math.exp(-SHRINK_RATE * seconds)));
			shown = animated ? Math.max(wanted, shown - step) : wanted;
		}
	}

	/** A config from before this mode has its width, which stays: only a new config starts in Auto. */
	public static void keepOldWidth(JsonObject json) {
		if (json.has(WIDTH_KEY) && !json.has(MODE_KEY)) {
			json.addProperty(MODE_KEY, FIXED);
		}
	}
}
