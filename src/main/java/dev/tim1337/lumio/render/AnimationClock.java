package dev.tim1337.lumio.render;

/**
 * Tracks animation progress as a value from 0 to 1 that wraps after each cycle. The speed can change at any time without the animation jumping,
 * since only the elapsed time is accumulated.
 */
public final class AnimationClock {
	/** Features may query the clock several times per frame; only the first query per frame advances it. */
	private static final long MIN_FRAME_NANOS = 4_000_000L;
	private static final float MAX_STEP_SECONDS = 0.1f;

	private float phase;
	private long lastNanos;

	/**
	 * @param speed        speed factor relative to the default
	 * @param cycleSeconds cycle length at the default speed
	 * @return the animation progress, from 0 inclusive to 1 exclusive
	 */
	public float advance(float speed, float cycleSeconds) {
		long now = System.nanoTime();
		if (lastNanos == 0L) {
			lastNanos = now;
		} else if (now - lastNanos >= MIN_FRAME_NANOS) {
			float seconds = Math.min((now - lastNanos) / 1e9f, MAX_STEP_SECONDS);
			phase += seconds * speed / cycleSeconds;
			phase -= (float) Math.floor(phase);
			lastNanos = now;
		}
		return phase;
	}
}
