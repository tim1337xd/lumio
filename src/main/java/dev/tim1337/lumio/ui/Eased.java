package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.motion.Spring;

/**
 * A value that animates smoothly towards its target, so a size change is a motion rather than a jump. It is a critically damped spring that
 * never overshoots, so when the target changes mid-way it continues from its current position and velocity without jerking.
 */
public final class Eased {
	private final Spring spring = Spring.smooth(Spring.DEFAULT);

	/** @return the current value, after moving towards the target by the elapsed time */
	public float follow(float target) {
		return spring.follow(target);
	}

	/** @return the current value without advancing it */
	public float value() {
		return spring.value();
	}
}
