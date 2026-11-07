package dev.tim1337.lumio.features.visual.world.cinematic;

import net.minecraft.util.Mth;

/** A camera pose: position, rotation in degrees and field of view. Mutable and reused, so the camera creates nothing per frame. */
final class CinePose {
	double x;
	double y;
	double z;
	float yaw;
	float pitch;
	float fov;

	void set(CinePose other) {
		x = other.x;
		y = other.y;
		z = other.z;
		yaw = other.yaw;
		pitch = other.pitch;
		fov = other.fov;
	}

	/** Points the pose from its position at a target. */
	void lookAt(double tx, double ty, double tz) {
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	/** Sets this pose between two others, angles along the shorter way round. */
	void blend(CinePose from, CinePose to, float t) {
		x = Mth.lerp(t, from.x, to.x);
		y = Mth.lerp(t, from.y, to.y);
		z = Mth.lerp(t, from.z, to.z);
		yaw = from.yaw + Mth.wrapDegrees(to.yaw - from.yaw) * t;
		pitch = Mth.lerp(t, from.pitch, to.pitch);
		fov = Mth.lerp(t, from.fov, to.fov);
	}

	double distanceSquared(double px, double py, double pz) {
		return (x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz);
	}
}
