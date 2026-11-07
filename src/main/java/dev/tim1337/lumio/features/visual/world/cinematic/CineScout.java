package dev.tim1337.lumio.features.visual.world.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * Plans the next shot while one runs: candidates of every allowed shot type, at several angles, distances and heights, are tested over many
 * frames with a fixed budget of block tests per frame. A candidate is dropped if the camera, anywhere on its move, is inside or touching a block,
 * in water or lava (unless the player is), breaks the distance rules, or has no clear view of the player's head and body. The rest are scored:
 * open depth behind the player, room for the lyric on the other third, light, room around the camera, something new compared to the last shot, and
 * the type the song asks for. The best ones are checked again right before the cut.
 */
final class CineScout {
	private static final int POOL = 48;
	/**
	 * Moments of a shot's move that are tested, in seconds: the whole of the longest shot, so a slow move cannot drift into a wall late in a
	 * long shot.
	 */
	private static final float SAMPLE_STEP_SECONDS = 2.5f;
	private static final float[] SAMPLES = samples();
	/** Block tests a candidate costs: per sample its room and two rays, then depth, four rays for the lyric's room, and walls. */
	static final int COST = SAMPLES.length * 3 + 6;
	/** The camera keeps this much room to every block, in blocks from its center. */
	private static final double ROOM = 0.5;
	private static final double WALL_ROOM = 1.5;
	private static final double DEPTH = 32.0;
	/** A lyric spot this open scores better: the line reads like a title in the distance. */
	private static final double LYRIC_OPEN = 10.0;
	/** Variety: shots this many degrees apart around the player count as different. */
	private static final double DIFFERENT_ANGLE = Math.toRadians(60.0);
	/** The low shot's height relative to the eyes. */
	private static final double LOW_HEIGHT = -1.05;

	/** Where the player is and what the next shot should be, captured when planning starts. */
	static final class Brief {
		double eyeX;
		double eyeY;
		double eyeZ;
		double lookYaw;
		boolean walking;
		double walkYaw;
		/** The side of the path the camera stays on while the player walks: 1 or -1. */
		int pathSide = 1;
		boolean inFluid;
		float day;
		float aspect = 16f / 9f;
		double maxHeight;
		double maxDistance;
		final boolean[] allowed = new boolean[ShotType.count()];
		/** The type the song asks for, or null; and whether wide shots are preferred (a chorus). */
		ShotType preferred;
		boolean wantWide;
		ShotType lastType;
		double lastAngle = Double.NaN;
		/** Whether the song card stays in the lower left, so the lyric keeps to the right third and the player to the left. */
		boolean cardStays;
	}

	private final Shot[] pool = new Shot[POOL];
	private final CinePose sample = new CinePose();
	private final Random random = new Random();
	private final Brief brief = new Brief();
	/** The ray tests of the lyric's spot against the level being scouted, and the spot found, both reused for every candidate. */
	private final LevelSpace space = new LevelSpace();
	private final CineLyricSpot.Spot spot = new CineLyricSpot.Spot();

	private static final class LevelSpace implements CineLyricSpot.Space {
		Level level;

		@Override
		public double reach(double x, double y, double z, double dx, double dy, double dz, double limit) {
			return CineRays.reach(level, x, y, z, dx, dy, dz, limit);
		}

		@Override
		public boolean clear(double x0, double y0, double z0, double x1, double y1, double z1) {
			return CineRays.clear(level, x0, y0, z0, x1, y1, z1);
		}
	}
	private int count;
	private int next;
	private boolean planning;

	CineScout() {
		for (int i = 0; i < POOL; i++) {
			pool[i] = new Shot();
		}
	}

	/** @return the brief to fill before {@link #begin} */
	Brief brief() {
		return brief;
	}

	/** Starts planning from the brief: makes the candidates; they are tested by {@link #step}. */
	void begin() {
		count = 0;
		next = 0;
		planning = true;
		for (int t = 0; t < ShotType.count(); t++) {
			ShotType type = ShotType.of(t);
			if (!brief.allowed[t] || type == ShotType.SIDE_TRACK && !brief.walking) {
				continue;
			}
			int variants = type == ShotType.OVER_SHOULDER || type == ShotType.CLOSE_PUSH_IN || type == ShotType.SIDE_TRACK ? 4 : 6;
			for (int v = 0; v < variants && count < POOL; v++) {
				make(pool[count++], type, v, variants);
			}
		}
	}

	/** @return whether a plan is being made or done */
	boolean planning() {
		return planning;
	}

	/** @return whether every candidate has been tested */
	boolean done() {
		return planning && next >= count;
	}

