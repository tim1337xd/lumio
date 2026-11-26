package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.features.music.WorldLyricOpenSky;

/**
 * The open sky of a shot, for its lyric: when the shot begins, the same coarse grid the lyrics use outside the film ({@link WorldLyricOpenSky}) is
 * laid over the picture of the shot's start and looked at from there, and every cell's far point is looked at again from where the shot's move
 * ends. A cell is free when nothing stands in its direction up to and well past that point in both views, and the point is in the safe part of
 * both pictures, off the song card and off the character. The lyric stands far away in the largest free area, fixed for the whole shot. Where the two
 * views share no free area of some size, the first view's own is taken, as fixed; without even that the shot keeps its own lyric spot. Works on a {@link CineLyricSpot.Space} of ray tests, so it is unit tested without a
 * world.
 */
final class CineOpenArea {
	/** How far from the shot's first view the lyric stands, and how far past it the view must stay open, in blocks. */
	static final double DISTANCE = 32.0;
	static final double BEYOND = 16.0;
	/** A free area this small is no room for a line. */
	static final int LEAST_COLUMNS = 3;
	static final int LEAST_ROWS = 2;
	private static final int COLUMNS = WorldLyricOpenSky.COLUMNS;
	private static final int ROWS = WorldLyricOpenSky.ROWS;
	private static final double SLACK = 1e-3;

	private final boolean[] open = new boolean[COLUMNS * ROWS];
	private final int[] rect = new int[4];
	private final double[] spot = new double[5];
	private final float[] point = new float[2];
	private final float[] startBody = new float[4];
	private final float[] endBody = new float[4];
	private boolean startBodySeen;
	private boolean endBodySeen;

	/** The area found: its middle in the world, and half its width and height there, in blocks, as seen from the shot's first view. */
	double x;
	double y;
	double z;
	double halfWidth;
	double halfHeight;
	/** How many cells were free, for the log. */
	int freeCells;
	/** Whether the area found is free in the first view only: the two views shared none. */
	boolean startOnly;

	/**
	 * Looks for the free area of a shot.
	 *
	 * @param start   the camera where the shot begins
	 * @param end     the camera where its move ends
	 * @param picture the share of the screen's height the picture takes between the letterbox bars
	 * @return whether there is one; it is read from the fields
	 */
	boolean find(CineLyricSpot.Space space, CinePose start, CinePose end, float aspect, float picture, double eyeX, double eyeY, double eyeZ) {
		startBodySeen = LyricFrame.characterBox(start, aspect, picture, eyeX, eyeY, eyeZ, startBody, point);
		endBodySeen = LyricFrame.characterBox(end, aspect, picture, eyeX, eyeY, eyeZ, endBody, point);
		// free in both views; where they share none, free in the first view alone, kept for the whole shot all the same
		startOnly = false;
		if (!largestFree(space, start, end, aspect, picture)) {
			startOnly = true;
			if (!largestFree(space, start, end, aspect, picture)) {
				return false;
			}
		}
		double left = -1.0 + rect[0] * 2.0 / COLUMNS;
		double right = -1.0 + (rect[2] + 1) * 2.0 / COLUMNS;
		double top = 1.0 - rect[1] * 2.0 / ROWS;
		double bottom = 1.0 - (rect[3] + 1) * 2.0 / ROWS;
		LyricFrame.unproject(start, aspect, picture, (float) ((left + right) / 2.0), (float) ((top + bottom) / 2.0), DISTANCE, spot);
		x = spot[0];
		y = spot[1];
		z = spot[2];
		double tan = Math.tan(Math.toRadians(start.fov) / 2.0);
		halfWidth = (right - left) / 2.0 * DISTANCE * tan * aspect;
		halfHeight = (top - bottom) / 2.0 * DISTANCE * tan * picture;
		return true;
	}

	/** @return whether the cells free (in both views, or the first alone with {@link #startOnly}) make an area of some size; it is in {@code rect} */
	private boolean largestFree(CineLyricSpot.Space space, CinePose start, CinePose end, float aspect, float picture) {
		freeCells = 0;
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLUMNS; c++) {
				boolean free = free(space, start, end, aspect, picture, (float) WorldLyricOpenSky.screenX(c), (float) WorldLyricOpenSky.screenY(r));
				open[r * COLUMNS + c] = free;
				if (free) {
					freeCells++;
				}
			}
		}
		return WorldLyricOpenSky.largest(open, COLUMNS, ROWS, rect) && rect[2] - rect[0] + 1 >= LEAST_COLUMNS && rect[3] - rect[1] + 1 >= LEAST_ROWS;
	}

	/** @return whether a cell of the first picture, at its middle, is free in both views */
	private boolean free(CineLyricSpot.Space space, CinePose start, CinePose end, float aspect, float picture, float across, float up) {
		if (!inPicture(across, up) || startBodySeen && onBody(startBody, across, up)) {
			return false;
		}
		LyricFrame.unproject(start, aspect, picture, across, up, DISTANCE, spot);
		double px = spot[0];
		double py = spot[1];
		double pz = spot[2];
		if (!openTo(space, start, px, py, pz)) {
			return false;
		}
		if (startOnly) {
			return true;
		}
		if (!LyricFrame.project(end, aspect, picture, px, py, pz, point) || !inPicture(point[0], point[1]) || endBodySeen && onBody(endBody, point[0], point[1])) {
			return false;
		}
		return openTo(space, end, px, py, pz);
	}

	/** @return whether nothing stands between the camera and the point, nor for a while past it */
	private static boolean openTo(CineLyricSpot.Space space, CinePose from, double px, double py, double pz) {
		double dx = px - from.x;
		double dy = py - from.y;
		double dz = pz - from.z;
		double far = Math.sqrt(dx * dx + dy * dy + dz * dz) + BEYOND;
		return space.reach(from.x, from.y, from.z, dx, dy, dz, far) >= far - SLACK;
	}

	/** @return whether a place is in the safe part of the picture and off the song card */
	private static boolean inPicture(float across, float up) {
		return Math.abs(across) <= LyricFrame.SAFE && Math.abs(up) <= LyricFrame.SAFE && !(across < LyricFrame.CARD_RIGHT && up < LyricFrame.CARD_TOP);
	}

	/** @return whether a place is on the character's box, widened by a cell */
	private static boolean onBody(float[] body, float across, float up) {
		float padX = 2f / COLUMNS;
		float padY = 2f / ROWS;
		return across > body[0] - padX && across < body[2] + padX && up > body[1] - padY && up < body[3] + padY;
	}
}
