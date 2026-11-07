package dev.tim1337.lumio.features.music;

/**
 * The geometry of the world lyric placement: rays through the block grid, the check that a line's whole box is free, and the check that the eye
 * sees all of it. A line stands at a distance in a direction from the eye, facing it; its box is its real size (text, rows, adlibs and glow) plus a
 * margin all around, front and back. All checks share a budget of block lookups per placement, so even a crowded spot costs a bounded time.
 */
final class WorldLyricSpace {
	/** The room kept free around a line's box, in blocks, to every side and in front of and behind it. */
	static final double MARGIN = 0.3;
	/** How far apart the box's samples are, in blocks, at most: a fence post or a pane between two samples is still found. */
	private static final double SAMPLE_STEP = 0.5;
	/** The most samples across and up a box takes; a larger box (a far sky line) is sampled more coarsely. */
	private static final int MOST_ACROSS = 25;
	private static final int MOST_UP = 9;
	/** The box is sampled in these planes along the view: in front of the line, at it and behind it. */
	private static final double[] DEPTHS = {-MARGIN, 0.0, MARGIN};
	/** The most block lookups one placement may do; after that every further spot counts as blocked. */
	static final int LOOKUP_BUDGET = 12000;
	/** A ray this close to square to an axis runs along it. */
	private static final double PARALLEL = 1e-12;

	private final WorldLyricBlocks blocks;
	/** The box of the cell looked at, and the part of a ray inside it. Reused. */
	private final double[] box = new double[WorldLyricBlocks.BOX_SIZE];
	private double spanNear;
	private double spanFar;
	private int budget = LOOKUP_BUDGET;
	/** The box's axes, set by {@link #axes}: the direction to it, and to its right and its top. Reused, nothing is allocated per check. */
	private double forwardX;
	private double forwardY;
	private double forwardZ;
	private double rightX;
	private double rightZ;
	private double upX;
	private double upY;
	private double upZ;

	WorldLyricSpace(WorldLyricBlocks blocks) {
		this.blocks = blocks;
	}

	/** Starts a new placement with a full lookup budget. */
	void resetBudget() {
		budget = LOOKUP_BUDGET;
	}

	/** @return whether the lookup budget of this placement is used up */
	boolean exhausted() {
		return budget <= 0;
	}

	/** @return whether a point lies inside what fills its cell (or the budget is used up) */
	private boolean occupied(double x, double y, double z) {
		int cellX = floor(x);
		int cellY = floor(y);
		int cellZ = floor(z);
		if (--budget < 0) {
			return true;
		}
		if (!blocks.shape(cellX, cellY, cellZ, box)) {
			return false;
		}
		double localX = x - cellX;
		double localY = y - cellY;
		double localZ = z - cellZ;
		return localX >= box[0] && localX <= box[3] && localY >= box[1] && localY <= box[4] && localZ >= box[2] && localZ <= box[5];
	}

	/**
	 * @return where a ray, inside a cell between two lengths along it, first meets what fills the cell, or -1 when it passes; the whole span when
	 *         the budget is used up
	 */
	private double meets(int cellX, int cellY, int cellZ, double x, double y, double z, double dx, double dy, double dz, double from, double to) {
		if (--budget < 0) {
			return from;
		}
		if (!blocks.shape(cellX, cellY, cellZ, box)) {
			return -1.0;
		}
		spanNear = from;
		spanFar = to;
		boolean meets = clip(x, dx, cellX + box[0], cellX + box[3]) && clip(y, dy, cellY + box[1], cellY + box[4])
			&& clip(z, dz, cellZ + box[2], cellZ + box[5]);
		return meets ? spanNear : -1.0;
	}

	/** Narrows the span of the ray to where it is between two values on one axis. @return whether some of it is left */
	private boolean clip(double origin, double direction, double min, double max) {
		if (Math.abs(direction) < PARALLEL) {
			return origin >= min && origin <= max;
		}
		double first = (min - origin) / direction;
		double second = (max - origin) / direction;
		spanNear = Math.max(spanNear, Math.min(first, second));
		spanFar = Math.min(spanFar, Math.max(first, second));
		return spanNear <= spanFar;
	}

