package dev.tim1337.lumio.features.visual.world.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line during a push-in stays fully inside the safe part of the picture until it fades out: on a mocked push-in path, sampled finely, the spot
 * found keeps its margin; a line too wide is made at most a quarter smaller, and then the push-in is shortened instead of letting it leave.
 */
class PushInSpotsTest {
	private static final double EYE_X = 0.0;
	private static final double EYE_Y = 64.0;
	private static final double EYE_Z = 0.0;
	private static final float ASPECT = 16f / 9f;
	/** The picture's share of the screen's height between the letterbox bars. */
	private static final float PICTURE = 0.82f;
	private static final float STEP = 0.05f;
	/** How long a line shows, and when it spawns: at the start of the push-in, when the most of it is still to come. */
	private static final float SHOWS = 5f;
	private static final float SPAWNS = 0.5f;
	private static final int SAMPLES = 5;
	/** How far from the player a wall stands behind them in the narrow place, in blocks. */
	private static final double WALL = 5.0;

	private static Shot pushIn() {
		Shot shot = new Shot();
		shot.type = ShotType.CLOSE_PUSH_IN;
		shot.angle = 0.7;
		shot.radius = 4.0;
		shot.height = 1.0;
		shot.maxHeight = 12.0;
		shot.maxDistance = 16.0;
		shot.side = 1;
		shot.turn = 1;
		shot.move = 1.0;
		shot.limit();
		return shot;
	}

	/** The check the camera makes, without the world's blocks: the least margin over a few poses of the line's time. */
	private static float margin(Shot path, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight) {
		return least(path, x, y, z, rightX, rightZ, halfWidth, halfHeight, (SHOWS) / (SAMPLES - 1));
	}

	/** The same check with a wall a little behind the player, as in a narrow place: spots past it are hidden. */
	private static float marginWithWall(Shot path, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight) {
		if (Math.hypot(x - EYE_X, z - EYE_Z) > WALL) {
			return LyricFrame.OUTSIDE;
		}
		return margin(path, x, y, z, rightX, rightZ, halfWidth, halfHeight);
	}

	/** @return the least margin of a line to the safe part of the picture while it shows, sampled every so often */
	private static float least(Shot path, double x, double y, double z, double rightX, double rightZ, double halfWidth, double halfHeight, float every) {
		LyricFrame frame = new LyricFrame();
		CinePose pose = new CinePose();
		float[] scratch = new float[2];
		float least = Float.MAX_VALUE;
		for (float t = SPAWNS; t <= SPAWNS + SHOWS + 1e-4f; t += every) {
			path.pose(t, EYE_X, EYE_Y, EYE_Z, ASPECT, pose);
			pose.pitch = Math.max(Shot.MOST_UP, pose.pitch);
			least = Math.min(least, frame.margin(pose, ASPECT, PICTURE, x, y, z, rightX, rightZ, halfWidth, halfHeight, scratch));
		}
		return least;
	}

	private static Shot after(Shot shot, double[] out) {
		Shot path = new Shot();
		path.copy(shot);
		if (out[6] < 1.0) {
			path.shortenPush(CineMotion.progress(SPAWNS) * shot.move, out[6]);
		}
		return path;
	}

	@Test
	void aLineStaysInsideUntilItFadesOut() {
		Shot shot = pushIn();
		double[] out = new double[7];
		assertTrue(new PushInSpots().find(shot, SPAWNS, SHOWS, EYE_X, EYE_Y, EYE_Z, ASPECT, PICTURE, 0.9, 0.22, PushInSpotsTest::margin, out));
		assertEquals(1.0, out[5], 1e-6, "a usual line keeps its size");
		assertEquals(1.0, out[6], 1e-6, "and the push-in its length");
		float least = least(after(shot, out), out[0], out[1], out[2], out[3], out[4], 0.9 * out[5], 0.22 * out[5], STEP);
		assertTrue(least >= 0f, "fully inside the safe part at every moment: " + least);
	}

	@Test
	void inANarrowPlaceAWideLineIsMadeAtMostAQuarterSmallerAndThenThePushInIsShortened() {
		boolean shortened = false;
		// ever wider lines, until one fits only with a shorter push-in: each stays fully inside
		for (double halfWidth = 1.0; halfWidth <= 4.0 && !shortened; halfWidth += 0.1) {
			Shot shot = pushIn();
			double[] out = new double[7];
			assertTrue(new PushInSpots().find(shot, SPAWNS, SHOWS, EYE_X, EYE_Y, EYE_Z, ASPECT, PICTURE, halfWidth, 0.3, PushInSpotsTest::marginWithWall, out),
				"a spot for a line " + halfWidth + " wide");
			assertTrue(out[5] >= 0.75 - 1e-6, "never more than a quarter smaller: " + out[5]);
			if (out[6] < 1.0) {
				shortened = true;
				assertEquals(0.75, out[5], 1e-6, "the push-in is only shortened once the line is as small as it may get");
			}
			float least = least(after(shot, out), out[0], out[1], out[2], out[3], out[4], halfWidth * out[5], 0.3 * out[5], STEP);
			assertTrue(least >= 0f, "a line " + halfWidth + " wide is fully inside the safe part at every moment: " + least);
		}
		assertTrue(shortened, "a line too wide even a quarter smaller gets a shorter push-in");
	}

	@Test
	void shorteningKeepsTheCameraWhereItIs() {
		Shot shot = pushIn();
		CinePose before = new CinePose();
		CinePose shortened = new CinePose();
		shot.pose(2f, EYE_X, EYE_Y, EYE_Z, ASPECT, before);
		Shot path = new Shot();
		path.copy(shot);
		path.shortenPush(CineMotion.progress(2f) * shot.move, 0.5);
		path.pose(2f, EYE_X, EYE_Y, EYE_Z, ASPECT, shortened);
		assertEquals(before.x, shortened.x, 1e-9, "no jump where it is shortened");
		assertEquals(before.z, shortened.z, 1e-9);
		CinePose fullEnd = new CinePose();
		CinePose shortEnd = new CinePose();
		shot.pose(12f, EYE_X, EYE_Y, EYE_Z, ASPECT, fullEnd);
		path.pose(12f, EYE_X, EYE_Y, EYE_Z, ASPECT, shortEnd);
		double fullDistance = Math.hypot(fullEnd.x - EYE_X, fullEnd.z - EYE_Z);
		double shortDistance = Math.hypot(shortEnd.x - EYE_X, shortEnd.z - EYE_Z);
		assertTrue(shortDistance > fullDistance, "it ends farther from the player: a shorter push-in");
	}
}
