package dev.tim1337.lumio.hud;

/**
 * A HUD element the open chat lies over: at the size the player chose, the chat's panel can reach the hotbar, the status bars or a panel. While
 * the chat is open, just those elements are dimmed, so the chat reads over them, and they return when it closes. An element is never hidden. Plain
 * logic, tested without the game.
 */
public final class HudUnderChat {
	/** An element under the open chat keeps this much of its opacity, and gets this much black over it. */
	public static final float DIMMED = 0.3f;
	public static final float SHADE = 0.45f;
	/** How long an element takes to dim and to return, in seconds. */
	static final float FADE_SECONDS = 0.15f;
	private static final float NANOS_PER_SECOND = 1e9f;

	private HudUnderChat() {
	}

	/**
	 * @param element an element's box (x, y, width, height)
	 * @param chat    the open chat's panel, or null while the chat is closed
	 * @return whether the chat lies over a part of the element
	 */
	public static boolean under(int[] element, int[] chat) {
		return chat != null && element != null && element[2] > 0 && element[3] > 0 && chat[2] > 0 && chat[3] > 0 && element[0] < chat[0] + chat[2]
			&& element[0] + element[2] > chat[0] && element[1] < chat[1] + chat[3] && element[1] + element[3] > chat[1];
	}

	/** @return how much black lies over an element that keeps this much of its opacity */
	public static float shade(float kept) {
		return SHADE * (1f - kept) / (1f - DIMMED);
	}

	/** How much of its opacity one element keeps, eased between whole and dimmed. */
	public static final class Fade {
		private float kept = 1f;
		private long last;

		/**
		 * @param under    whether the open chat lies over the element now
		 * @param animated whether it eases; else it changes at once
		 * @return how much of its opacity the element keeps in this frame
		 */
		public float step(boolean under, boolean animated, long nanos) {
			float wanted = under ? DIMMED : 1f;
			float seconds = last == 0L ? 0f : Math.max(0f, (nanos - last) / NANOS_PER_SECOND);
			last = nanos;
			if (!animated) {
				kept = wanted;
			} else if (kept != wanted) {
				float step = seconds / FADE_SECONDS * (1f - DIMMED);
				kept = kept < wanted ? Math.min(wanted, kept + step) : Math.max(wanted, kept - step);
			}
			return kept;
		}
	}
}
