package dev.tim1337.lumio.ui.motion;

/**
 * The main menu camera's way down at the start of the game: it starts high up and behind, looking up into the sky, and tilts down and comes down
 * to its resting place at the same time, on one smooth ease-in-out curve: slow at the start, fastest in the middle, settling softly with no
 * overshoot. The tilt leads the descent a little, so the fields come into view as the camera comes down. Both values go from 1 (the start) to 0
 * (at rest). Plain logic without the game, so it can be tested on its own.
 */
public final class CameraPath {
	/** How long the move takes, in seconds. */
	public static final float SECONDS = 3.2f;
	/** How much earlier the tilt is done than the descent, as a share of the move. */
	private static final float TILT_LEAD = 0.12f;

	private CameraPath() {
	}

	/** @return how high the camera still is, from 1 to 0, at a time in seconds since the move started */
	public static float descent(float seconds) {
		return 1f - ease(seconds / SECONDS);
	}

	/** @return how far the camera still looks up, from 1 to 0, at a time in seconds since the move started */
	public static float tilt(float seconds) {
		return 1f - ease(seconds / (SECONDS * (1f - TILT_LEAD)));
	}

	/** A quintic ease-in-out (smootherstep): no overshoot, and the speed is 0 at both ends. */
	public static float ease(float t) {
		float x = Math.max(0f, Math.min(1f, t));
		// clamped again, because float rounding can take the polynomial a hair past 1 just before the end
		return Math.min(1f, x * x * x * (x * (x * 6f - 15f) + 10f));
	}
}
