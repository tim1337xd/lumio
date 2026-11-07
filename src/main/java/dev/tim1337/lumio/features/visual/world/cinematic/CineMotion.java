package dev.tim1337.lumio.features.visual.world.cinematic;

/**
 * The limits of the cinematic camera's motion, all in one place, so they are easy to tune: a calm, steady film camera. Every shot's move (an
 * orbit over a small arc, a crane's rise, a push in or a pull back over a short way, a slow drift) runs once over {@link #MOVE_SECONDS}, starting
 * and ending softly, and then the camera holds; about a third of the shots are locked off with only a very slight drift. An orbit's arc is
 * the camera's whole turn too (it keeps looking at the player), so it stays within the turn of a shot. The camera turns at most about
 * {@link #MOST_TURN_RATE} degrees a second and {@link #MOST_TURN} degrees in a whole shot.
 */
final class CineMotion {
	/** The time a shot's move takes from its start to its end, in seconds; after it the camera holds still. */
	static final double MOVE_SECONDS = 24.0;
	/** The most the camera turns, in degrees per second and in a whole shot. */
	static final double MOST_TURN_RATE = 6.0;
	static final double MOST_TURN = 30.0;
	/** Arcs around the player, in degrees: a slow orbit's least and most, the wide crane's and the low shot's. */
	static final double ORBIT_LEAST_ARC = 12.0;
	static final double ORBIT_MOST_ARC = 30.0;
	static final double CRANE_ARC = 12.0;
	static final double LOW_ARC = 9.0;
	/** How far the moves go, in blocks: the crane's rise, the push in, the push over the shoulder and the drift from above. */
	static final double CRANE_RISE = 1.0;
	static final double PUSH_IN = 1.0;
	static final double SHOULDER_PUSH = 0.5;
	static final double TOP_DRIFT = 1.0;
	/** How much the wide crane's view narrows over its move, in degrees. */
	static final float CRANE_ZOOM = 4f;
	/** The share of shots that are locked off, and how much of its move such a shot keeps: a very slight drift. */
	static final double STILL_SHARE = 1.0 / 3.0;
	static final double STILL_MOVE = 0.15;

	private CineMotion() {
	}

	/** @return how far a shot's move has come at a moment, from 0 to 1, starting and ending softly with no sudden change of speed */
	static double progress(double seconds) {
		double t = Math.max(0.0, Math.min(1.0, seconds / MOVE_SECONDS));
		// smootherstep: its speed and its change of speed are zero at both ends
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}
}