	private void make(Shot shot, ShotType type, int variant, int variants) {
		shot.type = type;
		shot.valid = false;
		shot.hasAnchor = false;
		shot.score = 0f;
		shot.lookYaw = brief.walking ? brief.walkYaw : brief.lookYaw;
		shot.maxHeight = brief.maxHeight;
		shot.maxDistance = brief.maxDistance;
		shot.side = brief.cardStays || random.nextBoolean() ? 1 : -1;
		shot.turn = random.nextBoolean() ? 1 : -1;
		shot.arc = 0.0;
		// about half the shots are locked off, with only a very slight drift
		shot.move = random.nextDouble() < CineMotion.STILL_SHARE ? CineMotion.STILL_MOVE : 1.0;
		double spread = (variant + random.nextDouble() * 0.6) / variants * Mth.TWO_PI;
		double near = random.nextDouble();
		switch (type) {
			case WIDE_CRANE -> {
				shot.angle = spread;
				shot.radius = 9.0 + near * 4.0;
				shot.height = 1.5 + random.nextDouble() * 1.5;
				shot.arc = Math.toRadians(CineMotion.CRANE_ARC);
			}
			case SLOW_ORBIT -> {
				shot.angle = spread;
				shot.radius = 4.0 + near * 2.0;
				shot.height = 0.3 + random.nextDouble() * 0.9;
				shot.arc = Math.toRadians(CineMotion.ORBIT_LEAST_ARC + random.nextDouble() * (CineMotion.ORBIT_MOST_ARC - CineMotion.ORBIT_LEAST_ARC));
			}
			case OVER_SHOULDER -> {
				// behind the player, a little to one side
				shot.angle = Math.PI - shot.lookYaw + (variant % 2 == 0 ? 0.35 : -0.35) * (0.6 + near * 0.6);
				shot.radius = 2.6 + near * 0.6;
				shot.height = 0.45;
			}
			case LOW_HERO -> {
				shot.angle = spread;
				shot.radius = 3.0 + near * 1.2;
				// low, just over half a block above the ground (the eyes are 1.62 above the feet), so the camera keeps its room
				shot.height = LOW_HEIGHT;
				shot.arc = Math.toRadians(CineMotion.LOW_ARC);
			}
			case CLOSE_PUSH_IN -> {
				// in front of the face
				shot.angle = -shot.lookYaw + (variant - variants / 2.0) * 0.25;
				shot.radius = 2.8 + near * 0.6;
				shot.height = 0.05;
			}
			case SIDE_TRACK -> {
				// beside the path, on the side the film keeps to
				shot.angle = -brief.walkYaw + brief.pathSide * Math.PI / 2.0 + (random.nextDouble() - 0.5) * 0.3;
				shot.radius = 4.0;
				shot.height = 0.3;
			}
			case TOP_DOWN -> {
				shot.angle = spread;
				shot.radius = 1.2;
				shot.height = 10.0;
			}
		}
		shot.limit();
	}

	/** Tests candidates until the frame's budget of block tests is used. */
	void step(Level level, int budget) {
		while (planning && next < count && budget >= COST) {
			test(level, pool[next++]);
			budget -= COST;
		}
	}

	private void test(Level level, Shot shot) {
		shot.valid = checks(level, shot, brief.eyeX, brief.eyeY, brief.eyeZ);
		if (!shot.valid) {
			return;
		}
		shot.pose(0f, brief.eyeX, brief.eyeY, brief.eyeZ, brief.aspect, sample);
		double cx = sample.x;
		double cy = sample.y;
		double cz = sample.z;
		float score = 0f;
		// open depth behind the player, seen from the camera
		double awayX = brief.eyeX - cx;
		double awayY = brief.eyeY - cy;
		double awayZ = brief.eyeZ - cz;
		double depth = CineRays.reach(level, brief.eyeX, brief.eyeY, brief.eyeZ, awayX, awayY * 0.3, awayZ, DEPTH);
		score += (float) (depth / DEPTH) * 30f;
		// the lyric's spot: in the open on the other third, or the safe spot in the lower third when no open spot is free; every shot has one
		anchor(level, shot, brief.eyeX, brief.eyeY, brief.eyeZ);
		score += spot.fallback ? -25f : spot.open >= LYRIC_OPEN ? 20f : 8f;
		// light on the player and behind them; at night, light sources in the background
		double backX = brief.eyeX + awayX / Math.max(0.01, Math.sqrt(awayX * awayX + awayZ * awayZ)) * Math.min(depth, 16.0);
		double backZ = brief.eyeZ + awayZ / Math.max(0.01, Math.sqrt(awayX * awayX + awayZ * awayZ)) * Math.min(depth, 16.0);
		float light = (CineRays.light(level, brief.eyeX, brief.eyeY, brief.eyeZ, brief.day) + CineRays.light(level, backX, brief.eyeY, backZ, brief.day)) / 2f;
		score += light * 12f;
		if (brief.day < 0.3f) {
			score += CineRays.glow(level, backX, brief.eyeY, backZ) * 8f;
		}
		// not too close to walls
		if (CineRays.roomy(level, cx, cy, cz, WALL_ROOM)) {
			score += 8f;
		}
		// something new: another type, from another direction
		if (shot.type == brief.lastType) {
			score -= 40f;
		}
		if (!Double.isNaN(brief.lastAngle) && Math.abs(Mth.wrapDegrees(Math.toDegrees(shot.angle - brief.lastAngle))) < Math.toDegrees(DIFFERENT_ANGLE)) {
			score -= 25f;
		}
		if (shot.type == brief.preferred) {
			score += 22f;
		}
		if (brief.wantWide && shot.type.wide) {
			score += 12f;
		}
		score += random.nextFloat() * 4f;
		shot.score = score;
	}

