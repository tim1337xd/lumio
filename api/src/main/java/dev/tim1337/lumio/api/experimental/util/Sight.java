package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Whether the player sees an entity right now: inside the view on screen (in front, within the field of view up and down and, by the screen's
 * shape, left and right) and with nothing solid between the eyes and it. Highlights that show through walls use it, so they only light up
 * what the player can see (see the server rules in API.md).
 */
public final class Sight {
	/** Closer than this, an entity counts as in view whatever the direction: the player stands at it. */
	static final double NEAR_DISTANCE = 1.0;
	/** World up; another axis when the player looks straight up or down. */
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 NORTH = new Vec3(0.0, 0.0, -1.0);
	private static final double PARALLEL = 1.0e-6;

	private Sight() {
	}

	/** @return whether the player sees the entity now: in view first (cheap), then a line-of-sight raycast */
	public static boolean visible(Minecraft client, Entity entity) {
		if (client.player == null) {
			return false;
		}
		double aspect = client.getWindow().getWidth() / (double) Math.max(1, client.getWindow().getHeight());
		return inView(client.player.getEyePosition(), client.player.getViewVector(1f), entity.getBoundingBox().getCenter(), client.options.fov().get(),
			aspect) && client.player.hasLineOfSight(entity);
	}

	/**
	 * @param eye      where the player's eyes are
	 * @param look     the direction they look, of length 1
	 * @param target   the point looked for
	 * @param fov      the vertical field of view in degrees
	 * @param aspect   the screen's width divided by its height
	 * @return whether the point is inside the view: in front of the eyes, and on screen up and down and left and right
	 */
	static boolean inView(Vec3 eye, Vec3 look, Vec3 target, double fov, double aspect) {
		Vec3 to = target.subtract(eye);
		if (to.length() < NEAR_DISTANCE) {
			return true;
		}
		double ahead = to.dot(look);
		if (ahead <= 0.0) {
			return false;
		}
		Vec3 right = look.cross(UP);
		if (right.lengthSqr() < PARALLEL) {
			right = look.cross(NORTH);
		}
		right = right.normalize();
		Vec3 up = right.cross(look);
		double halfHeight = Math.tan(Math.toRadians(fov) / 2.0);
		return Math.abs(to.dot(up)) <= ahead * halfHeight && Math.abs(to.dot(right)) <= ahead * halfHeight * aspect;
	}
}
