package dev.tim1337.lumio.api.experimental.motion;

/**
 * An animation clock on real elapsed time, the same at 30 or 240 frames per second. Each frame advances it by the time since the last frame, but
 * by at most a thirtieth of a second: when the game stalls for a moment (as it does while loading), the animation goes on a little slower from
 * where it was instead of jumping ahead. Plain logic without the game, so it can be tested on its own.
 */
public final class AnimClock {
	/** The longest step of one frame, in seconds. */
	public static final float MAX_STEP = 1f / 30f;

	private long lastNanos;
	private double seconds;

	/** Advances the clock to the current time. @return the step of this frame in seconds, 0 for the first one */
	public float tick() {
		return tick(System.nanoTime());
	}

	/** Advances the clock to a point in time, in nanoseconds. @return the step in seconds, at most {@link #MAX_STEP}, 0 for the first one */
	public float tick(long nowNanos) {
		float step = lastNanos == 0L ? 0f : Math.min(MAX_STEP, Math.max(0f, (nowNanos - lastNanos) / 1_000_000_000f));
		lastNanos = nowNanos;
		seconds += step;
		return step;
	}

	/** @return the animation time in seconds since the start: the sum of the clamped steps */
	public float seconds() {
		return (float) seconds;
	}

	/** Starts again from 0. */
	public void reset() {
		lastNanos = 0L;
		seconds = 0.0;
	}
}
