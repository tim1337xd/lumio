package dev.tim1337.lumio.features.music;

/**
 * How a line of the lyrics in the world stands: always upright, its up the world's up, never rolled or sideways, whatever the view. It faces
 * the player's horizontal direction at the moment it is placed (yaw only) and stays so; it stands at most {@link #MOST_ELEVATION_DEGREES}
 * above or below the horizon, so it is never right overhead or underfoot; placed above or below the eye it leans back toward the player by up
 * to {@link #MOST_LEAN_DEGREES}, so it reads well, turning only about its own level axis. The same for Classic, Cut-out and the cinematic
 * camera. Plain logic, tested without the game.
 */
final class LyricUpright {
	/** How far above or below the horizon a line may stand, in degrees. */
	static final double MOST_ELEVATION_DEGREES = 45.0;
	/** How far a line leans back toward the player at most, in degrees. */
	static final double MOST_LEAN_DEGREES = 20.0;

	private LyricUpright() {
	}

	/**
	 * Keeps a direction from the eye within {@link #MOST_ELEVATION_DEGREES} of the horizon, with its length and its heading across the ground;
	 * straight up or down it takes the heading of the view.
	 *
	 * @param direction {x, y, z} from the eye, changed in place
	 * @param viewYaw   the view's yaw in degrees (Minecraft's), for a direction with no heading of its own
	 * @return the direction's elevation afterwards, in radians: positive above the horizon
	 */
	static double keepNearHorizon(double[] direction, float viewYaw) {
		double across = Math.hypot(direction[0], direction[2]);
		double length = Math.sqrt(across * across + direction[1] * direction[1]);
		if (length < 1e-9) {
			return 0.0;
		}
		double headX;
		double headZ;
		if (across < 1e-6 * length) {
			double yaw = Math.toRadians(viewYaw);
			headX = -Math.sin(yaw);
			headZ = Math.cos(yaw);
		} else {
			headX = direction[0] / across;
			headZ = direction[2] / across;
		}
		double most = Math.toRadians(MOST_ELEVATION_DEGREES);
		double elevation = Math.clamp(Math.atan2(direction[1], across), -most, most);
		direction[0] = headX * Math.cos(elevation) * length;
		direction[1] = Math.sin(elevation) * length;
		direction[2] = headZ * Math.cos(elevation) * length;
		return elevation;
	}

	/** @return the turn about the up axis that faces a line at the eye, from where it stands (x and z from the eye), in radians */
	static float facing(double dx, double dz) {
		return (float) Math.atan2(-dx, -dz);
	}

	/** @return how far a line standing at an elevation leans back toward the eye, in radians, at most {@link #MOST_LEAN_DEGREES} either way */
	static float lean(double elevation) {
		double most = Math.toRadians(MOST_LEAN_DEGREES);
		return (float) Math.clamp(elevation, -most, most);
	}

	/** A block placed by the view faces the eye to within this, in degrees: steeply above or below, it leans further than the usual most. */
	static final double MOST_OFF_FACING_DEGREES = 30.0;

	/**
	 * @return how far a block placed by the view leans toward the eye, in radians: as {@link #lean}, and where it stands so steeply above or
	 *         below (the player looks down at a farm, or up a shaft) that it would be seen flat, further, so it is never more than
	 *         {@link #MOST_OFF_FACING_DEGREES} off facing the eye and keeps its size on the screen
	 */
	static float leanToward(double elevation) {
		double off = Math.toRadians(MOST_OFF_FACING_DEGREES);
		double least = Math.abs(elevation) - off;
		double usual = lean(elevation);
		return (float) (Math.abs(usual) >= least ? usual : Math.signum(elevation) * least);
	}

	/**
	 * @return the line's up and its right after its turn and its lean, as {upX, upY, upZ, rightX, rightY, rightZ}: the same turns as the drawing
	 *         makes (about the up axis by {@code facing}, then about the line's own level axis by {@code lean})
	 */
	static double[] axes(float facing, float lean) {
		double cf = Math.cos(facing);
		double sf = Math.sin(facing);
		double cl = Math.cos(lean);
		double sl = Math.sin(lean);
		// up (0, 1, 0) turned about x by the lean: (0, cos, sin); then about y by the facing
		double upX = sf * sl;
		double upY = cl;
		double upZ = cf * sl;
		// right (1, 0, 0) is the lean's own axis: only the facing turns it, so it stays level
		return new double[] {upX, upY, upZ, cf, 0.0, -sf};
	}
}
