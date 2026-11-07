package dev.tim1337.lumio.features.music;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Finds the spot of a new world lyric line, once, when the line starts; the line then stays there. A spot counts only when the line's whole box
 * (its real size with rows, adlibs and glow, plus a margin) is free of blocks, leaves, glass, fences and water surfaces included, and the eye sees
 * its middle and corners (see {@link WorldLyricSpace}), so a line is never inside terrain or behind a wall. Lines stand to the left and the right
 * of the view, taking turns, and never in the band around the crosshair; a line may go below an older one on its side. A line never covers one
 * that is still visible, and lines never move once they stand. When one side is blocked the other is taken, when both are, a little farther
 * out; when nothing is free at the usual distance, the line comes closer and gets a little smaller; when nothing is free at all, it is left out
 * of the world (the info bar and the player still show it).
 * <ul>
 * <li>Around you: 5 to 10 blocks away near eye height, down to 2.5 blocks when the space is tight.</li>
 * <li>In the sky: 40 to 60 blocks away, clearly up in the sky (about 20 to 25 degrees above the horizon, whatever the view's pitch, so also when
 * looking straight up or down it is ahead and never overhead). Behind a mountain, a tree or a roof it is tried higher, then closer.</li>
 * </ul>
 * The Manual placement's distance is the most: a blocked line comes closer in its own direction before another direction is tried.
 */
final class WorldLyricPlacer {
	/** Distances of the two placements, in blocks, before the distance setting. */
	private static final double NEAR_MIN = 5.0;
	private static final double NEAR_MAX = 10.0;
	private static final double SKY_MIN = 40.0;
	private static final double SKY_MAX = 60.0;
	/** A line around you comes no closer than this when something is in the way; in the sky, no closer than this. */
	private static final double NEAR_CLOSEST = 2.5;
	private static final double SKY_CLOSEST = 20.0;
	/** A line counts as at its usual distance down to this share of the wanted distance; closer, it gets a little smaller. */
	private static final double USUAL_SHARE = 0.7;
	/** Coming closer, a line is tried this much nearer each time, around you and in the sky. */
	private static final double NEAR_CLOSER_STEP = 0.75;
	private static final double SKY_CLOSER_STEP = 5.0;
	/** At the closest distance a line is this much of its size. */
	private static final float CLOSEST_SHRINK = 0.85f;
	/** How far before an obstacle the line stops, in blocks. */
	private static final double OBSTACLE_GAP = 1.0;
	/**
	 * The band around the crosshair that stays free, to each side of it, and the room kept from the edges of the view, in degrees: lines stand
	 * to the left and the right, never above or below the crosshair.
	 */
	private static final float CENTER_BAND = 15f;
	private static final float EDGE_DEGREES = 3f;
	/** How far the spread setting moves lines further out to the side, in degrees at full spread. */
	private static final float SPREAD_SIDE = 14f;
	/** When both sides are blocked, the line is tried this much farther out, a few times. */
	private static final float FARTHER_DEGREES = 10f;
	private static final int FARTHER_TRIES = 3;
	private static final float SPREAD_JITTER = 4f;
	/**
	 * The sky, in degrees above the horizon: a line stands between the two first values, no lower than the lowest; the room between two lines, and
	 * how far a line leans back.
	 */
	private static final float SKY_FIRST_LOW = 20f;
	private static final float SKY_FIRST_HIGH = 24f;
	private static final float SKY_LOWEST = 14f;
	/** A sky line below another may go down to here, its bottom in degrees above the horizon. */
	private static final float SKY_STACK_FLOOR = 5f;
	private static final float SKY_HIGHEST = 60f;
	private static final float SKY_GAP = 3f;
	/** The two sides a line stands on, for the Auto placement to judge. */
	static final int LEFT = 0;
	static final int RIGHT = 1;
	static final int KINDS = 2;
	/** In the sky, a line hidden behind terrain is tried this much higher, a few times. */
	private static final float SKY_RAISE = 8f;
	private static final int SKY_TRIES = 3;
	/** At most this many visible lines are kept clear of. */
	static final int MOST_AVOIDED = 4;
	/** The most directions one placement tries: per side below the newest line and three heights, farther out, and higher in the sky. */
	private static final int MOST_CANDIDATES = 2 * (1 + 3) * (1 + SKY_TRIES) + FARTHER_TRIES * 2 * (1 + SKY_TRIES);

	/**
	 * A found spot: where the line's middle is, which way it faces (radians, about the up axis), how much it leans, its distance, and how much of
	 * its size it has there.
	 */
	static final class Spot {
		double x;
		double y;
		double z;
		float facing;
		double distance;
		float shrink = 1f;
		/** Whether a free spot was found; when none is, the line is left out of the world rather than shown inside blocks. */
		boolean open;
	}

