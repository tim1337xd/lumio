package dev.tim1337.lumio.features.visual.world.cinematic;

import net.minecraft.util.Mth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shot type's planned camera path stays calm (see {@link CineMotion}): it turns no faster than the limit and no further in a whole shot,
 * orbits over a small arc only, starts and ends softly, and never changes its speed suddenly. Checked with the largest moves a shot can get.
 */
class ShotPathTest {
	private static final double EYE_X = 0.0;
	private static final double EYE_Y = 64.0;
	private static final double EYE_Z = 0.0;
	private static final float ASPECT = 16f / 9f;
	private static final float STEP = 0.05f;
	private static final float LONGEST_SHOT = 40f;
	/** Room for the rounding of the samples. */
	private static final double SLACK = 0.05;
	/** Turning at the very start and after the move is over, in degrees per second: next to nothing. */
	private static final double STILL_RATE = 0.2;
	/** The most the turn speed changes from one sample to the next, in degrees per second. */
	private static final double MOST_RATE_CHANGE = 0.1;

	/** @return a shot of a type with its largest move */
	private static Shot shot(ShotType type) {
		Shot shot = new Shot();
		shot.type = type;
		shot.angle = 0.7;
		shot.radius = type == ShotType.WIDE_CRANE ? 11.0 : type == ShotType.TOP_DOWN ? 1.2 : 4.0;
		shot.height = type == ShotType.TOP_DOWN ? 10.0 : type == ShotType.LOW_HERO ? -1.05 : 1.0;
		shot.maxHeight = 12.0;
		shot.maxDistance = 16.0;
		shot.side = 1;
		shot.turn = 1;
		shot.move = 1.0;
		shot.lookYaw = 0.3;
		shot.arc = Math.toRadians(switch (type) {
			case WIDE_CRANE -> CineMotion.CRANE_ARC;
			case SLOW_ORBIT -> CineMotion.ORBIT_MOST_ARC;
			case LOW_HERO -> CineMotion.LOW_ARC;
			default -> 0.0;
		});
		shot.limit();
		return shot;
	}

	@Test
	void everyShotTypeMovesCalmly() {
		for (ShotType type : ShotType.values()) {
			Shot shot = shot(type);
			CinePose pose = new CinePose();
			shot.pose(0f, EYE_X, EYE_Y, EYE_Z, ASPECT, pose);
			float firstYaw = pose.yaw;
			float lastYaw = pose.yaw;
			float lastPitch = pose.pitch;
			double lastRate = 0.0;
			double most = 0.0;
			double leastTurn = 0.0;
			double mostTurn = 0.0;
			for (float t = STEP; t <= LONGEST_SHOT; t += STEP) {
				shot.pose(t, EYE_X, EYE_Y, EYE_Z, ASPECT, pose);
				double yawRate = Math.abs(Mth.wrapDegrees(pose.yaw - lastYaw)) / STEP;
				double pitchRate = Math.abs(pose.pitch - lastPitch) / STEP;
				double rate = Math.max(yawRate, pitchRate);
				most = Math.max(most, rate);
				double turned = Mth.wrapDegrees(pose.yaw - firstYaw);
				leastTurn = Math.min(leastTurn, turned);
				mostTurn = Math.max(mostTurn, turned);
				assertTrue(Math.abs(rate - lastRate) <= MOST_RATE_CHANGE, type + ": the speed changes suddenly at " + t + " s");
				if (t <= STEP * 2 || t > CineMotion.MOVE_SECONDS + STEP) {
					assertTrue(rate <= STILL_RATE, type + ": moves at " + t + " s, where it should be still: " + rate);
				}
				lastYaw = pose.yaw;
				lastPitch = pose.pitch;
				lastRate = rate;
			}
			assertTrue(most <= CineMotion.MOST_TURN_RATE + SLACK, type + " turns at " + most + " degrees a second");
			assertTrue(mostTurn - leastTurn <= CineMotion.MOST_TURN + SLACK, type + " turns " + (mostTurn - leastTurn) + " degrees in a shot");
			System.out.println("SHOTPATH " + type + ": most turn speed " + Math.round(most * 100) / 100.0 + " degrees a second, whole turn "
				+ Math.round((mostTurn - leastTurn) * 100) / 100.0 + " degrees");
		}
	}

	@Test
	void orbitsStayWithinASmallArc() {
		assertTrue(CineMotion.ORBIT_MOST_ARC <= 35.0);
		// an orbit turns the camera as far as it goes around the player, so its arc keeps within the turn of a shot
		assertTrue(CineMotion.ORBIT_MOST_ARC <= CineMotion.MOST_TURN);
		assertTrue(CineMotion.MOST_TURN_RATE <= 6.0 && CineMotion.MOST_TURN <= 30.0);
		assertTrue(CineMotion.CRANE_ARC <= CineMotion.ORBIT_MOST_ARC && CineMotion.LOW_ARC <= CineMotion.ORBIT_MOST_ARC);
	}

	@Test
	void theMoveStartsAndEndsSoftly() {
		assertTrue(CineMotion.progress(0.0) == 0.0);
		assertTrue(CineMotion.progress(CineMotion.MOVE_SECONDS) == 1.0);
		assertTrue(CineMotion.progress(CineMotion.MOVE_SECONDS * 2) == 1.0);
		// its speed is near zero at both ends
		double small = CineMotion.MOVE_SECONDS * 0.01;
		assertTrue(CineMotion.progress(small) < 1e-4);
		assertTrue(1.0 - CineMotion.progress(CineMotion.MOVE_SECONDS - small) < 1e-4);
	}
}
