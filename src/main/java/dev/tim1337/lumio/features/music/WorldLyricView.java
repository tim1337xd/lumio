package dev.tim1337.lumio.features.music;

/**
 * Where a direction from the eye lies on the screen, and back: the math the lyrics in the world are placed by. Screen places run from -1 to 1 across
 * and up the picture (0 is the crosshair). Plain math, tested without the game.
 */
final class WorldLyricView {
	private WorldLyricView() {
	}

	/**
	 * Writes where a direction from the eye lies on the screen into {@code out} (across, up).
	 *
	 * @param yaw      the camera's yaw and pitch as the game counts them, in degrees (pitch down is positive)
	 * @param halfFovY half the vertical field of view, in degrees
	 * @param aspect   the window's width over its height
	 * @return whether the direction is in front of the camera; behind it the place means nothing
	 */
	static boolean project(double dx, double dy, double dz, double yaw, double pitch, double halfFovY, double aspect, double[] out) {
		double sinYaw = Math.sin(Math.toRadians(yaw));
		double cosYaw = Math.cos(Math.toRadians(yaw));
		double sinPitch = Math.sin(Math.toRadians(pitch));
		double cosPitch = Math.cos(Math.toRadians(pitch));
		// forward, right, and up as right x forward
		double fx = -sinYaw * cosPitch;
		double fy = -sinPitch;
		double fz = cosYaw * cosPitch;
		double rx = -cosYaw;
		double rz = -sinYaw;
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double ahead = dx * fx + dy * fy + dz * fz;
		if (ahead <= 1e-6) {
			return false;
		}
		double tan = Math.tan(Math.toRadians(halfFovY));
		out[0] = (dx * rx + dz * rz) / ahead / (tan * aspect);
		out[1] = (dx * ux + dy * uy + dz * uz) / ahead / tan;
		return true;
	}

	/** Writes the direction from the eye of a place on the screen into {@code out}, as a vector of length one. */
	static void direction(double x, double y, double yaw, double pitch, double halfFovY, double aspect, double[] out) {
		double sinYaw = Math.sin(Math.toRadians(yaw));
		double cosYaw = Math.cos(Math.toRadians(yaw));
		double sinPitch = Math.sin(Math.toRadians(pitch));
		double cosPitch = Math.cos(Math.toRadians(pitch));
		double fx = -sinYaw * cosPitch;
		double fy = -sinPitch;
		double fz = cosYaw * cosPitch;
		double rx = -cosYaw;
		double rz = -sinYaw;
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double tan = Math.tan(Math.toRadians(halfFovY));
		double across = x * tan * aspect;
		double up = y * tan;
		double vx = fx + across * rx + up * ux;
		double vy = fy + up * uy;
		double vz = fz + across * rz + up * uz;
		double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
		out[0] = vx / length;
		out[1] = vy / length;
		out[2] = vz / length;
	}

	/** @return how much of the screen half a width or height of this many degrees takes, seen near the middle of the view */
	static double share(double halfDegrees, double halfFov) {
		return Math.tan(Math.toRadians(Math.min(89.0, halfDegrees))) / Math.tan(Math.toRadians(halfFov));
	}
}
