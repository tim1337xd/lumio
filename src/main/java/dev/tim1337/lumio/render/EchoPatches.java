package dev.tim1337.lumio.render;

/**
 * Where Radiance's Echo glows: in a few soft patches along the item, not all around it, like a flame. The patches come from a smooth noise over the
 * screen, anchored to the item so they move with it, drifting through time; they lean to the side away from the recent motion and a little to the
 * bottom. The shader (echo_glow_source) masks the glow's source with it before the blurs, so the patches' edges stay soft.
 * <p>
 * The same noise is worked out here at points along the item's outline, to keep about a third of the outline glowing: when less glows the
 * threshold sinks slowly, when more glows it rises, so it is never all of it and never none. The numbers here and in the shader must match.
 */
public final class EchoPatches {
	/** The noise's scale: per pixel of a 1080 line screen, at the width 1. */
	static final float SCALE = 0.006f;
	/** How fast the noise moves through time, in cells a second, and after how many cells it comes round again (seamless). */
	static final float CELLS_PER_SECOND = 0.45f;
	static final int TIME_CELLS = 32;
	static final double PERIOD_SECONDS = TIME_CELLS / CELLS_PER_SECOND;
	/** The soft threshold of a patch; a point counts as glowing from its middle on. */
	static final float LOW = 0.45f;
	static final float HIGH = 0.70f;
	/** How much the side away from the motion and the bottom are favored. */
	static final float AWAY_BIAS = 0.15f;
	static final float BOTTOM_BIAS = 0.05f;
	/** A point counts as glowing only from this much of the mask on: the soft edge of a patch is blurred away and is hardly seen. */
	static final float SEEN = 0.65f;
	/** The share of the outline that should glow, how fast the threshold follows (per second), and how far it may move. */
	private static final float LEAST_COVER = 0.30f;
	private static final float MOST_COVER = 0.45f;
	private static final float SCARCE_COVER = 0.20f;
	private static final float FOLLOW_PER_SECOND = 0.6f;
	private static final float LOWEST_SHIFT = -0.2f;
	private static final float HIGHEST_SHIFT = 0.35f;
	/** At most this many points of the outline are looked at. */
	public static final int MOST_POINTS = 48;

	/** Points of the outline, relative to the anchor, in pixels of a 1080 line screen: x, y. */
	private static final float[] points = new float[MOST_POINTS * 2];
	private static int pointCount;
	/** How far the threshold is lowered now (negative: raised). */
	private static float shift;

	private EchoPatches() {
	}

	/** Notes points of the item's outline, relative to its anchor, in pixels of a 1080 line screen; kept until new ones come. */
	/** @return whether points of an outline were noted */
	public static boolean hasOutline() {
		return pointCount > 0;
	}

	public static void outline(float[] relative, int count) {
		pointCount = Math.min(count, MOST_POINTS);
		System.arraycopy(relative, 0, points, 0, pointCount * 2);
	}

	/** @return how far the threshold is lowered now, for the shader */
	static float shift() {
		return shift;
	}

	/**
	 * Moves the threshold after the share of the outline that glows now.
	 *
	 * @param seconds the frame's length
	 * @param phase   the noise's place in time, from 0 to 1 over {@link #PERIOD_SECONDS}
	 * @param leanX   the lean against the recent motion, as a direction with its strength (length up to 1); y up
	 * @param width   the width setting, which spreads the patches
	 */
	static void follow(double seconds, double phase, float leanX, float leanY, float width) {
		if (pointCount == 0) {
			return;
		}
		int glowing = 0;
		for (int i = 0; i < pointCount; i++) {
			if (mask(points[2 * i], points[2 * i + 1], phase, leanX, leanY, width, shift) >= SEEN) {
				glowing++;
			}
		}
		float cover = glowing / (float) pointCount;
		float step = (float) seconds * FOLLOW_PER_SECOND;
		if (cover < LEAST_COVER) {
			// too little: the threshold sinks, faster when almost nothing glows
			shift += cover < SCARCE_COVER ? 3f * step : step;
		} else if (cover > MOST_COVER) {
			shift -= step;
		}
		shift = Math.clamp(shift, LOWEST_SHIFT, HIGHEST_SHIFT);
	}

	/** @return how much a point glows, from 0 to 1, as the shader works it out */
	static float mask(float x, float y, double phase, float leanX, float leanY, float width, float lowered) {
		float scale = SCALE / Math.max(0.25f, width);
		float n = noise(x * scale, y * scale, (float) (phase * TIME_CELLS));
		float length = (float) Math.sqrt(x * x + y * y);
		if (length > 1e-3f) {
			float dx = x / length;
			float dy = y / length;
			n += AWAY_BIAS * Math.max(0f, dx * leanX + dy * leanY) + BOTTOM_BIAS * Math.max(0f, -dy);
		}
		return smoothstep(LOW - lowered, HIGH - lowered, n);
	}

	/** Smooth value noise over the screen and time, coming round in time after {@link #TIME_CELLS} cells; as noise3 in the shader. */
	static float noise(float x, float y, float z) {
		float ix = (float) Math.floor(x);
		float iy = (float) Math.floor(y);
		float iz = (float) Math.floor(z);
		float fx = x - ix;
		float fy = y - iy;
		float fz = z - iz;
		float ux = fx * fx * (3f - 2f * fx);
		float uy = fy * fy * (3f - 2f * fy);
		float uz = fz * fz * (3f - 2f * fz);
		float z0 = wrap(iz);
		float z1 = wrap(iz + 1f);
		float a = lerp(lerp(hash(ix, iy, z0), hash(ix + 1f, iy, z0), ux), lerp(hash(ix, iy + 1f, z0), hash(ix + 1f, iy + 1f, z0), ux), uy);
		float b = lerp(lerp(hash(ix, iy, z1), hash(ix + 1f, iy, z1), ux), lerp(hash(ix, iy + 1f, z1), hash(ix + 1f, iy + 1f, z1), ux), uy);
		return lerp(a, b, uz);
	}

	private static float wrap(float cell) {
		return cell - TIME_CELLS * (float) Math.floor(cell / TIME_CELLS);
	}

	/** A number from 0 to 1 for a cell, from plain arithmetic (as hash13 in the shader), so it is the same on the graphics card. */
	static float hash(float x, float y, float z) {
		float px = fract(x * 0.1031f);
		float py = fract(y * 0.1031f);
		float pz = fract(z * 0.1031f);
		float d = px * (pz + 31.32f) + py * (py + 31.32f) + pz * (px + 31.32f);
		px += d;
		py += d;
		pz += d;
		return fract((px + py) * pz);
	}

	private static float fract(float value) {
		return value - (float) Math.floor(value);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
