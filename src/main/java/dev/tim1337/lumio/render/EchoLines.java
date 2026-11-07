package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * The lines of light that every Echo is made of, Radiance's along a swing and the trails' behind arrows and pearls: a thin core, near white and
 * tinted by the color, and a soft light to each side that fades out, added to the screen, so where lines cross they glow brighter. Sizes are in
 * screen pixels at 1080 lines, the same at every distance. A line costs three quads and no texture of its own, so a swing's many copies are cheap.
 */
public final class EchoLines {
	/** The core's width, and how far the soft light reaches to each side of it, in pixels of a 1080 line screen. */
	static final float CORE_PIXELS = 1.25f;
	static final float GLOW_PIXELS = 5f;
	/** How much white is in the core, against the color. */
	static final float CORE_WHITE = 0.65f;
	/** How bright the soft light is right next to the core, against the core. */
	static final float GLOW_LIGHT = 0.5f;
	/** How light the copies are against the rim, at their newest: many of them overlap, and they would wash out to white. */
	public static final float COPY_LIGHT = 0.45f;
	/** The faint sheet between two copies against their light: about a fifth of the rim at the newest end, a smear of motion. */
	static final float SHEET_LIGHT = 0.2f / COPY_LIGHT;
	/** Lines are pushed this much away from the camera, so that the item covers them where it is in front: only what is outside it shows. */
	private static final float PUSH = 1.004f;
	private static final float REFERENCE_LINES = 1080f;
	/** The field of view of the first person hand, which the game draws with its own, fixed one. */
	public static final float HAND_FOV_DEGREES = 70f;
	private static RenderType type;

	private EchoLines() {
	}

	/** @return how Echos are drawn: added to the screen, behind what is nearer */
	public static RenderType type() {
		if (type == null) {
			type = OutlineRenderTypes.light(Lumio.id("textures/white.png"));
		}
		return type;
	}

	/** How thick the oldest copies' lines are, against the newest. */
	private static final float OLDEST_THICKNESS = 0.6f;

	/** @return how light a copy of an Echo of this age is against the rim, 0 the newest to 1 the end of the echo: it falls quickly, to nothing */
	public static float ageLight(float age) {
		float left = 1f - Math.clamp(age, 0f, 1f);
		return COPY_LIGHT * left * left;
	}

	/** @return how thick the lines of a copy of this age are: older ones a little thinner */
	public static float ageThickness(float age) {
		float left = 1f - Math.clamp(age, 0f, 1f);
		return OLDEST_THICKNESS + (1f - OLDEST_THICKNESS) * left * left;
	}

	/** @return how large a pixel of a 1080 line screen is at a distance of 1, for a vertical field of view */
	public static float pixelAngle(float fovDegrees) {
		return (float) (2.0 * Math.tan(Math.toRadians(fovDegrees) / 2.0)) / REFERENCE_LINES;
	}

	/**
	 * One line of light from a to b, in a space whose origin is the camera.
	 *
	 * @param pixelAngle the size of a pixel at a distance of 1, see {@link #pixelAngle}
	 * @param thickness  how thick the line is, 1 for the full width
	 * @param light      how bright, from 0 to 1
	 */
	public static void line(VertexConsumer consumer, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float pixelAngle, float thickness,
		int rgb, float light) {
		if (light <= 0.002f) {
			return;
		}
		ax *= PUSH;
		ay *= PUSH;
		az *= PUSH;
		bx *= PUSH;
		by *= PUSH;
		bz *= PUSH;
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float mx = (ax + bx) * 0.5f;
		float my = (ay + by) * 0.5f;
		float mz = (az + bz) * 0.5f;
		// across the line and across the line of sight: the strip faces the camera
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float across = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (across < 1.0e-9f) {
			return;
		}
		float pixel = (float) Math.sqrt(mx * mx + my * my + mz * mz) * pixelAngle;
		float core = CORE_PIXELS * 0.5f * thickness * pixel / across;
		float glow = core + GLOW_PIXELS * (float) Math.sqrt(thickness) * pixel / across;
		int coreColor = premultiplied(mix(rgb, CORE_WHITE), light);
		int glowColor = premultiplied(rgb, light * GLOW_LIGHT);
		quad(consumer, pose, ax - sx * core, ay - sy * core, az - sz * core, bx - sx * core, by - sy * core, bz - sz * core,
			bx + sx * core, by + sy * core, bz + sz * core, ax + sx * core, ay + sy * core, az + sz * core, coreColor, coreColor);
		quad(consumer, pose, ax + sx * core, ay + sy * core, az + sz * core, bx + sx * core, by + sy * core, bz + sz * core,
			bx + sx * glow, by + sy * glow, bz + sz * glow, ax + sx * glow, ay + sy * glow, az + sz * glow, glowColor, 0);
		quad(consumer, pose, ax - sx * core, ay - sy * core, az - sz * core, bx - sx * core, by - sy * core, bz - sz * core,
			bx - sx * glow, by - sy * glow, bz - sz * glow, ax - sx * glow, ay - sy * glow, az - sz * glow, glowColor, 0);
	}

