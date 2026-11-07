package dev.tim1337.lumio.ui.motion;

/**
 * When a loading screen may fade out once the game is ready: only after the drawn bar has reached its end and rested there for a moment, and
 * after the screen was shown for a minimum time, so a very short load does not flash. These waits only delay the fade of the picture, never
 * the game, and together they add at most {@link #MAX_EXTRA_SECONDS}. Times are in seconds on one clock. Plain logic without the game, so it
 * can be tested on its own.
 */
public final class LoadingTimeline {
	/** The shortest time a loading screen is shown. */
	public static final float MIN_SHOWN_SECONDS = 0.5f;
	/** How long the full bar rests before the fade. */
	public static final float REST_SECONDS = 0.15f;
	/** The longest the fade waits after the game is ready. */
	public static final float MAX_EXTRA_SECONDS = 0.6f;
	/** How long the fade out takes. */
	public static final float FADE_SECONDS = 0.25f;

	private LoadingTimeline() {
	}

	/**
	 * @param shownAt when the screen first showed
	 * @param readyAt when the game was ready
	 * @param fullAt when the drawn bar reached its end, or a negative value while it has not
	 * @param now the time now
	 * @return whether the fade may start
	 */
	public static boolean mayFade(float shownAt, float readyAt, float fullAt, float now) {
		if (now < readyAt) {
			return false;
		}
		if (now - readyAt >= MAX_EXTRA_SECONDS) {
			return true;
		}
		return fullAt >= 0f && now - fullAt >= REST_SECONDS && now - shownAt >= MIN_SHOWN_SECONDS;
	}

	/** @return how visible the screen still is during its fade out, from 1 to 0, smooth at both ends */
	public static float fadeAlpha(float fadeStart, float now) {
		float t = Math.max(0f, Math.min(1f, (now - fadeStart) / FADE_SECONDS));
		return 1f - t * t * (3f - 2f * t);
	}
}
