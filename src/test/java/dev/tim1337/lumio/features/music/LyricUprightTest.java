package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A line of the lyrics in the world stands upright for every view, from straight down to straight up: world up, no roll, a bounded lean. */
class LyricUprightTest {
	private static final double EPSILON = 1e-6;

	/** @return the direction a view looks along, as Minecraft's yaw and pitch (pitch positive looking down) give it */
	private static double[] view(float yaw, float pitch) {
		double y = Math.toRadians(yaw);
		double p = Math.toRadians(pitch);
		return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
	}

	@Test
	void everyViewGivesAnUprightLineWithoutRoll() {
		double mostLean = Math.toRadians(LyricUpright.MOST_LEAN_DEGREES);
		for (float yaw : new float[] {0f, 37f, 90f, -135f, 180f}) {
			for (float pitch = -90f; pitch <= 90f; pitch += 5f) {
				double[] direction = view(yaw, pitch);
				for (int k = 0; k < 3; k++) {
					direction[k] *= 40.0;
				}
				double elevation = LyricUpright.keepNearHorizon(direction, yaw);
				String name = "yaw " + yaw + ", pitch " + pitch;
				assertTrue(Math.abs(elevation) <= Math.toRadians(LyricUpright.MOST_ELEVATION_DEGREES) + EPSILON, name + ": stands at " + Math.toDegrees(elevation));
				assertEquals(40.0, Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]), 1e-6, name + ": as far away");
				float facing = LyricUpright.facing(direction[0], direction[2]);
				float lean = LyricUpright.lean(elevation);
				assertTrue(Math.abs(lean) <= mostLean + EPSILON, name + ": leans " + Math.toDegrees(lean));
				double[] axes = LyricUpright.axes(facing, lean);
				assertEquals(0.0, axes[4], EPSILON, name + ": its right stays level, no roll");
				assertTrue(axes[1] >= Math.cos(mostLean) - EPSILON, name + ": its up is the world's up but for the lean: " + axes[1]);
				// it faces back toward the eye across the ground: its front is the opposite of the way to it
				double across = Math.hypot(direction[0], direction[2]);
				assertEquals(-direction[0] / across, Math.sin(facing), 1e-6, name);
				assertEquals(-direction[2] / across, Math.cos(facing), 1e-6, name);
				// above the eye it leans back with its top toward the eye, below with its top away: either way its face turns toward the eye
				double frontY = -Math.sin(lean);
				assertTrue(Math.abs(elevation) < EPSILON || Math.signum(frontY) == -Math.signum(elevation), name + ": leans toward the eye");
			}
		}
	}

	@Test
	void straightUpOrDownItStandsAtTheMostHeightInTheViewsHeading() {
		double[] up = {0.0, 30.0, 0.0};
		double elevation = LyricUpright.keepNearHorizon(up, 90f);
		assertEquals(Math.toRadians(LyricUpright.MOST_ELEVATION_DEGREES), elevation, EPSILON);
		// yaw 90 looks toward minus x
		assertTrue(up[0] < 0 && Math.abs(up[2]) < 1e-6, "in the view's heading: " + up[0] + ", " + up[2]);
		double[] down = {0.0, -30.0, 0.0};
		assertEquals(-Math.toRadians(LyricUpright.MOST_ELEVATION_DEGREES), LyricUpright.keepNearHorizon(down, 0f), EPSILON);
	}

	@Test
	void rowsAreLevelOnTheScreenForAnyPlacementInTheView() {
		java.util.Random random = new java.util.Random(7L);
		for (int i = 0; i < 20_000; i++) {
			double yaw = Math.toRadians(random.nextDouble() * 360.0);
			double pitch = Math.toRadians(random.nextDouble() * 170.0 - 85.0);
			// the view (Minecraft's: yaw 0 looks toward plus z, a positive pitch looks down), its right and its up, never rolled
			double[] forward = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
			double[] right = norm(new double[] {-forward[2], 0.0, forward[0]});
			double[] up = cross(right, forward);
			// a spot anywhere in the view, as a block placed by the view stands: square to the view of that moment, leaned as allowed
			double a = random.nextDouble() * 1.6 - 0.8;
			double b = random.nextDouble() * 1.0 - 0.5;
			double distance = 3.0 + random.nextDouble() * 110.0;
			double[] spot = new double[3];
			for (int k = 0; k < 3; k++) {
				spot[k] = (forward[k] + a * right[k] + b * up[k]) * distance;
			}
			float facing = LyricUpright.facing(forward[0], forward[2]);
			double[] axes = LyricUpright.axes(facing, LyricUpright.leanToward(Math.asin(forward[1])));
			double half = 0.4 * distance;
			double leftY = screenY(spot, axes, -half, forward, up);
			double rightY = screenY(spot, axes, half, forward, up);
			assertEquals(leftY, rightY, 1e-6, "a row slants on the screen at yaw " + yaw + ", pitch " + pitch + ", spot " + a + ", " + b);
		}
	}

	/** @return how high on the screen a point of a row lies: the block's middle moved along its rows by {@code along} */
	private static double screenY(double[] spot, double[] axes, double along, double[] forward, double[] up) {
		double[] point = {spot[0] + axes[3] * along, spot[1] + axes[4] * along, spot[2] + axes[5] * along};
		return dot(point, up) / dot(point, forward);
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double[] norm(double[] v) {
		double length = Math.sqrt(dot(v, v));
		return new double[] {v[0] / length, v[1] / length, v[2] / length};
	}

	@Test
	void nearTheHorizonNothingChanges() {
		double[] level = {3.0, 1.0, -4.0};
		double elevation = LyricUpright.keepNearHorizon(level, 0f);
		assertEquals(3.0, level[0], 1e-9);
		assertEquals(1.0, level[1], 1e-9);
		assertEquals(-4.0, level[2], 1e-9);
		assertEquals(elevation, LyricUpright.lean(elevation), 1e-6, "a small height leans as much as it stands high");
	}
}