	private final Random random;
	private final WorldLyricSpace space;
	private int lastKind = -1;
	/**
	 * The lines standing in the world as seen from the eye (direction and half size, in degrees), and which of them is the newest: a new line
	 * keeps clear of all of them, and may go below the newest when that stands on its side.
	 */
	private final float[] avoidYaw = new float[MOST_AVOIDED];
	private final float[] avoidElevation = new float[MOST_AVOIDED];
	private final float[] avoidHalfWidth = new float[MOST_AVOIDED];
	private final float[] avoidHalfHeight = new float[MOST_AVOIDED];
	private int avoided;
	private int newest = -1;
	/** The side the next line goes to, -1 left and 1 right, taking turns; and the side the Auto placement picked for it, or 0. */
	private float nextSide = -1f;
	private float preferredSide;
	/** The directions of one placement, in the order they are tried: yaw, elevation and side. Reused. */
	private final float[] candidateYaw = new float[MOST_CANDIDATES];
	private final float[] candidateElevation = new float[MOST_CANDIDATES];
	private final float[] candidateSide = new float[MOST_CANDIDATES];
	private int candidates;
	/** Half the view's size, in degrees. Reused. */
	private final float[] view = new float[2];

	/** A placer for the world the player is in. */
	WorldLyricPlacer() {
		this(WorldLyricBlocks.client(), new Random());
	}

	/** A placer for any blocks, with its own random source (for tests). */
	WorldLyricPlacer(WorldLyricBlocks blocks, Random random) {
		this.space = new WorldLyricSpace(blocks);
		this.random = random;
	}

	/** The next line tries this side first (the Auto placement's pick). */
	void preferNext(int kind) {
		preferredSide = kind == LEFT ? -1f : 1f;
	}

	/** @return the side of the last line, or -1 */
	int lastKind() {
		return lastKind;
	}

	/** Forgets the lines standing, before {@link #avoid} adds the ones still visible. */
	void clearAvoided() {
		avoided = 0;
		newest = -1;
	}

	/** A line still visible, which a new line must not cover; the newest of them is the one a new line goes below or across from. */
	void avoid(float yaw, float elevation, float halfWidth, float halfHeight, boolean isNewest) {
		if (avoided >= MOST_AVOIDED) {
			return;
		}
		avoidYaw[avoided] = yaw;
		avoidElevation[avoided] = elevation;
		avoidHalfWidth[avoided] = halfWidth;
		avoidHalfHeight[avoided] = halfHeight;
		if (isNewest) {
			newest = avoided;
		}
		avoided++;
	}

