package dev.tim1337.lumio.features.visual.world.cinematic;

/**
 * Where a lyric line stands during a push-in, so it stays in the picture while the picture narrows toward the player. Spots are taken from the
 * picture at the end of the line's time (the narrowest it gets): in its inner third, on the side away from the player, starting just clear of
 * the player. A line that fits nowhere may be up to a quarter smaller (decided once, when it spawns); if it still does not fit, the rest of the
 * push-in is shortened until it does. The line itself never moves. Plain numbers and a check passed in, so it is unit tested with a mocked path.
 */
final class PushInSpots {
	/** Measures a spot along a camera path: its least margin to the safe part of the picture, negative when it reaches out or lands on something. */
	@FunctionalInterface
	interface Check {
		float margin(Shot path, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight);
	}

	/** The sizes tried, the line's own first: at most a quarter smaller. */
	static final float[] SIZES = {1f, 0.875f, 0.75f};
	/** The shares of the rest of the push-in tried when no spot fits: a little shorter first, at last none. */
	static final double[] SHORTER = {0.75, 0.5, 0.25, 0.0};
	/** Places up the picture (-1 bottom, 1 top), within its inner third, and how far beyond the player's side the line's near edge starts, in
	 * picture units. */
	private static final float[] UP = {0.2f, 0.05f, 0.3f};
	private static final float[] CLEAR = {0.06f, 0.14f, 0.24f};
	/** The inner third of the picture: a line's middle stays within it. */
	private static final float INNER = 1f / 3f;
	/** How far in front of the camera the spots are, as a share of the player's distance. */
	private static final double[] DEPTHS = {1.0, 1.2};
	/** The share of the picture's half width half a line may take at the least deep spot of each depth step. */
	private static final double[] FIT_SHARES = {0.3, 0.22};
	/** How far above the player's feet the middle of their body is, in blocks: the point whose side of the picture is avoided. */
	private static final double BODY_MIDDLE = 0.9;
	/** Half the player's width, in blocks. */
	private static final double BODY_HALF = 0.35;

	/** Scratch for the search, reused. */
	private final Shot trial = new Shot();
	private final CinePose end = new CinePose();
	private final float[] projected = new float[2];
	private final double[] spot = new double[5];

	/**
	 * Finds a spot for a line in a push-in.
	 *
	 * @param now        seconds into the shot
	 * @param seconds    how long the line shows
	 * @param halfWidth  half the line's width at its own size, in blocks
	 * @param halfHeight half its height at its own size, in blocks
	 * @param out        receives x, y, z (the line's middle), its right direction (x, z), its size (1 its own) and the share of the rest of the
	 *                   push-in to keep (1 unchanged)
	 * @return whether a spot keeps the line fully in the picture
	 */
	boolean find(Shot shot, float now, float seconds, double focusX, double focusY, double focusZ, float aspect, float picture, double halfWidth,
				 double halfHeight, Check check, double[] out) {
		if (tryAll(shot, now, seconds, focusX, focusY, focusZ, aspect, picture, halfWidth, halfHeight, check, out)) {
			out[6] = 1.0;
			return true;
		}
		double movedNow = CineMotion.progress(now) * shot.move;
		for (double share : SHORTER) {
			trial.copy(shot);
			trial.shortenPush(movedNow, share);
			if (tryAll(trial, now, seconds, focusX, focusY, focusZ, aspect, picture, halfWidth, halfHeight, check, out)) {
				out[6] = share;
				return true;
			}
		}
		return false;
	}

	private boolean tryAll(Shot path, float now, float seconds, double focusX, double focusY, double focusZ, float aspect, float picture,
						   double halfWidth, double halfHeight, Check check, double[] out) {
		// the narrowest picture the line sees: at the end of its time
		path.pose(now + seconds, focusX, focusY, focusZ, aspect, end);
		end.pitch = Math.max(Shot.MOST_UP, end.pitch);
		boolean projectedPlayer = LyricFrame.project(end, aspect, picture, focusX, focusY + BODY_MIDDLE, focusZ, projected);
		float playerAcross = projectedPlayer ? projected[0] : 0f;
		float away = playerAcross > 0f ? -1f : 1f;
		double depth = Math.max(1.0, LyricFrame.depth(end, focusX, focusY + BODY_MIDDLE, focusZ));
		double tan = Math.tan(Math.toRadians(end.fov) / 2.0);
		// the player's side edge in the picture: the line starts beyond it
		float playerEdge = playerAcross + away * (float) (BODY_HALF / (depth * tan * aspect));
		for (float size : SIZES) {
			double width = halfWidth * size;
			double height = halfHeight * size;
			for (int d = 0; d < DEPTHS.length; d++) {
				// at the player's depth or a little beyond, but at least so deep that the line takes only part of the picture's width there: a
				// lyric line is wider than the whole picture at the player in a close shot
				double at = Math.max(depth * DEPTHS[d], width / (FIT_SHARES[d] * tan * aspect));
				// half the line's width in picture units there, so its near edge starts just clear of the player
				float halfAcross = (float) (width / (at * tan * aspect));
				for (float clear : CLEAR) {
					// the middle of the line, in the inner third of the picture
					float across = Math.max(-INNER, Math.min(INNER, playerEdge + away * (clear + halfAcross)));
					for (float up : UP) {
						LyricFrame.unproject(end, aspect, picture, across, up, at, spot);
						if (check.margin(path, spot[0], spot[1], spot[2], spot[3], spot[4], width, height) >= 0f) {
							System.arraycopy(spot, 0, out, 0, spot.length);
							out[5] = size;
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
