package dev.tim1337.lumio.ui.motion;

/**
 * The drawn value of a loading bar. It never shows the reported progress directly: it follows it with a critically damped motion (no overshoot),
 * only ever moves forward within one load, and has a top speed, so even a jump from 20% to 100% glides over at least about 0.4 seconds. When the
 * game starts a new phase whose progress begins at 0 again, the bar carries on from where it is towards the end instead of going back. Plain
 * logic without the game, so it can be tested on its own.
 */
public final class ProgressSmoother {
	/** The fastest the bar moves, in fractions of the bar per second: 0.8 of the bar take at least 0.4 seconds. */
	public static final float MAX_SPEED = 2f;
	/** How quickly the bar follows, as the spring's angular frequency, per second. */
	private static final float RESPONSE = 9f;
	/** A drop of the reported progress by more than this starts a new phase. */
	private static final float NEW_PHASE_DROP = 0.2f;

	private float value;
	private float velocity;
	private float target;
	/** Where the bar was when the current phase began: the phase's 0, so its progress fills the rest of the bar. */
	private float phaseBase;
	private float lastReported = -1f;

	/** Starts a new load from an empty bar. */
	public void reset() {
		value = 0f;
		velocity = 0f;
		target = 0f;
		phaseBase = 0f;
		lastReported = -1f;
	}

	/** Reports the game's progress, from 0 to 1. The target only moves forward. */
	public void report(float progress) {
		float p = Math.max(0f, Math.min(1f, progress));
		if (lastReported >= 0f && p < lastReported - NEW_PHASE_DROP) {
			// a new phase that counts from 0 again: it fills what is left of the bar
			phaseBase = value;
		}
		lastReported = p;
		target = Math.max(target, phaseBase + p * (1f - phaseBase));
	}

	/** The load is done: the bar goes to its end. */
	public void complete() {
		target = 1f;
	}

	/** Moves the bar by a time step in seconds. @return the drawn value, from 0 to 1 */
	public float update(float dt) {
		if (dt <= 0f) {
			return value;
		}
		// critically damped spring towards the target, integrated in small steps so a long frame stays stable
		float left = dt;
		while (left > 0f) {
			float h = Math.min(left, 1f / 120f);
			left -= h;
			float acceleration = RESPONSE * RESPONSE * (target - value) - 2f * RESPONSE * velocity;
			velocity += acceleration * h;
			velocity = Math.max(0f, Math.min(MAX_SPEED, velocity));
			value = Math.min(target, value + velocity * h);
			if (value >= target) {
				velocity = 0f;
			}
		}
		return value;
	}

	public float value() {
		return value;
	}

	public float target() {
		return target;
	}

	/** @return whether the bar has reached its end */
	public boolean full() {
		return value >= 0.999f;
	}
}