	/**
	 * A piece of the faint sheet between two copies: from the edge a to b of the newer one to the edge c to d of the older one, fading out with
	 * their light.
	 */
	public static void sheet(VertexConsumer consumer, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
		float dx, float dy, float dz, int rgb, float newer, float older) {
		if (newer <= 0.002f) {
			return;
		}
		quad(consumer, pose, ax * PUSH, ay * PUSH, az * PUSH, bx * PUSH, by * PUSH, bz * PUSH, cx * PUSH, cy * PUSH, cz * PUSH, dx * PUSH, dy * PUSH, dz * PUSH,
			premultiplied(rgb, newer * SHEET_LIGHT), premultiplied(rgb, older * SHEET_LIGHT));
	}

	/** Four corners; the first two get the first color, the last two the second, so the light fades across. */
	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
		float dx, float dy, float dz, int near, int far) {
		consumer.addVertex(pose, ax, ay, az).setUv(0.5f, 0.5f).setColor(near);
		consumer.addVertex(pose, bx, by, bz).setUv(0.5f, 0.5f).setColor(near);
		consumer.addVertex(pose, cx, cy, cz).setUv(0.5f, 0.5f).setColor(far);
		consumer.addVertex(pose, dx, dy, dz).setUv(0.5f, 0.5f).setColor(far);
	}

	/** @return the color with some white in it */
	private static int mix(int rgb, float white) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return Math.round(r + (255 - r) * white) << 16 | Math.round(g + (255 - g) * white) << 8 | Math.round(b + (255 - b) * white);
	}

	/** @return the color as light to add: each channel times the light, as the additive blend takes it */
	static int premultiplied(int rgb, float light) {
		float l = Math.clamp(light, 0f, 1f);
		return 0xFF000000 | Math.round((rgb >> 16 & 0xFF) * l) << 16 | Math.round((rgb >> 8 & 0xFF) * l) << 8 | Math.round((rgb & 0xFF) * l);
	}

	/**
	 * A thin line of light from one point to another, turned to the camera, which is at the origin of the space they are in: the plain lines the
	 * other trails draw with.
	 *
	 * @param textured whether the vertices have a texture coordinate, as the type they are drawn with needs
	 */
	public static void strip(VertexConsumer consumer, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float half, int argb, boolean textured) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float sx = dy * az - dz * ay;
		float sy = dz * ax - dx * az;
		float sz = dx * ay - dy * ax;
		float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (length < 1.0e-6f) {
			return;
		}
		sx *= half / length;
		sy *= half / length;
		sz *= half / length;
		corner(consumer, pose, ax + sx, ay + sy, az + sz, argb, textured);
		corner(consumer, pose, bx + sx, by + sy, bz + sz, argb, textured);
		corner(consumer, pose, bx - sx, by - sy, bz - sz, argb, textured);
		corner(consumer, pose, ax - sx, ay - sy, az - sz, argb, textured);
		if (!textured) {
			// the plain quads are culled: the back as well
			corner(consumer, pose, ax - sx, ay - sy, az - sz, argb, false);
			corner(consumer, pose, bx - sx, by - sy, bz - sz, argb, false);
			corner(consumer, pose, bx + sx, by + sy, bz + sz, argb, false);
			corner(consumer, pose, ax + sx, ay + sy, az + sz, argb, false);
		}
	}

	private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int argb, boolean textured) {
		if (textured) {
			consumer.addVertex(pose, x, y, z).setUv(0.5f, 0.5f).setColor(argb);
		} else {
			consumer.addVertex(pose, x, y, z).setColor(argb);
		}
	}
}
