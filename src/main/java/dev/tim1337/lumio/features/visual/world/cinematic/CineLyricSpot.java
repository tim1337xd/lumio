package dev.tim1337.lumio.features.visual.world.cinematic;

/**
 * Where a shot's lyric stands. The ideal spot is in the open on the third opposite the player, far enough to read like a title. Where no such
 * spot is free (close-ups against a wall, small rooms), the lyric still gets a safe spot: in the lower third, a little right of the center so
 * it keeps clear of the song card in the lower left, as far as the open space allows, at least close in front of the camera. A shot never ends
 * up without a lyric. Works on a {@link Space} of ray tests, so it is unit tested without a world.
 */
final class CineLyricSpot {
	/** The ray tests the spot needs: how far the open space reaches in a direction, and whether a straight path is clear. */
	interface Space {
		double reach(double x, double y, double z, double dx, double dy, double dz, double limit);

		boolean clear(double x0, double y0, double z0, double x1, double y1, double z1);
	}

	/** A spot: where, how large (blocks per text unit), how open the space before it was, and whether it is the safe fallback. */
	static final class Spot {
		double x;
		double y;
		double z;
		float unit;
		double open;
		boolean fallback;
	}

	// the ideal spot: its distance range, the margin kept from what is behind it, how far above the view's center, how wide a typical line is
	static final double NEAREST = 6.0;
	static final double FURTHEST = 14.0;
	static final double MARGIN = 1.5;
	static final float RAISE = 6f;
	static final float SHARE = 0.4f;
	static final float TYPICAL_LINE_UNITS = 220f;
	/** The open space has to reach the line's ends and below it: how far out (a share of half the line) and how far down. */
	static final double REACH = 0.65;
	static final double BELOW = 0.6;
	// the safe spot: how far right of the center (a share of half the frame), how far down into the lower third, its distances and size
	private static final double SAFE_RIGHT = 0.15;
	private static final double SAFE_DOWN = 0.38;
	private static final double SAFE_FURTHEST = 8.0;
	private static final double SAFE_NEAREST = 1.2;
	private static final double SAFE_MARGIN = 0.6;
	private static final float SAFE_SHARE = 0.85f;
	/** The safe spot stands this far in front of the player when the player is ahead of the camera, so the player never covers it, in blocks. */
	private static final double PLAYER_ROOM = 0.8;

	private CineLyricSpot() {
	}

	/**
	 * Finds the lyric's spot for a camera.
	 *
	 * @param lyricYaw the yaw of the ideal spot (on the other third), viewYaw and viewPitch where the camera looks, all in degrees
	 * @param px       the player's eyes (with py and pz): the safe spot stands in front of the player, never behind them
	 */
	static void find(Space space, double cx, double cy, double cz, float lyricYaw, float viewYaw, float viewPitch, float fov, float aspect, double px,
					 double py, double pz, Spot out) {
		double yaw = Math.toRadians(lyricYaw);
		double pitch = Math.toRadians(viewPitch - RAISE);
		double dirX = -Math.sin(yaw) * Math.cos(pitch);
		double dirY = -Math.sin(pitch);
		double dirZ = Math.cos(yaw) * Math.cos(pitch);
		double open = space.reach(cx, cy, cz, dirX, dirY, dirZ, FURTHEST + MARGIN);
		double place = Math.min(FURTHEST, open - MARGIN);
		double halfFrame = Math.tan(Math.toRadians(fov) / 2.0) * aspect;
		double frameWidth = 2.0 * place * halfFrame;
		// the text is wide and the next line stands below it: the open space has to reach its ends and below it too, or a wall covers part of it
		double halfText = frameWidth * SHARE * REACH;
		double sideX = Math.cos(yaw) * halfText;
		double sideZ = Math.sin(yaw) * halfText;
		boolean room = place >= NEAREST
			&& space.clear(cx, cy, cz, cx + dirX * place + sideX, cy + dirY * place, cz + dirZ * place + sideZ)
			&& space.clear(cx, cy, cz, cx + dirX * place - sideX, cy + dirY * place, cz + dirZ * place - sideZ)
			&& space.clear(cx, cy, cz, cx + dirX * place, cy + dirY * place - halfText * BELOW, cz + dirZ * place);
		out.open = open;
		if (room) {
			out.fallback = false;
			out.x = cx + dirX * place;
			out.y = cy + dirY * place;
			out.z = cz + dirZ * place;
			out.unit = (float) (frameWidth * SHARE / TYPICAL_LINE_UNITS);
			return;
		}
		// the safe spot: lower third, a little right of the center, as far as the open space allows, never nearer than close in front
		double halfHeight = Math.tan(Math.toRadians(fov) / 2.0);
		double safeYaw = Math.toRadians(viewYaw + Math.toDegrees(Math.atan(halfFrame * SAFE_RIGHT)));
		double safePitch = Math.toRadians(viewPitch + Math.toDegrees(Math.atan(halfHeight * SAFE_DOWN)));
		double sx = -Math.sin(safeYaw) * Math.cos(safePitch);
		double sy = -Math.sin(safePitch);
		double sz = Math.cos(safeYaw) * Math.cos(safePitch);
		double safeOpen = space.reach(cx, cy, cz, sx, sy, sz, SAFE_FURTHEST + SAFE_MARGIN);
		double safeFurthest = Math.min(SAFE_FURTHEST, safeOpen - SAFE_MARGIN);
		// a player ahead of the camera would cover a line behind them: it comes to stand in front of them
		double playerDepth = (px - cx) * sx + (py - cy) * sy + (pz - cz) * sz;
		if (playerDepth > 0.0) {
			safeFurthest = Math.min(safeFurthest, playerDepth - PLAYER_ROOM);
		}
		double safePlace = Math.max(SAFE_NEAREST, safeFurthest);
		out.fallback = true;
		out.x = cx + sx * safePlace;
		out.y = cy + sy * safePlace;
		out.z = cz + sz * safePlace;
		out.unit = (float) (2.0 * safePlace * halfFrame * SHARE * SAFE_SHARE / TYPICAL_LINE_UNITS);
	}
}
