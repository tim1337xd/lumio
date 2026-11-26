package dev.tim1337.lumio.features.visual.world;

import java.util.Random;

/**
 * Shooting stars with real paths: each one flies in a straight line through the air at a distance of its own, near ones over the player, far ones down behind the hills, in fixed arrays. They are
 * kept relative to the place the sky is seen from, and shifted as it moves, so walking shows their depth and a warp does not move them. All fly the same way, as the stars of one shower do: one
 * diagonal through the world ({@link #HEADING}, {@link #DESCENT}), each a few degrees off it so they do not look copied. Plain, so its rules are unit tested.
 */
public final class SkyMeteors {
	/** The most at once. */
	public static final int CAP = 24;
	private static final float NEAR_FROM = 180f;
	private static final float NEAR_TO = 340f;
	private static final float FAR_FROM = 420f;
	private static final float FAR_TO = 640f;
	private static final float FLY_BY_HEIGHT = 40f;
	private static final float FLY_BY_SIDE = 26f;
	private static final float FLY_BY_LENGTH = 300f;
	private static final float SPEED_BLOCKS_PER_SECOND = 170f;
	private static final float MOST_PER_SECOND = 8f;
	private static final float FLY_BY_EVERY_SECONDS = 45f;
	private static final float FAR_SHARE = 0.45f;
	/** Some come closer: lower, bigger and brighter, with a longer trail. */
	private static final float CLOSE_SHARE = 0.15f;
	private static final float CLOSE_FROM = 90f;
	private static final float CLOSE_TO = 150f;
	private static final float CLOSE_TRAIL = 1.8f;
	private static final float FADE_SECONDS = 0.25f;
	/** The way they all fly: this heading across the world (radians from +x toward +z), going down this much for each block across. */
	static final float HEADING = 0.62f;
	static final float DESCENT = 0.36f;
	/** How far one may be off that way: across, in radians (about 4 degrees), and in its descent. */
	static final float HEADING_SPREAD = 0.07f;
	static final float DESCENT_SPREAD = 0.04f;
	/**
	 * Their pace against the speed they flew at before (2026-11-15: 60 %). They live longer by as much, so each still crosses the same stretch of sky,
	 * and the drawing keeps the streak as long as before ({@link #streakSeconds}).
	 */
	public static final float PACE = 0.6f;

	public final float[] x = new float[CAP];
	public final float[] y = new float[CAP];
	public final float[] z = new float[CAP];
	public final float[] dx = new float[CAP];
	public final float[] dy = new float[CAP];
	public final float[] dz = new float[CAP];
	public final float[] speed = new float[CAP];
	public final float[] age = new float[CAP];
	public final float[] life = new float[CAP];
	public final float[] size = new float[CAP];
	public final float[] light = new float[CAP];
	public final float[] hue = new float[CAP];
	/** How long the trail is against the others: the close ones draw a longer one. */
	public final float[] trail = new float[CAP];
	private final Random random = new Random();
	private int count;
	private float spawnDue;
	private float flyByIn = FLY_BY_EVERY_SECONDS / 3f;

	public int count() {
		return count;
	}

	/** The place they are kept around has moved: they stay where they are in the world, and so walking shows how far off they are. */
	public void shift(float byX, float byY, float byZ) {
		for (int i = 0; i < count; i++) {
			x[i] -= byX;
			y[i] -= byY;
			z[i] -= byZ;
		}
	}

	/** @return how much of a meteor shows: it comes and goes softly, and the part of its tail still to come is left to the drawing */
	public float alpha(int i) {
		float share = Math.min(age[i], life[i] - age[i]) / FADE_SECONDS;
		return light[i] * Math.max(0f, Math.min(1f, share));
	}

	/**
	 * Moves them on and lets new ones start.
	 *
	 * @param amount how many, from 0 to 1
	 * @param speedFactor how fast relative to the normal speed
	 * @param active whether new ones start; those flying finish their way
	 * @param flyBys whether one now and then passes right overhead
	 */
	public void update(float seconds, float amount, float speedFactor, boolean active, boolean flyBys) {
		for (int i = count - 1; i >= 0; i--) {
			age[i] += seconds;
			if (age[i] >= life[i]) {
				remove(i);
				continue;
			}
			x[i] += dx[i] * speed[i] * seconds;
			y[i] += dy[i] * speed[i] * seconds;
			z[i] += dz[i] * speed[i] * seconds;
		}
		if (!active) {
			return;
		}
		spawnDue += seconds * MOST_PER_SECOND * amount;
		while (spawnDue >= 1f) {
			spawnDue -= 1f;
			float kind = random.nextFloat();
			if (kind < CLOSE_SHARE) {
				close(speedFactor);
			} else if (kind < CLOSE_SHARE + FAR_SHARE) {
				far(speedFactor);
			} else {
				near(speedFactor);
			}
		}
		flyByIn -= seconds;
		if (flyByIn <= 0f) {
			flyByIn = FLY_BY_EVERY_SECONDS * (0.6f + random.nextFloat() * 0.8f);
			if (flyBys) {
				flyBy(speedFactor);
			}
		}
	}