	/**
	 * Walks a ray cell by cell from a point (the cell it starts in is skipped, it is the eye's).
	 *
	 * @param dx the ray's direction, of length 1
	 * @return how far it gets before it meets what fills a cell, or the full length
	 */
	double ray(double x, double y, double z, double dx, double dy, double dz, double length) {
		int cellX = floor(x);
		int cellY = floor(y);
		int cellZ = floor(z);
		int stepX = (int) Math.signum(dx);
		int stepY = (int) Math.signum(dy);
		int stepZ = (int) Math.signum(dz);
		double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
		double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
		double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
		double nextX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? cellX + 1 - x : x - cellX) * deltaX;
		double nextY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? cellY + 1 - y : y - cellY) * deltaY;
		double nextZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? cellZ + 1 - z : z - cellZ) * deltaZ;
		while (true) {
			double at;
			if (nextX <= nextY && nextX <= nextZ) {
				at = nextX;
				cellX += stepX;
				nextX += deltaX;
			} else if (nextY <= nextZ) {
				at = nextY;
				cellY += stepY;
				nextY += deltaY;
			} else {
				at = nextZ;
				cellZ += stepZ;
				nextZ += deltaZ;
			}
			if (at > length) {
				return length;
			}
			double leaves = Math.min(length, Math.min(nextX, Math.min(nextY, nextZ)));
			double met = meets(cellX, cellY, cellZ, x, y, z, dx, dy, dz, at, leaves);
			if (met >= 0.0) {
				return met;
			}
		}
	}

	/** @return how far a ray in a direction (yaw and elevation in degrees) gets, less the gap kept before what it hits */
	double ray(double eyeX, double eyeY, double eyeZ, float yaw, float elevation, double length, double gap) {
		double yawRad = Math.toRadians(yaw);
		double up = Math.toRadians(elevation);
		double horizontal = Math.cos(up);
		double reached = ray(eyeX, eyeY, eyeZ, -Math.sin(yawRad) * horizontal, Math.sin(up), Math.cos(yawRad) * horizontal, length);
		return reached >= length ? length : Math.max(0.0, reached - gap);
	}

	/**
	 * @return how far a line can stand in a direction, up to the wanted distance: rays to its middle, both ends, its top and its bottom must reach
	 *         it, and it stops a gap before anything they hit
	 */
	double openDistance(double eyeX, double eyeY, double eyeZ, float yaw, float elevation, float halfWidth, float halfHeight, double wanted,
						double gap) {
		double room = ray(eyeX, eyeY, eyeZ, yaw, elevation, wanted, gap);
		room = Math.min(room, ray(eyeX, eyeY, eyeZ, yaw - halfWidth, elevation, wanted, gap));
		room = Math.min(room, ray(eyeX, eyeY, eyeZ, yaw + halfWidth, elevation, wanted, gap));
		room = Math.min(room, ray(eyeX, eyeY, eyeZ, yaw, elevation + halfHeight, wanted, gap));
		return Math.min(room, ray(eyeX, eyeY, eyeZ, yaw, elevation - halfHeight, wanted, gap));
	}

	/**
	 * @return whether a line can stand there: its whole box, with the margin, is free of blocks, and rays from the eye reach its middle and
	 *         its four corners
	 * @param halfWidth  half the line's width as seen from the eye, glow included, in degrees
	 * @param halfHeight half its height, adlibs and glow included, in degrees
	 */
	boolean fits(double eyeX, double eyeY, double eyeZ, float yaw, float elevation, double distance, float halfWidth, float halfHeight) {
		axes(yaw, elevation);
		double halfAcross = distance * Math.tan(Math.toRadians(halfWidth));
		double halfUp = distance * Math.tan(Math.toRadians(halfHeight));
		return boxFree(eyeX, eyeY, eyeZ, distance, halfAcross + MARGIN, halfUp + MARGIN) && seen(eyeX, eyeY, eyeZ, distance, halfAcross, halfUp);
	}

	/** Sets the box's axes: forward from the eye, right level with the ground, and up square to both. */
	private void axes(float yaw, float elevation) {
		double yawRad = Math.toRadians(yaw);
		double up = Math.toRadians(elevation);
		double horizontal = Math.cos(up);
		forwardX = -Math.sin(yawRad) * horizontal;
		forwardY = Math.sin(up);
		forwardZ = Math.cos(yawRad) * horizontal;
		rightX = Math.cos(yawRad);
		rightZ = Math.sin(yawRad);
		// right × forward, both of length 1 and square to each other
		upX = -forwardY * rightZ;
		upY = rightZ * forwardX - rightX * forwardZ;
		upZ = rightX * forwardY;
	}

	/** @return whether no sample of the box (its half size with the margin) lies in what fills a block */
	private boolean boxFree(double eyeX, double eyeY, double eyeZ, double distance, double halfAcross, double halfUp) {
		int across = samples(halfAcross, MOST_ACROSS);
		int up = samples(halfUp, MOST_UP);
		for (double depth : DEPTHS) {
			double centerX = eyeX + forwardX * (distance + depth);
			double centerY = eyeY + forwardY * (distance + depth);
			double centerZ = eyeZ + forwardZ * (distance + depth);
			for (int i = 0; i < across; i++) {
				double side = (2.0 * i / (across - 1) - 1.0) * halfAcross;
				for (int j = 0; j < up; j++) {
					double height = (2.0 * j / (up - 1) - 1.0) * halfUp;
					if (occupied(centerX + rightX * side + upX * height, centerY + upY * height, centerZ + rightZ * side + upZ * height)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** @return how many samples a box side of this half size takes: at most a step apart, at least its ends and middle, at most {@code most} */
	private static int samples(double half, int most) {
		return Math.max(3, Math.min(most, (int) Math.ceil(2.0 * half / SAMPLE_STEP) + 1));
	}

	/** @return whether rays from the eye reach the line's middle and its four corners */
	private boolean seen(double eyeX, double eyeY, double eyeZ, double distance, double halfAcross, double halfUp) {
		double centerX = eyeX + forwardX * distance;
		double centerY = eyeY + forwardY * distance;
		double centerZ = eyeZ + forwardZ * distance;
		if (!reaches(eyeX, eyeY, eyeZ, centerX, centerY, centerZ)) {
			return false;
		}
		for (int corner = 0; corner < 4; corner++) {
			double side = (corner & 1) == 0 ? -halfAcross : halfAcross;
			double height = (corner & 2) == 0 ? -halfUp : halfUp;
			if (!reaches(eyeX, eyeY, eyeZ, centerX + rightX * side + upX * height, centerY + upY * height, centerZ + rightZ * side + upZ * height)) {
				return false;
			}
		}
		return true;
	}

	/** @return whether a ray from the eye gets to a point (to within the margin: the box around the point is already known to be free) */
	private boolean reaches(double eyeX, double eyeY, double eyeZ, double x, double y, double z) {
		double dx = x - eyeX;
		double dy = y - eyeY;
		double dz = z - eyeZ;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < MARGIN) {
			return true;
		}
		return ray(eyeX, eyeY, eyeZ, dx / length, dy / length, dz / length, length) >= length - MARGIN;
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}
}
