package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The view part of {@link Sight}, used by the egg sac and corpse outlines: only what is on screen counts, never what is behind the player or
 * beside the screen's edges. (The line of sight is the game's own raycast.)
 */
class SightTest {
	private static final Vec3 EYE = new Vec3(0.0, 64.0, 0.0);
	/** Looking north (towards negative z), level. */
	private static final Vec3 NORTH = new Vec3(0.0, 0.0, -1.0);
	private static final double FOV = 70.0;
	private static final double WIDE = 16.0 / 9.0;

	private static boolean sees(double x, double y, double z) {
		return Sight.inView(EYE, NORTH, EYE.add(x, y, z), FOV, WIDE);
	}

	@Test
	void straightAheadIsSeen() {
		assertTrue(sees(0.0, 0.0, -10.0));
	}

	@Test
	void behindAndBesideThePlayerAreNot() {
		assertFalse(sees(0.0, 0.0, 10.0));
		assertFalse(sees(10.0, 0.0, 0.0));
		assertFalse(sees(0.0, -10.0, 0.0));
	}

	@Test
	void theEdgesOfTheScreenCount() {
		// half of a 70 degree view is 35 degrees: tan 35 = 0.70 up and down, 0.70 * 16/9 = 1.24 to the sides
		assertTrue(sees(0.0, 6.9, -10.0));
		assertFalse(sees(0.0, 7.1, -10.0));
		assertTrue(sees(12.3, 0.0, -10.0));
		assertFalse(sees(12.6, 0.0, -10.0));
		// a square window is narrower
		assertFalse(Sight.inView(EYE, NORTH, EYE.add(12.3, 0.0, -10.0), FOV, 1.0));
	}

	@Test
	void lookingStraightUpOrDownStillWorks() {
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		assertTrue(Sight.inView(EYE, up, EYE.add(0.0, 10.0, 0.0), FOV, WIDE));
		assertFalse(Sight.inView(EYE, up, EYE.add(0.0, -10.0, 0.0), FOV, WIDE));
	}

	@Test
	void somethingAtThePlayerCounts() {
		assertTrue(sees(0.0, 0.0, 0.5));
	}
}
