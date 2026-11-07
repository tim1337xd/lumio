package dev.tim1337.lumio.api.experimental.motion;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;

/**
 * A value that follows its target like a mass on a spring, preserving its velocity when the target changes: an element grabbed or reversed
 * mid-motion neither jumps nor stops, it curves. This is the motion model of Apple's interfaces, described by the same two parameters:
 * <ul>
 * <li>damping ratio: 1 settles without overshoot, below 1 overshoots and swings back (0.8 is a small bounce)</li>
 * <li>response in seconds: roughly how long it takes to arrive. It is not a duration, since a spring has none</li>
 * </ul>
 */
public final class Spring {
	/** Damping for plain repositioning: arrives and stays, no swinging. */
	public static final float SMOOTH = 1f;

	// Shared response times, from fastest to slowest, so elements of the same kind move alike.
	/** Mouse-driven feedback: hover, highlights, opening lists. */
	public static final float SNAPPY = 0.16f;
	/** The default move of an element to a new position. */
	public static final float DEFAULT = 0.22f;
	/** Windows and panels appearing and disappearing. */
	public static final float GENTLE = 0.42f;
	/** Damping of a bouncy spring at the maximum bounce setting. */
	private static final float MOST_BOUNCE = 0.62f;

	private final float damping;
	/** Whether the damping follows the user's bounce setting, from none to {@link #MOST_BOUNCE}: for thrown elements, switches, opening windows. */
	private final boolean bouncy;
	private final float response;
	private float value;
	private float velocity;
	private boolean known;
	private long last = System.nanoTime();

	private Spring(float damping, boolean bouncy, float response) {
		this.damping = damping;
		this.bouncy = bouncy;
		this.response = response;
	}

	/** @return a critically damped spring that reaches its target without overshoot */
	public static Spring smooth(float response) {
		return new Spring(SMOOTH, false, response);
	}

	/** @return a spring with a fixed damping ratio, independent of the bounce setting: slightly below 1 for a barely visible overshoot */
	public static Spring damped(float damping, float response) {
		return new Spring(damping, false, response);
	}

	/** @return a spring that overshoots and swings back, as much as the bounce setting allows */
	public static Spring bouncy(float response) {
		return new Spring(SMOOTH, true, response);
	}

	private float dampingNow() {
		if (!bouncy) {
			return damping;
		}
		float bounce = MainSettings.bounce();
		return 1f + (MOST_BOUNCE - 1f) * bounce;
	}

	/** @return the current value, advanced by the time since the last call towards the current target */
	public float follow(float target) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		return step(target, dt);
	}

	/** @return the value moved towards the target by the given seconds (a panel's frame), or snapped there while the menu's animations are off */
	public float follow(float target, float seconds) {
		return MainSettings.animations() ? step(target, seconds) : snap(target);
	}

	/** Same, using the screen's frame time: with animations off nothing moves and the value is the target. */
	public float follow(Canvas ui, float target) {
		if (!ui.animated()) {
			return snap(target);
		}
		return step(target, ui.deltaSeconds());
	}

	/** @return the value after {@code dt} seconds, integrated in small sub-steps so a slow frame cannot destabilize it */
	public float step(float target, float dt) {
		if (!known) {
			known = true;
			value = target;
			velocity = 0f;
			return value;
		}
		float omega = (float) (2.0 * Math.PI / Math.max(0.05f, response));
		float stiffness = omega * omega;
		float friction = 2f * dampingNow() * omega;
		int steps = Math.max(1, (int) Math.ceil(dt / (1f / 240f)));
		float h = dt / steps;
		for (int i = 0; i < steps; i++) {
			velocity += (stiffness * (target - value) - friction * velocity) * h;
			value += velocity * h;
		}
		if (Math.abs(target - value) < 0.0008f && Math.abs(velocity) < 0.004f) {
			value = target;
			velocity = 0f;
		}
		return value;
	}

	/** Jumps to a value immediately and stops. */
	public float snap(float to) {
		known = true;
		value = to;
		velocity = 0f;
		last = System.nanoTime();
		return value;
	}

	/** Adds an impulse, as a click or press does to an element at rest. */
	public void kick(float speed) {
		velocity += speed;
	}

	public float value() {
		return value;
	}

	public float velocity() {
		return velocity;
	}

	public boolean moving() {
		return Math.abs(velocity) > 0.004f;
	}
}
