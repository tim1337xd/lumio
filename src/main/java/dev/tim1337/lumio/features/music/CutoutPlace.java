package dev.tim1337.lumio.features.music;

/**
 * When a placed block of lyrics in the world (Classic or Cut-out) ends or takes the next line. The lyrics never follow the camera: a block keeps
 * its place, turning on the spot to face the player, and is replaced only at a line start.
 * <ul>
 * <li>any part of it in view: it stays where it is, and the next line joins it;</li>
 * <li>out of view when a line starts: that line is placed fresh in the view; one that could not be read for a while is replaced then too
 * ({@link LyricReadable}).</li>
 * </ul>
 * The cinematic camera keeps its own, quicker rules ({@link #keepsShot}, {@link #clearShot}): its shots move on purpose. Screen places run from
 * -1 to 1 across and up the picture. Plain logic, tested without the game.
 */
final class CutoutPlace {
	/** Where a block lies in the view. */
	enum Where {
		INSIDE,
		EDGE,
		OUTSIDE
	}

	/** In the cinematic camera a column out of the picture for longer than this is placed anew in the shot. */
	static final long SHOT_OUTSIDE_MS = 400L;
	/** A block nearer than this to an edge of the screen is at the edge. */
	static final double NEAR = 0.03;

	private CutoutPlace() {
	}

	/**
	 * @param inFront    whether the block's middle is in front of the camera
	 * @param x          its middle on the screen, across and ({@code y}) up
	 * @param halfWidth  half its width and ({@code halfHeight}) height on the screen
	 * @return where it lies: outside when nothing of it is on the screen, inside when all of it is, clear of the edges
	 */
	static Where where(boolean inFront, double x, double y, double halfWidth, double halfHeight) {
		if (!inFront || x - halfWidth >= 1.0 || x + halfWidth <= -1.0 || y - halfHeight >= 1.0 || y + halfHeight <= -1.0) {
			return Where.OUTSIDE;
		}
		double most = 1.0 - NEAR;
		return Math.abs(x) + halfWidth <= most && Math.abs(y) + halfHeight <= most ? Where.INSIDE : Where.EDGE;
	}

	/** @return whether the next line joins the block: any part of it is in view */
	static boolean joins(Where where) {
		return where != Where.OUTSIDE;
	}

	/** @return whether the film's column is cleared and placed anew in the shot now, in the middle of its line */
	static boolean clearShot(Where where, long outsideMs) {
		return where == Where.OUTSIDE && outsideMs > SHOT_OUTSIDE_MS;
	}

	/** @return whether the film's next line takes the column's spot: all of it well inside the picture */
	static boolean keepsShot(Where where) {
		return where == Where.INSIDE;
	}
}
