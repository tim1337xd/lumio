package dev.tim1337.lumio.features.waypoints;

import java.util.BitSet;
import java.util.List;

/**
 * Where a route is: when the player counts as at its next point (near it, past it along the route, or at a later point), and when the next
 * point's block changed while the player was close. Players usually stand near a point, not on its block, so "at" is a reach around it. One per
 * group, kept only while the game runs; it never moves a route back on its own, and after a point was chosen by hand it waits until the player
 * arrives there anew, so a point once left is not taken again by itself.
 */
final class RouteProgress {
	/** The reach a point counts as reached in, in blocks across, and its bounds. */
	static final int DEFAULT_REACH = 5;
	static final int MIN_REACH = 2;
	static final int MAX_REACH = 12;
	/** How far above or below the point's middle the player may be and still be at it, in blocks. */
	static final double VERTICAL_BLOCKS = 4.0;
	/** A point counts as passed only this close to it, in blocks. */
	static final double PASSED_WITHIN_BLOCKS = 10.0;
	/** How far beyond the point along the way to the next one the player must be for it to count as passed, in blocks. */
	static final double PASSED_BEYOND_BLOCKS = 0.5;
	/** The player counts as on the way on when closer to the next point than this share of the distance to the current one. */
	static final double CLEARLY_CLOSER = 1.0;
	/** A change of the point's block counts as mined only with the player this close, in blocks. */
	static final double MINED_WITHIN_BLOCKS = 6.0;

	/** Which points the player was at in the last step: a later point counts only when the player newly arrives at it. */
	private final BitSet at = new BitSet();
	private int known = -1;
	/** After a point was chosen by hand: the route waits until the player newly arrives at it, or goes far away. */
	private boolean held;
	/** The next point's block as last seen, and which point that was. */
	private Object block;
	private int blockOf = -1;

	/** Forgets where the player was at and any hold: the route starts as if it had just been started. */
	void reset() {
		at.clear();
		known = -1;
		held = false;
		block = null;
		blockOf = -1;
	}

	/** Holds the route at its next point, after it was chosen by hand (the keys, or the page). */
	void hold() {
		held = true;
	}

	boolean held() {
		return held;
	}

	/**
	 * Takes one step: whether the route goes on from its next point.
	 *
	 * @param px     the player's middle
	 * @param reach  the reach of the points, in blocks across
	 * @param moveOn whether being at or past a point moves the route on (else only the walk is followed, for the keys and the mined points)
	 * @return the index of the next point after this step
	 */
	int step(List<Waypoint> points, int next, double px, double py, double pz, int reach, boolean moveOn) {
		int count = points.size();
		if (count == 0) {
			return next;
		}
		if (count != known) {
			at.clear();
			known = count;
		}
		int later = -1;
		boolean arrivedAtNext = false;
		for (int i = 0; i < count; i++) {
			Waypoint point = points.get(i);
			boolean now = !point.hidden() && reached(point, px, py, pz, reach);
			boolean newly = now && !at.get(i);
			at.set(i, now);
			if (newly && i > next) {
				later = i;
			}
			arrivedAtNext |= newly && i == next;
		}
		if (held) {
			Waypoint point = points.get(next);
			if (arrivedAtNext || later >= 0) {
				held = false;
			} else {
				if (point.distanceSq(px, py, pz) > (PASSED_WITHIN_BLOCKS + reach) * (PASSED_WITHIN_BLOCKS + reach)) {
					// wandered off: the route follows again from there, without moving on
					held = false;
				}
				return next;
			}
		}
		if (!moveOn) {
			return next;
		}
		// at the next point itself first: a later point at the same place (a loop's last) must not take the route around
		if (at.get(next)) {
			return after(next, count);
		}
		if (later >= 0) {
			return after(later, count);
		}
		if (next + 1 < count && passed(points.get(next), points.get(next + 1), px, py, pz)) {
			return after(next, count);
		}
		return next;
	}

	/**
	 * @param state the block at the next point now, null while its chunk is not there
	 * @param near  whether the player is close enough for a change to count
	 * @return whether the next point's block changed while the player was close: mined, or anything else in its place
	 */
	boolean mined(int next, Object state, boolean near) {
		if (state == null) {
			return false;
		}
		if (next != blockOf || block == null || !near) {
			// a new point, or far away: what is there now is what counts
			blockOf = next;
			block = state;
			return false;
		}
		if (!state.equals(block)) {
			block = state;
			return true;
		}
		return false;
	}

	/** @return the point after this one; the route starts again after its last */
	static int after(int index, int count) {
		return (index + 1) % count;
	}

	/** @return the point before this one; before the first comes the last */
	static int before(int index, int count) {
		return Math.floorMod(index - 1, count);
	}

	/** @return whether the player is at a point: within its reach across, and not far above or below it */
	static boolean reached(Waypoint point, double px, double py, double pz, int reach) {
		double dx = point.x() + 0.5 - px;
		double dz = point.z() + 0.5 - pz;
		double dy = point.y() + 0.5 - py;
		return dx * dx + dz * dz <= (double) reach * reach && Math.abs(dy) <= VERTICAL_BLOCKS;
	}

	/**
	 * @return whether the player went past a point on the way to the next one: near it, and beyond it along the way, or clearly closer to the
	 *         next point
	 */
	static boolean passed(Waypoint current, Waypoint next, double px, double py, double pz) {
		double toCurrentSq = current.distanceSq(px, py, pz);
		if (toCurrentSq > PASSED_WITHIN_BLOCKS * PASSED_WITHIN_BLOCKS) {
			return false;
		}
		double wayX = next.x() - current.x();
		double wayY = next.y() - current.y();
		double wayZ = next.z() - current.z();
		double wayLength = Math.sqrt(wayX * wayX + wayY * wayY + wayZ * wayZ);
		if (wayLength < 1e-6) {
			return false;
		}
		double along = ((px - current.x() - 0.5) * wayX + (py - current.y() - 0.5) * wayY + (pz - current.z() - 0.5) * wayZ) / wayLength;
		if (along > PASSED_BEYOND_BLOCKS) {
			return true;
		}
		return next.distanceSq(px, py, pz) < CLEARLY_CLOSER * CLEARLY_CLOSER * toCurrentSq;
	}

	/** @return whether the player is close enough to a point for a change of its block to count as mined */
	static boolean nearForMined(Waypoint point, double px, double py, double pz) {
		return point.distanceSq(px, py, pz) <= MINED_WITHIN_BLOCKS * MINED_WITHIN_BLOCKS;
	}
}
