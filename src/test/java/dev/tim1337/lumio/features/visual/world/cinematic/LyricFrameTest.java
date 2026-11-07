package dev.tim1337.lumio.features.visual.world.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lyric line stays in the picture along the camera's path, or it is held in the frame instead: checked with made-up paths, a slow pan, a fast
 * orbit and a push-in.
 */
class LyricFrameTest {
	private static final float ASPECT = 16f / 9f;
	/** A 2.39 letterbox on a 16:9 screen. */
	private static final float PICTURE = ASPECT / 2.39f;
	private static final float FOV = 70f;
	/** The line: 6 blocks ahead of the camera's start, a little right and below the middle, about a third of the picture wide. */
	private static final double LINE_X = -1.2;
	private static final double LINE_Y = 63.4;
	private static final double LINE_Z = 6.0;
	private static final double HALF_WIDTH = 1.4;
	private static final double HALF_HEIGHT = 0.35;
	private static final int SAMPLES = 5;

	/** A camera path: its pose at a share of the line's time, from 0 to 1. */
	private interface Path {
		void pose(float t, CinePose out);
	}

	/** @return the least margin of the line along a path, as the camera checks it */
	private static float leastMargin(Path path) {
		LyricFrame frame = new LyricFrame();
		CinePose pose = new CinePose();
		float[] scratch = new float[2];
		float least = Float.MAX_VALUE;
		for (int k = 0; k < SAMPLES; k++) {
			path.pose(k / (SAMPLES - 1f), pose);
			float margin = frame.margin(pose, ASPECT, PICTURE, LINE_X, LINE_Y, LINE_Z, 1.0, 0.0, HALF_WIDTH, HALF_HEIGHT, scratch);
			least = Math.min(least, margin);
			if (margin < 0f) {
				break;
			}
		}
		return least;
	}

	private static void at(CinePose pose, double x, double y, double z, float yaw, float pitch, float fov) {
		pose.x = x;
		pose.y = y;
		pose.z = z;
		pose.yaw = yaw;
		pose.pitch = pitch;
		pose.fov = fov;
	}

	@Test
	void aPointAheadIsInTheMiddleAndRightIsPositive() {
		CinePose pose = new CinePose();
		at(pose, 0, 64, 0, 0f, 0f, FOV);
		float[] out = new float[2];
		assertTrue(LyricFrame.project(pose, ASPECT, PICTURE, 0, 64, 5, out));
		assertEquals(0f, out[0], 1e-4f);
		assertEquals(0f, out[1], 1e-4f);
		// looking along +z, the right is -x
		assertTrue(LyricFrame.project(pose, ASPECT, PICTURE, -1, 64.5, 5, out));
		assertTrue(out[0] > 0f && out[1] > 0f);
		assertFalse(LyricFrame.project(pose, ASPECT, PICTURE, 0, 64, -5, out), "behind the camera");
	}

	@Test
	void aSlowPanKeepsTheLineInside() {
		// four degrees over the line's time
		assertTrue(leastMargin((t, pose) -> at(pose, 0, 64, 0, 4f * t, 0f, FOV)) >= 0f);
	}

	@Test
	void aFastOrbitLosesTheLineSoItIsHeldInTheFrame() {
		// a quarter turn around the player at five blocks over the line's time
		float least = leastMargin((t, pose) -> {
			double angle = Math.toRadians(90.0 * t);
			double x = -Math.sin(angle) * 5.0;
			double z = 3.0 - Math.cos(angle) * 5.0;
			at(pose, x, 64, z, 0f, 0f, FOV);
			pose.lookAt(0, 64, 3);
		});
		assertTrue(least < 0f, "the line leaves the picture: " + least);
	}

	@Test
	void aPushInThatGrowsTheLinePastTheEdgeFallsBack() {
		// gently closer: still inside
		assertTrue(leastMargin((t, pose) -> at(pose, 0, 64, 1.0 * t, 0f, 0f, FOV)) >= 0f);
		// far closer and narrower: the line grows past the safe part
		assertTrue(leastMargin((t, pose) -> at(pose, 0, 64, 4.5 * t, 0f, 0f, FOV - 20f * t)) < 0f);
	}

	@Test
	void theSongCardCornerIsNoPlace() {
		LyricFrame frame = new LyricFrame();
		CinePose pose = new CinePose();
		at(pose, 0, 64, 0, 0f, 0f, FOV);
		float[] scratch = new float[2];
		// lower left of the picture
		float margin = frame.margin(pose, ASPECT, PICTURE, 3.0, 62.7, 6.0, 1.0, 0.0, 0.6, 0.15, scratch);
		assertEquals(LyricFrame.OUTSIDE, margin);
	}
}
