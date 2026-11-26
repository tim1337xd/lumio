package dev.tim1337.lumio.features.music;

/**
 * Keeps a placed block of lyrics in the world readable without moving it. Its place in the world never changes; it turns on the spot to face the
 * player (across the ground, and up or down by at most {@link #MOST_PITCH_DEGREES}), following slowly and never rolled, so its rows are never
 * seen slanted or flat. A block that is still unreadable for longer than {@link #UNREADABLE_MS} (a third of it hidden by something nearer, half
 * of it off the screen, or far smaller or larger on the screen than when it was placed) is replaced at the next line start: that line is placed
 * fresh, and the old block fades out over {@link #REPLACED_SECONDS}. Nothing happens to it in the middle of a line, and a short glance away or a
 * tree passing before it never counts. Plain logic, tested without the game.
 */
final class LyricReadable {
	/** How far a block leans up or down toward the player at most, in degrees. */
	static final double MOST_PITCH_DEGREES = 30.0;
	/** How fast it turns after the player: the share of the way left it goes per second (a slow follow, no snapping). */
	static final double TURN_RATE = 2.5;
	/** More of its area than this hidden by something nearer makes it unreadable. */
	static final double MOST_HIDDEN = 1.0 / 3.0;
	/** More of its area than this off the screen makes it unreadable. */
	static final double MOST_OUTSIDE = 0.5;
	/** Its size on the screen against the size it was placed at: outside these it is unreadable. */
	static final double LEAST_SIZE = 0.6;
	static final double MOST_SIZE = 1.7;
	/** It is unreadable only after this long without a break. */
	static final long UNREADABLE_MS = 1_000L;
	/** How long a replaced block fades out. */
	static final float REPLACED_SECONDS = 0.4f;
	/** Something nearer than this hides a block; farther terrain is what a far block is drawn over on purpose. */
	static final double NEAR_COVER_BLOCKS = 24.0;

	private LyricReadable() {
	}

	/**
	 * @param dx where the player's eye is, from the block (x, y and z)
	 * @return the turn about the up axis and the lean that face the eye: {facing, lean} in radians, the lean at most {@link #MOST_PITCH_DEGREES}
	 *         either way; no roll is ever made
	 */
	static float[] facingEye(double dx, double dy, double dz) {
		double across = Math.hypot(dx, dz);
		// the same turn the drawing makes: it faces a point at (-dx, -dz) from the eye's side, so here the block looks toward the eye
		float facing = (float) Math.atan2(dx, dz);
		double most = Math.toRadians(MOST_PITCH_DEGREES);
		// as LyricUpright.lean: a block above the eye leans back toward it (the eye is below: dy is negative)
		float lean = (float) Math.clamp(Math.atan2(-dy, Math.max(across, 1e-6)), -most, most);
		return new float[] {facing, lean};
	}

	/** @return an angle moved part of the way toward another, the short way round, as {@link #TURN_RATE} asks for this frame */
	static float follow(float angle, float target, float seconds) {
		double difference = Math.atan2(Math.sin(target - angle), Math.cos(target - angle));
		double share = 1.0 - Math.exp(-TURN_RATE * Math.max(0f, seconds));
		return (float) (angle + difference * share);
	}

	/**
	 * @param hidden  the share of its area hidden by something nearer than it (and than {@link #NEAR_COVER_BLOCKS})
	 * @param outside the share of its area off the screen
	 * @param size    its size on the screen against the size it was placed at
	 * @return whether the block can not be read now
	 */
	static boolean unreadable(double hidden, double outside, double size) {
		return hidden > MOST_HIDDEN || outside > MOST_OUTSIDE || size < LEAST_SIZE || size > MOST_SIZE;
	}

	/** @return how long it has been unreadable without a break, after this frame, in milliseconds: back to 0 as soon as it can be read */
	static long unreadableFor(long before, boolean unreadable, long frameMs) {
		return unreadable ? before + Math.max(0L, frameMs) : 0L;
	}

	/** @return whether the block is replaced at a line start: unreadable for longer than {@link #UNREADABLE_MS} */
	static boolean replaceAtLineStart(long unreadableMs) {
		return unreadableMs > UNREADABLE_MS;
	}
}
