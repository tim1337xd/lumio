package dev.tim1337.lumio.features.visual.world.cinematic;

/**
 * Whether a lyric line stays in the picture while the camera moves: its box (its real size with glow and adlibs, standing upright and facing where
 * it was placed) is projected through camera poses along the shot's planned path, and it has to stay inside the safe part of the picture (about the
 * inner 85 %, the letterbox bars left out), clear of the song card's corner and of the character, at every pose. The picture is measured from its
 * middle, from -1 to 1 across and from -1 (bottom) to 1 (top). Plain numbers, so it is unit tested with made-up camera paths.
 */
final class LyricFrame {
	/** The safe part of the picture, as a share of its half width and half height. */
	static final float SAFE = 0.85f;
	/** The song card's corner, lower left: it reaches up to here and right to here. */
	static final float CARD_RIGHT = -0.4f;
	static final float CARD_TOP = -0.4f;
	/** A point closer than this in front of the camera, in blocks, counts as behind it. */
	private static final double NEAR = 0.1;
	/** No room at all: outside, behind the camera, on the card or the character. */
	static final float OUTSIDE = -1f;

	/** The projected bounds of the last box measured, in picture units: left, bottom, right, top. Reused. */
	final float[] bounds = new float[4];

	/**
	 * Projects a point into the picture.
	 *
	 * @param aspect  the screen's width by its height
	 * @param picture the share of the screen's height the picture takes between the letterbox bars
	 * @param out     its place across and up, in picture units
	 * @return false when it is behind the camera
	 */
	static boolean project(CinePose pose, float aspect, float picture, double x, double y, double z, float[] out) {
		double yaw = Math.toRadians(pose.yaw);
		double pitch = Math.toRadians(pose.pitch);
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		// up is right x forward
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double dx = x - pose.x;
		double dy = y - pose.y;
		double dz = z - pose.z;
		double depth = dx * fx + dy * fy + dz * fz;
		if (depth < NEAR) {
			return false;
		}
		double tan = Math.tan(Math.toRadians(pose.fov) / 2.0);
		out[0] = (float) ((dx * rx + dz * rz) / (depth * tan * aspect));
		out[1] = (float) ((dx * ux + dy * uy + dz * uz) / (depth * tan) / picture);
		return true;
	}

	/** @return how far in front of the camera a point is, along its view, in blocks */
	static double depth(CinePose pose, double x, double y, double z) {
		double yaw = Math.toRadians(pose.yaw);
		double pitch = Math.toRadians(pose.pitch);
		return (x - pose.x) * -Math.sin(yaw) * Math.cos(pitch) + (y - pose.y) * -Math.sin(pitch) + (z - pose.z) * Math.cos(yaw) * Math.cos(pitch);
	}

	/**
	 * Finds the world point behind a place in the picture, at a depth in front of the camera: the opposite of {@link #project}.
	 *
	 * @param across its place across the picture, -1 (left) to 1 (right)
	 * @param up     its place up the picture, -1 (bottom) to 1 (top)
	 * @param spot   receives x, y, z and the camera's level right direction (x, z)
	 */
	static void unproject(CinePose pose, float aspect, float picture, float across, float up, double depth, double[] spot) {
		double yaw = Math.toRadians(pose.yaw);
		double pitch = Math.toRadians(pose.pitch);
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double tan = Math.tan(Math.toRadians(pose.fov) / 2.0);
		double sideways = across * depth * tan * aspect;
		double upward = up * depth * tan * picture;
		spot[0] = pose.x + fx * depth + rx * sideways + ux * upward;
		spot[1] = pose.y + fy * depth + uy * upward;
		spot[2] = pose.z + fz * depth + rz * sideways + uz * upward;
		spot[3] = rx;
		spot[4] = rz;
	}

	/** Where the character is, from the eye: feet and shoulders. */
	private static final double FEET_BELOW_EYE = 1.5;
	private static final double SHOULDER = 0.3;
	/** How far toward the picture's edge the character may be, and the least it has to cover of the picture's height or width. */
	private static final float CHARACTER_FRAME = 0.92f;
	private static final float LEAST_CHARACTER_SHARE = 0.05f;
	private static final ThreadLocal<float[]> POINT = ThreadLocal.withInitial(() -> new float[2]);

