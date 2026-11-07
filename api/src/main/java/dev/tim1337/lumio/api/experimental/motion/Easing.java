package dev.tim1337.lumio.api.experimental.motion;

/**
 * The easing curves shared by the animations. Each takes a fraction, clamped to 0..1, and returns the eased fraction. Plain logic without the
 * game, so it can be tested on its own.
 */
public final class Easing {
	private Easing() {
	}

	/** @return a cubic ease out: fast at first, settling softly */
	public static float easeOut(float t) {
		float inverse = 1f - clamp(t);
		return 1f - inverse * inverse * inverse;
	}

	/** @return a smoothstep: slow at both ends */
	public static float smooth(float t) {
		float x = clamp(t);
		return x * x * (3f - 2f * x);
	}

	private static float clamp(float t) {
		return Math.max(0f, Math.min(1f, t));
	}
}
