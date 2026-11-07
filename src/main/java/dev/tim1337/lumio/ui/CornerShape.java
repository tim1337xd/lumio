package dev.tim1337.lumio.ui;

import net.minecraft.util.Mth;

/**
 * The one corner shape of the mod, for what is drawn from textures (the same rule as {@code shaders/include/corners.glsl} for what the shaders
 * draw): continuous corners, a superellipse instead of a quarter circle, so the side flows into the corner without the kink a circle has.
 * <ul>
 * <li>With room on the sides the corner reaches a little further than a circle of its radius and is a superellipse of exponent 4: it covers
 * about the same area as the circle, so it looks the same size.</li>
 * <li>With no room (the radius is half the shape, as on a pill, a dot or a knob) it is exactly the circle, so round shapes stay perfectly
 * round.</li>
 * <li>In between it blends, by how much room the sides have.</li>
 * </ul>
 * Textures made from it are cached per size and per step of the blend, so it costs nothing per frame.
 */
public final class CornerShape {
	/** How much further a full continuous corner reaches than a circle of the same radius, as a share of the radius. */
	private static final float REACH = 0.305f;
	/** The blend is quantized to this many steps for cached textures. */
	private static final int STEPS = 8;

	private CornerShape() {
	}

	/** @return how continuous a corner of this radius is in a shape whose shorter half is {@code halfMin}: 0 a circle, 1 a full superellipse */
	public static float continuity(float radius, float halfMin) {
		return radius <= 0f ? 0f : Mth.clamp((halfMin - radius) / (REACH * radius), 0f, 1f);
	}

	/** @return the continuity quantized for a cached texture, from 0 to {@link #STEPS} */
	public static int step(float radius, float halfMin) {
		return Math.round(continuity(radius, halfMin) * STEPS);
	}

	/** @return how far from the shape's side a corner of a quantized continuity reaches: never more than the shorter half of the shape */
	public static int extent(int radius, int step) {
		return Math.round(radius * (1f + REACH * step / STEPS));
	}

	private static float exponent(int step) {
		return 2f + 2f * step / STEPS;
	}

	/**
	 * @return the signed distance to the edge of a box with continuous corners, from its middle, in pixels: less than 0 inside. {@code extent} is
	 *         how far its corners reach and {@code step} their quantized continuity.
	 */
	public static float box(float x, float y, float halfWidth, float halfHeight, int extent, int step) {
		float qx = Math.abs(x) - (halfWidth - extent);
		float qy = Math.abs(y) - (halfHeight - extent);
		if (extent > 0 && qx > 0f && qy > 0f) {
			float n = exponent(step);
			float ux = qx / extent;
			float uy = qy / extent;
			float f = (float) Math.pow(Math.pow(ux, n) + Math.pow(uy, n), 1.0 / n);
			float gx = (float) Math.pow(ux / f, n - 1f);
			float gy = (float) Math.pow(uy / f, n - 1f);
			float length = Math.max(Mth.sqrt(gx * gx + gy * gy), 1e-4f);
			return (f - 1f) * extent / length;
		}
		return Math.max(qx, qy) - extent;
	}
}