	/** @return whether the character is inside the picture from head to feet at this pose, and large enough to be seen */
	static boolean playerInFrame(CinePose pose, float aspect, double eyeX, double eyeY, double eyeZ) {
		float[] point = POINT.get();
		if (!project(pose, aspect, 1f, eyeX, eyeY + Shot.HEAD, eyeZ, point)) {
			return false;
		}
		float headX = point[0];
		float headY = point[1];
		if (!project(pose, aspect, 1f, eyeX, eyeY - FEET_BELOW_EYE, eyeZ, point)) {
			return false;
		}
		if (Math.abs(headX) > CHARACTER_FRAME || Math.abs(headY) > CHARACTER_FRAME || Math.abs(point[0]) > CHARACTER_FRAME || Math.abs(point[1]) > CHARACTER_FRAME) {
			return false;
		}
		// from above, the character is short in the picture: its width counts as well
		return Math.max(Math.abs(headX - point[0]), Math.abs(headY - point[1])) / 2f >= LEAST_CHARACTER_SHARE
			|| projectedWidth(pose, aspect, eyeX, eyeY, eyeZ, point) >= LEAST_CHARACTER_SHARE;
	}

	private static float projectedWidth(CinePose pose, float aspect, double eyeX, double eyeY, double eyeZ, float[] point) {
		double yaw = Math.toRadians(pose.yaw);
		double sideX = Math.cos(yaw) * SHOULDER;
		double sideZ = Math.sin(yaw) * SHOULDER;
		if (!project(pose, aspect, 1f, eyeX - sideX, eyeY, eyeZ - sideZ, point)) {
			return 0f;
		}
		float left = point[0];
		if (!project(pose, aspect, 1f, eyeX + sideX, eyeY, eyeZ + sideZ, point)) {
			return 0f;
		}
		return Math.abs(point[0] - left) / 2f;
	}

	/** @return whether the camera sees the character past the blocks: its head and body, and at least one shoulder (a fence post may hide the other) */
	static boolean playerSeen(net.minecraft.world.level.Level level, CinePose pose, double eyeX, double eyeY, double eyeZ) {
		if (!CineRays.clear(level, pose.x, pose.y, pose.z, eyeX, eyeY + Shot.HEAD, eyeZ) || !CineRays.clear(level, pose.x, pose.y, pose.z, eyeX, eyeY + Shot.BODY, eyeZ)) {
			return false;
		}
		double yaw = Math.toRadians(pose.yaw);
		double sideX = Math.cos(yaw) * SHOULDER;
		double sideZ = Math.sin(yaw) * SHOULDER;
		return CineRays.clear(level, pose.x, pose.y, pose.z, eyeX - sideX, eyeY + Shot.BODY, eyeZ - sideZ)
			|| CineRays.clear(level, pose.x, pose.y, pose.z, eyeX + sideX, eyeY + Shot.BODY, eyeZ + sideZ);
	}

	/**
	 * Measures a line's box at a camera pose:
 how far it stays inside the safe part of the picture, as a share of it.
	 *
	 * @param rightX     the line's direction to its right, level with the ground, of length 1
	 * @param halfWidth  half its width, in blocks
	 * @param halfHeight half its height, in blocks
	 * @return its least distance to the safe part's edges, or {@link #OUTSIDE} when it is behind the camera or on the song card
	 */
	float margin(CinePose pose, float aspect, float picture, double x, double y, double z, double rightX, double rightZ, double halfWidth,
				 double halfHeight, float[] scratch) {
		float left = Float.MAX_VALUE;
		float bottom = Float.MAX_VALUE;
		float right = -Float.MAX_VALUE;
		float top = -Float.MAX_VALUE;
		for (int corner = 0; corner < 4; corner++) {
			double side = (corner & 1) == 0 ? -halfWidth : halfWidth;
			double up = (corner & 2) == 0 ? -halfHeight : halfHeight;
			if (!project(pose, aspect, picture, x + rightX * side, y + up, z + rightZ * side, scratch)) {
				return OUTSIDE;
			}
			left = Math.min(left, scratch[0]);
			right = Math.max(right, scratch[0]);
			bottom = Math.min(bottom, scratch[1]);
			top = Math.max(top, scratch[1]);
		}
		bounds[0] = left;
		bounds[1] = bottom;
		bounds[2] = right;
		bounds[3] = top;
		if (left < CARD_RIGHT && bottom < CARD_TOP) {
			return OUTSIDE;
		}
		return Math.min(Math.min(SAFE + left, SAFE - right), Math.min(SAFE + bottom, SAFE - top));
	}

	/** @return whether the last box measured overlaps a rectangle in picture units (the character's) */
	boolean overlaps(float left, float bottom, float right, float top) {
		return bounds[0] < right && bounds[2] > left && bounds[1] < top && bounds[3] > bottom;
	}
}
