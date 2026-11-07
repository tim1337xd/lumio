package dev.tim1337.lumio.features.visual.world;

import java.util.Random;

/**
 * Shooting stars with real paths: each one flies in a straight line through the air at a distance of its own, near ones over the player, far ones down behind the hills, in fixed arrays. They are
 * kept relative to the place the sky is seen from, and shifted as it moves, so walking shows their depth and a warp does not move them. Plain, so its rules are unit tested.
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
		float angle = random.nextFloat() * (float) Math.PI * 2f;
		float heading = angle + (float) Math.PI + (random.nextFloat() - 0.5f) * 1.4f;
		float distance = 120f + random.nextFloat() * 260f;
		start((float) Math.cos(angle) * distance, height, (float) Math.sin(angle) * distance, (float) Math.cos(heading), -0.28f, (float) Math.sin(heading), speedFactor * (0.9f + random.nextFloat() * 0.5f),
			1.4f + random.nextFloat() * 1.2f, 2.4f + random.nextFloat() * 1.8f, 0.8f + random.nextFloat() * 0.3f);
	}

	/** A close one: lower and nearer than the others, so it looks bigger and brighter, with a longer trail. */
	private void close(float speedFactor) {
		float height = CLOSE_FROM + random.nextFloat() * (CLOSE_TO - CLOSE_FROM);
		float angle = random.nextFloat() * (float) Math.PI * 2f;
		float heading = angle + (float) Math.PI + (random.nextFloat() - 0.5f) * 1.2f;
		float distance = 60f + random.nextFloat() * 110f;
		if (start((float) Math.cos(angle) * distance, height, (float) Math.sin(angle) * distance, (float) Math.cos(heading), -0.22f, (float) Math.sin(heading), speedFactor * (0.9f + random.nextFloat() * 0.3f),
			1.2f + random.nextFloat() * 0.8f, 4.5f + random.nextFloat() * 2f, 1f)) {
			trail[count - 1] = CLOSE_TRAIL;
		}
	}

	/** A far one comes down steeply, and ends behind what stands on the horizon. */
	private void far(float speedFactor) {
		float distance = FAR_FROM + random.nextFloat() * (FAR_TO - FAR_FROM);
		float angle = random.nextFloat() * (float) Math.PI * 2f;
		float heading = angle + (float) Math.PI * 0.5f * (random.nextBoolean() ? 1f : -1f) + (random.nextFloat() - 0.5f);
		start((float) Math.cos(angle) * distance, 130f + random.nextFloat() * 170f, (float) Math.sin(angle) * distance, (float) Math.cos(heading), -0.75f, (float) Math.sin(heading), speedFactor * (0.7f + random.nextFloat() * 0.4f),
			1.6f + random.nextFloat() * 1.0f, 3f + random.nextFloat() * 2f, 0.55f + random.nextFloat() * 0.3f);
	}

	/** A close one: low, right over the player, bright and large. */
	private void flyBy(float speedFactor) {
		float angle = random.nextFloat() * (float) Math.PI * 2f;
		float along = FLY_BY_LENGTH * 0.5f;
		float sideX = -(float) Math.sin(angle) * FLY_BY_SIDE;
		float sideZ = (float) Math.cos(angle) * FLY_BY_SIDE;
		start(-(float) Math.cos(angle) * along + sideX, FLY_BY_HEIGHT + 30f, -(float) Math.sin(angle) * along + sideZ, (float) Math.cos(angle), -0.1f, (float) Math.sin(angle), speedFactor,
			FLY_BY_LENGTH / SPEED_BLOCKS_PER_SECOND / Math.max(0.2f, speedFactor), 0.9f, 1f);
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
		speed[i] = SPEED_BLOCKS_PER_SECOND * speedFactor;
		age[i] = 0f;
		life[i] = seconds;
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