	/**
	 * Gives a shot its lyric's spot from where its move starts: also the crane taken without scouting when nothing else fits, so every shot
	 * has its lyric.
	 */
	void anchor(Level level, Shot shot, double eyeX, double eyeY, double eyeZ) {
		shot.pose(0f, eyeX, eyeY, eyeZ, brief.aspect, sample);
		space.level = level;
		CineLyricSpot.find(space, sample.x, sample.y, sample.z, sample.yaw + shot.lyricYawOffset(sample.fov, brief.aspect), sample.yaw, sample.pitch,
			sample.fov, brief.aspect, eyeX, eyeY, eyeZ, spot);
		shot.hasAnchor = true;
		shot.anchorFallback = spot.fallback;
		shot.anchorX = spot.x;
		shot.anchorY = spot.y;
		shot.anchorZ = spot.z;
		shot.anchorUnit = spot.unit;
	}

	/** @return the moments tested, from the start to the end of the longest shot */
	private static float[] samples() {
		float[] out = new float[(int) (CineDirector.LONGEST_SHOT / SAMPLE_STEP_SECONDS) + 1];
		for (int i = 0; i < out.length; i++) {
			out[i] = i * SAMPLE_STEP_SECONDS;
		}
		return out;
	}

	/**
	 * @return whether the shot can be filmed from the player's place: along its whole move the camera has room, is out of fluids (unless the
	 *         player is in one), within the rules, and sees the player's head and body; while walking it stays on its side of the path
	 */
	boolean checks(Level level, Shot shot, double eyeX, double eyeY, double eyeZ) {
		if (brief.walking && shot.type != ShotType.TOP_DOWN) {
			// the side of the path: which way the camera is from the walking direction
			double side = Math.sin(shot.angle + brief.walkYaw);
			if (side * brief.pathSide < 0) {
				return false;
			}
		}
		for (float t : SAMPLES) {
			shot.pose(t, eyeX, eyeY, eyeZ, brief.aspect, sample);
			if (sample.y > eyeY + shot.maxHeight + 0.01 || sample.distanceSquared(eyeX, eyeY, eyeZ) > (shot.maxDistance + 0.01) * (shot.maxDistance + 0.01)) {
				return false;
			}
			if (!CineRays.roomy(level, sample.x, sample.y, sample.z, ROOM)) {
				return false;
			}
			if (!brief.inFluid && CineRays.inFluid(level, sample.x, sample.y, sample.z)) {
				return false;
			}
			if (!LyricFrame.playerInFrame(sample, brief.aspect, eyeX, eyeY, eyeZ) || !LyricFrame.playerSeen(level, sample, eyeX, eyeY, eyeZ)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Takes the best shot that is still valid where the player is now (they may have moved, or a block changed), checking the candidates in order
	 * of their score.
	 *
	 * @return whether one was found and copied into {@code into}
	 */
	boolean take(Level level, double eyeX, double eyeY, double eyeZ, Shot into) {
		boolean found = false;
		for (int round = 0; round < count && !found; round++) {
			Shot best = null;
			for (int i = 0; i < count; i++) {
				Shot candidate = pool[i];
				if (candidate.valid && (best == null || candidate.score > best.score)) {
					best = candidate;
				}
			}
			if (best == null) {
				break;
			}
			if (checks(level, best, eyeX, eyeY, eyeZ)) {
				into.copy(best);
				// the lyric stands where it was found, moved with the player
				into.anchorX += eyeX - brief.eyeX;
				into.anchorY += eyeY - brief.eyeY;
				into.anchorZ += eyeZ - brief.eyeZ;
				found = true;
			} else {
				best.valid = false;
			}
		}
		planning = false;
		return found;
	}

	/** @return the number of candidates tested and how many passed, for the test log */
	int tested() {
		return next;
	}
}
