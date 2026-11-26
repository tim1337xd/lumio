package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Random;

/** Where a direction lies on the screen and back, as the lyrics in the world are placed by. */
class WorldLyricViewTest {
	private static final double CLOSE = 1e-6;
	private static final double[] FOVS = {15.0, 35.0, 45.0, 55.0};
	private static final double[] ASPECTS = {16.0 / 9.0, 4.0 / 3.0, 21.0 / 9.0, 0.8};

	@Test
	void aPlaceOnTheScreenAndItsDirectionAreTheSame() {
		double[] place = new double[2];
		double[] vector = new double[3];
		for (double yaw = -180; yaw <= 180; yaw += 37) {
			for (double pitch = -90; pitch <= 90; pitch += 15) {
				for (double fov : FOVS) {
					WorldLyricView.direction(0.4, -0.7, yaw, pitch, fov, 16.0 / 9.0, vector);
					assertEquals(1.0, Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]), CLOSE);
					assertTrue(WorldLyricView.project(vector[0], vector[1], vector[2], yaw, pitch, fov, 16.0 / 9.0, place));
					assertEquals(0.4, place[0], CLOSE, yaw + " " + pitch);
					assertEquals(-0.7, place[1], CLOSE, yaw + " " + pitch);
				}
			}
		}
	}

	@Test
	void lookingSouthTheMiddleIsAheadAndUpIsUp() {
		double[] place = new double[2];
		assertTrue(WorldLyricView.project(0, 0, 1, 0, 0, 35, 1, place));
		assertEquals(0.0, place[0], CLOSE);
		assertEquals(0.0, place[1], CLOSE);
		assertTrue(WorldLyricView.project(0, 0.2, 1, 0, 0, 35, 1, place));
		assertTrue(place[1] > 0, "a point above the horizon is in the upper half");
		// looking south, west is to the right
		assertTrue(WorldLyricView.project(-0.2, 0, 1, 0, 0, 35, 1, place));
		assertTrue(place[0] > 0);
		assertFalse(WorldLyricView.project(0, 0, -1, 0, 0, 35, 1, place), "behind the camera");
		// looking straight down, the sky is behind
		assertFalse(WorldLyricView.project(0, 1, 0, 0, 90, 35, 1, place));
	}
}