	/** A near one passes over the player, on the way across the sky. */
	private void near(float speedFactor) {
		float height = NEAR_FROM + random.nextFloat() * (NEAR_TO - NEAR_FROM);
		along(80f + random.nextFloat() * 180f, (random.nextFloat() * 2f - 1f) * 260f, height, speedFactor * (0.9f + random.nextFloat() * 0.5f),
			1.4f + random.nextFloat() * 1.2f, 2.4f + random.nextFloat() * 1.8f, 0.8f + random.nextFloat() * 0.3f);
	}

	/** A close one: lower and nearer than the others, so it looks bigger and brighter, with a longer trail. */
	private void close(float speedFactor) {
		float height = CLOSE_FROM + random.nextFloat() * (CLOSE_TO - CLOSE_FROM);
		if (along(40f + random.nextFloat() * 90f, (random.nextFloat() * 2f - 1f) * 130f, height, speedFactor * (0.9f + random.nextFloat() * 0.3f),
			1.2f + random.nextFloat() * 0.8f, 4.5f + random.nextFloat() * 2f, 1f)) {
			trail[count - 1] = CLOSE_TRAIL;
		}
	}

	/** A far one crosses low over the horizon, and ends behind what stands there. */
	private void far(float speedFactor) {
		float distance = FAR_FROM + random.nextFloat() * (FAR_TO - FAR_FROM);
		float angle = random.nextFloat() * (float) Math.PI * 2f;
		float[] way = way();
		start((float) Math.cos(angle) * distance, 130f + random.nextFloat() * 170f, (float) Math.sin(angle) * distance, way[0], way[1], way[2],
			speedFactor * (0.7f + random.nextFloat() * 0.4f), 1.6f + random.nextFloat() * 1.0f, 3f + random.nextFloat() * 2f, 0.55f + random.nextFloat() * 0.3f);
	}

	/** A close one: low, right over the player, bright and large. */
	private void flyBy(float speedFactor) {
		float[] way = way();
		float along = FLY_BY_LENGTH * 0.5f;
		float side = (random.nextFloat() * 2f - 1f) * FLY_BY_SIDE;
		start(-way[0] * along - way[2] * side, FLY_BY_HEIGHT + 30f + way[1] * -along * 0.3f, -way[2] * along + way[0] * side, way[0], way[1] * 0.3f, way[2], speedFactor,
			FLY_BY_LENGTH / SPEED_BLOCKS_PER_SECOND / Math.max(0.2f, speedFactor), 0.9f, 1f);
	}

	/**
	 * Starts one that crosses the sky on the shared way: from a point this far back along it and this far to its side of the player, at a height.
	 *
	 * @return whether it was started
	 */
	private boolean along(float back, float side, float height, float speedFactor, float seconds, float sizeBlocks, float brightness) {
		float[] way = way();
		return start(-way[0] * back - way[2] * side, height, -way[2] * back + way[0] * side, way[0], way[1], way[2], speedFactor, seconds, sizeBlocks, brightness);
	}

	/** @return the shared way, a few degrees off for this one: across x, down, across z (not yet of length 1) */
	private float[] way() {
		float heading = HEADING + (random.nextFloat() * 2f - 1f) * HEADING_SPREAD;
		float descent = DESCENT + (random.nextFloat() * 2f - 1f) * DESCENT_SPREAD;
		return new float[] {(float) Math.cos(heading), -descent, (float) Math.sin(heading)};
	}

	/** @return how many seconds of its flight a streak shows: as long a streak as before they were slowed ({@link #PACE}) */
	public static float streakSeconds(float tailSeconds) {
		return tailSeconds / PACE;
	}

	/** @return whether it was started: not when there are as many as there may be */
	private boolean start(float px, float py, float pz, float hx, float hy, float hz, float speedFactor, float seconds, float sizeBlocks, float brightness) {
		if (count >= CAP) {
			return false;
		}
		int i = count++;
		float length = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
		x[i] = px;
		y[i] = py;
		z[i] = pz;
		dx[i] = hx / length;
		dy[i] = hy / length;
		dz[i] = hz / length;
		speed[i] = SPEED_BLOCKS_PER_SECOND * speedFactor * PACE;
		age[i] = 0f;
		life[i] = seconds / PACE;
		size[i] = sizeBlocks;
		light[i] = brightness;
		trail[i] = 1f;
		hue[i] = random.nextFloat();
		return true;
	}

	private void remove(int i) {
		int last = --count;
		x[i] = x[last];
		y[i] = y[last];
		z[i] = z[last];
		dx[i] = dx[last];
		dy[i] = dy[last];
		dz[i] = dz[last];
		speed[i] = speed[last];
		age[i] = age[last];
		life[i] = life[last];
		size[i] = size[last];
		light[i] = light[last];
		hue[i] = hue[last];
		trail[i] = trail[last];
	}
}