	/** @return the most half width, in degrees, a line can have and still fit on one side of the view, outside the band around the crosshair */
	static float mostHalfWidth(Minecraft client) {
		float halfFovY = client.options.fov().get() / 2f;
		float aspect = client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getHeight());
		float halfFovX = (float) Math.toDegrees(Math.atan(Math.tan(Math.toRadians(halfFovY)) * aspect));
		return Math.max(1f, (halfFovX - EDGE_DEGREES - CENTER_BAND) / 2f);
	}

	/**
	 * @return how far from the crosshair a line's middle stands to the side, in degrees: just outside the band around the crosshair, and further
	 *         out by the spread only as far as the line still fits in the view
	 */
	private static float sideOffset(float halfFovX, float halfWidth, float spread) {
		float room = Math.max(0f, halfFovX - EDGE_DEGREES - CENTER_BAND - 2f * halfWidth);
		return CENTER_BAND + halfWidth + Math.min(spread * SPREAD_SIDE, room);
	}

	/** Writes half the view's width and height, in degrees, into {@code out}. */
	static void halfView(Minecraft client, float[] out) {
		float halfFovY = client.options.fov().get() / 2f;
		float aspect = client.getWindow().getWidth() / (float) Math.max(1, client.getWindow().getHeight());
		out[0] = (float) Math.toDegrees(Math.atan(Math.tan(Math.toRadians(halfFovY)) * aspect));
		out[1] = halfFovY;
	}

	/**
	 * Writes the direction of a kind of spot around the player into {@code out} (yaw, elevation), as {@link #place} would choose it without its
	 * random jitter, for the Auto placement to judge.
	 *
	 * @return false when that kind does not fit in the view
	 */
	static boolean nearAngles(int kind, float viewYaw, float viewElevation, float halfFovX, float halfFovY, float halfWidth, float halfHeight,
							  float spread, float[] out) {
		float side = sideOffset(halfFovX, halfWidth, spread);
		if (side + halfWidth > halfFovX - EDGE_DEGREES + 0.01f) {
			return false;
		}
		float level = Mth.clamp(-viewElevation, -halfFovY + halfHeight + EDGE_DEGREES, halfFovY - halfHeight - EDGE_DEGREES);
		out[0] = kind == LEFT ? viewYaw - side : viewYaw + side;
		out[1] = viewElevation + level;
		return true;
	}

	/** @return the distance a line around the player wants, without its random part */
	static double nearDistance(float reach) {
		return (NEAR_MIN + NEAR_MAX) / 2.0 * reach;
	}

	/** @return the least room a line around the player needs */
	static double nearClosest() {
		return NEAR_CLOSEST;
	}

	/** @return the gap a line keeps before what a ray to it hits */
	static double obstacleGap() {
		return OBSTACLE_GAP;
	}

	/** Forgets the last spots, so the next line starts in front of the player. */
	void reset() {
		lastKind = -1;
		nextSide = -1f;
		preferredSide = 0f;
		clearAvoided();
	}

	/**
	 * Finds a spot for a line of the given angular size. Lines already standing never move: the new one goes into a spot of its own, to the left
	 * or the right of the view (taking turns), never in the band around the crosshair and never over a line that is still visible.
	 *
	 * @param manual     whether the distance was chosen by the player: it is the most, a blocked line comes closer in its own direction first
	 * @param halfWidth  half the line's width, as an angle in degrees
	 * @param halfHeight half its height with its adlibs, as an angle in degrees
	 * @param glow       the room its glow takes around it, as an angle in degrees
	 * @param spread     how far from the crosshair the lines may go, from 0 to 1
	 * @param reach      the distance setting, a factor of the placement's distances
	 */
	void place(Minecraft client, Vec3 eye, float viewYaw, float viewPitch, boolean sky, boolean manual, float halfWidth, float halfHeight, float glow,
			   float spread, float reach, Spot out) {
		halfView(client, view);
		place(eye.x, eye.y, eye.z, viewYaw, viewPitch, view[0], view[1], sky, manual, halfWidth, halfHeight, glow, spread, reach, out);
	}

	/** {@link #place(Minecraft, Vec3, float, float, boolean, boolean, float, float, float, float, float, Spot)} with the view's size given. */
	void place(double eyeX, double eyeY, double eyeZ, float viewYaw, float viewPitch, float halfFovX, float halfFovY, boolean sky, boolean manual,
			   float halfWidth, float halfHeight, float glow, float spread, float reach, Spot out) {
		float viewElevation = -viewPitch;
		double wanted;
		float elevation;
		float floor;
		float highest;
		if (sky) {
			wanted = Mth.lerp(random.nextDouble(), SKY_MIN, SKY_MAX) * reach;
			// the height comes from the sky, not from the view: looking straight up or down the line still stands ahead, up in the sky; looking
			// ahead it stays below the top of the view
			// a tall line that does not fit between the usual lowest height and the top of the view comes down towards the horizon (always above
			// it) rather than reaching out of the view
			float top = Math.min(SKY_HIGHEST, viewElevation + halfFovY - halfHeight - EDGE_DEGREES);
			float lowest = Math.max(SKY_STACK_FLOOR + halfHeight, Math.min(SKY_LOWEST + halfHeight, top));
			highest = Math.max(lowest, top);
			elevation = Math.max(lowest, Math.min(highest, Mth.lerp(random.nextFloat(), SKY_FIRST_LOW, SKY_FIRST_HIGH)));
			floor = SKY_STACK_FLOOR + halfHeight;
		} else {
			wanted = Mth.lerp(random.nextDouble(), NEAR_MIN, NEAR_MAX) * reach;
			// out in the world near eye height, within the view
			float level = Mth.clamp(-viewElevation + (random.nextFloat() - 0.5f) * 2f * SPREAD_JITTER * spread,
				-halfFovY + halfHeight + EDGE_DEGREES, halfFovY - halfHeight - EDGE_DEGREES);
			elevation = viewElevation + level;
			floor = -Float.MAX_VALUE;
			highest = Float.MAX_VALUE;
		}
		collect(viewYaw, viewElevation, halfFovX, halfFovY, halfWidth, halfHeight, spread, sky, elevation, floor, highest);
		space.resetBudget();
		out.open = false;
		if (manual) {
			// the chosen distance is the most: the nearest free spot in the first direction, before another direction is tried
			for (int i = 0; i < candidates && !out.open; i++) {
				out.open = tryUsual(i, eyeX, eyeY, eyeZ, sky, wanted, halfWidth, halfHeight, glow, out)
					|| tryCloser(i, eyeX, eyeY, eyeZ, sky, wanted, halfWidth, halfHeight, glow, out);
			}
		} else {
			for (int i = 0; i < candidates && !out.open; i++) {
				out.open = tryUsual(i, eyeX, eyeY, eyeZ, sky, wanted, halfWidth, halfHeight, glow, out);
			}
			for (int i = 0; i < candidates && !out.open; i++) {
				out.open = tryCloser(i, eyeX, eyeY, eyeZ, sky, wanted, halfWidth, halfHeight, glow, out);
			}
		}
	}

	/**
	 * Lists the directions to try, in order: on the side whose turn it is (or the Auto placement's pick), below the newest line if that stands on
	 * this side, else at the side's own spot or under or over it; then the other side; then a bit farther out to the sides rather than towards
	 * the middle, even if the line then reaches out of the view. In the sky every direction is followed by the same one higher up, for a line
	 * hidden behind a mountain or a tree. Directions in the band around the crosshair or over a visible line are left out.
	 */
	private void collect(float viewYaw, float viewElevation, float halfFovX, float halfFovY, float halfWidth, float halfHeight, float spread,
						 boolean sky, float elevation, float floor, float highest) {
		candidates = 0;
		float firstSide = preferredSide != 0 ? preferredSide : nextSide;
		preferredSide = 0;
		float sideOffset = sideOffset(halfFovX, halfWidth, spread);
		float stack = 2f * halfHeight + SKY_GAP;
		for (int pass = 0; pass < 2; pass++) {
			float side = pass == 0 ? firstSide : -firstSide;
			if (newest >= 0 && Math.signum(Mth.wrapDegrees(avoidYaw[newest] - viewYaw)) == side
				&& Math.abs(Mth.wrapDegrees(avoidYaw[newest] - viewYaw)) <= halfFovX) {
				float below = avoidElevation[newest] - avoidHalfHeight[newest] - SKY_GAP - halfHeight;
				if (below >= floor) {
					add(avoidYaw[newest], below, side, true, viewYaw, viewElevation, halfFovX, halfFovY, halfWidth, halfHeight, sky, highest);
				}
			}
			float yaw = viewYaw + side * sideOffset;
			for (int h = 0; h < 3; h++) {
				float height = h == 0 ? elevation : h == 1 ? elevation - stack : elevation + stack;
				if (height >= floor && height <= Math.max(highest, elevation)) {
					add(yaw, height, side, true, viewYaw, viewElevation, halfFovX, halfFovY, halfWidth, halfHeight, sky, highest);
				}
			}
		}
		for (int k = 1; k <= FARTHER_TRIES; k++) {
			for (int pass = 0; pass < 2; pass++) {
				float side = pass == 0 ? firstSide : -firstSide;
				add(viewYaw + side * (sideOffset + k * FARTHER_DEGREES), elevation, side, false, viewYaw, viewElevation, halfFovX, halfFovY,
					halfWidth, halfHeight, sky, highest);
			}
		}
	}

	/** Adds a direction if it keeps out of the band around the crosshair, off the visible lines and (unless {@code inView} is false) in the view. */
	private void add(float yaw, float elevation, float side, boolean inView, float viewYaw, float viewElevation, float halfFovX, float halfFovY,
					 float halfWidth, float halfHeight, boolean sky, float highest) {
		float across = Math.abs(Mth.wrapDegrees(yaw - viewYaw));
		if (across - halfWidth < CENTER_BAND || overlapsAny(yaw, elevation, halfWidth, halfHeight)) {
			return;
		}
		if (inView && (across + halfWidth > halfFovX - EDGE_DEGREES || Math.abs(elevation - viewElevation) + halfHeight > halfFovY - EDGE_DEGREES)) {
			return;
		}
		addCandidate(yaw, elevation, side);
		if (sky) {
			for (int i = 1; i <= SKY_TRIES; i++) {
				float higher = elevation + SKY_RAISE * i;
				if (higher > highest || overlapsAny(yaw, higher, halfWidth, halfHeight)) {
					break;
				}
				addCandidate(yaw, higher, side);
			}
		}
	}

	private void addCandidate(float yaw, float elevation, float side) {
		if (candidates < MOST_CANDIDATES) {
			candidateYaw[candidates] = yaw;
			candidateElevation[candidates] = elevation;
			candidateSide[candidates] = side;
			candidates++;
		}
	}

	/**
	 * Tries a direction at the usual distance: as far as the rays to the line's middle, ends, top and bottom reach, up to the wanted distance and
	 * down to its usual share.
	 */
	private boolean tryUsual(int i, double eyeX, double eyeY, double eyeZ, boolean sky, double wanted, float halfWidth, float halfHeight, float glow,
							 Spot out) {
		double room = room(i, eyeX, eyeY, eyeZ, sky, wanted, halfWidth, halfHeight);
		if (room < wanted * USUAL_SHARE || space.exhausted()) {
			return false;
		}
		return tryAt(i, eyeX, eyeY, eyeZ, sky, room, 1f, halfWidth, halfHeight, glow, out);
	}

	/** Tries a direction closer than usual, step by step down to the closest distance, the line a little smaller the closer it comes. */
	private boolean tryCloser(int i, double eyeX, double eyeY, double eyeZ, boolean sky, double wanted, float halfWidth, float halfHeight, float glow,
							  Spot out) {
		double closest = sky ? SKY_CLOSEST : NEAR_CLOSEST;
		double usual = wanted * USUAL_SHARE;
		double start = Math.min(usual, room(i, eyeX, eyeY, eyeZ, sky, wanted, halfWidth, halfHeight));
		double step = sky ? SKY_CLOSER_STEP : NEAR_CLOSER_STEP;
		for (double distance = start; distance >= closest && !space.exhausted(); distance -= step) {
			float near = (float) Mth.clamp((usual - distance) / Math.max(1e-6, usual - closest), 0.0, 1.0);
			if (tryAt(i, eyeX, eyeY, eyeZ, sky, distance, Mth.lerp(near, 1f, CLOSEST_SHRINK), halfWidth, halfHeight, glow, out)) {
				return true;
			}
		}
		return false;
	}

	/** @return how far the rays in a direction get: to the line's middle in the sky (the sky is open), to its middle and edges around you */
	private double room(int i, double eyeX, double eyeY, double eyeZ, boolean sky, double wanted, float halfWidth, float halfHeight) {
		if (sky) {
			return space.ray(eyeX, eyeY, eyeZ, candidateYaw[i], candidateElevation[i], wanted, OBSTACLE_GAP);
		}
		return space.openDistance(eyeX, eyeY, eyeZ, candidateYaw[i], candidateElevation[i], halfWidth, halfHeight, wanted, OBSTACLE_GAP);
	}

	/** @return whether the line was placed in a direction at a distance: its whole box free and seen from the eye */
	private boolean tryAt(int i, double eyeX, double eyeY, double eyeZ, boolean sky, double distance, float shrink, float halfWidth, float halfHeight,
						  float glow, Spot out) {
		float yaw = candidateYaw[i];
		float elevation = candidateElevation[i];
		if (!space.fits(eyeX, eyeY, eyeZ, yaw, elevation, distance, (halfWidth + glow) * shrink, (halfHeight + glow) * shrink)) {
			return false;
		}
		float side = candidateSide[i];
		nextSide = -side;
		lastKind = side < 0f ? LEFT : RIGHT;
		set(eyeX, eyeY, eyeZ, yaw, elevation, distance, out);
		out.shrink = shrink;
		return true;
	}

	/** @return whether a line there would cover a line that is still visible, with a little room between them */
	private boolean overlapsAny(float yaw, float elevation, float halfWidth, float halfHeight) {
		for (int i = 0; i < avoided; i++) {
			if (Math.abs(Mth.wrapDegrees(yaw - avoidYaw[i])) < halfWidth + avoidHalfWidth[i] + SKY_GAP
				&& Math.abs(elevation - avoidElevation[i]) < halfHeight + avoidHalfHeight[i] + SKY_GAP) {
				return true;
			}
		}
		return false;
	}

	static Vec3 direction(float yaw, float elevation) {
		double yawRad = Math.toRadians(yaw);
		double up = Math.toRadians(elevation);
		return new Vec3(-Math.sin(yawRad) * Math.cos(up), Math.sin(up), Math.cos(yawRad) * Math.cos(up));
	}

	/** Sets the spot in a direction and distance; it faces the eye about the up axis. */
	private static void set(double eyeX, double eyeY, double eyeZ, float yaw, float elevation, double distance, Spot out) {
		double yawRad = Math.toRadians(yaw);
		double up = Math.toRadians(elevation);
		double dx = -Math.sin(yawRad) * Math.cos(up);
		double dz = Math.cos(yawRad) * Math.cos(up);
		out.x = eyeX + dx * distance;
		out.y = eyeY + Math.sin(up) * distance;
		out.z = eyeZ + dz * distance;
		out.facing = (float) Math.atan2(-dx, -dz);
		out.distance = distance;
	}
}
