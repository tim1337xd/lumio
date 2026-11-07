package dev.tim1337.lumio.ui.motion;

/**
 * The path of a critically damped spring released at rest, as a function of time: for effects that start at a known moment (a name appearing,
 * a gain floating up), where no per-frame state is needed. It arrives without overshoot, like {@link dev.tim1337.lumio.api.experimental.motion.Spring}.
 */
public final class Critical {
	private static final double TWO_PI = Math.PI * 2.0;

	private Critical() {
	}

	/**
	 * @param seconds  time since the start; negative counts as the start
	 * @param response roughly how long it takes to arrive, in seconds
	 * @return progress from 0 to 1
	 */
	public static float progress(float seconds, float response) {
		if (seconds <= 0f) {
			return 0f;
		}
		double omega = TWO_PI / Math.max(0.01f, response);
		double t = omega * seconds;
		return (float) Math.min(1.0, 1.0 - (1.0 + t) * Math.exp(-t));
	}
}
