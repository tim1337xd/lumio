package dev.tim1337.lumio.features.visual.world.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every shot gets a lyric spot: the open one when there is room, otherwise the safe one in the lower third, never none. */
class CineLyricSpotTest {
	/** A space open to a distance in every direction, its paths clear only when everything is open far enough. */
	private record Open(double distance) implements CineLyricSpot.Space {
		@Override
		public double reach(double x, double y, double z, double dx, double dy, double dz, double limit) {
			return Math.min(limit, distance);
		}

		@Override
		public boolean clear(double x0, double y0, double z0, double x1, double y1, double z1) {
			double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
			return length <= distance;
		}
	}

	private static CineLyricSpot.Spot find(double open) {
		// the player behind the camera, out of the way
		return find(open, -10.0);
	}

	/** @param playerZ how far ahead of the camera the player stands (the camera looks along +z) */
	private static CineLyricSpot.Spot find(double open, double playerZ) {
		CineLyricSpot.Spot spot = new CineLyricSpot.Spot();
		CineLyricSpot.find(new Open(open), 0, 64, 0, 20f, 0f, 10f, 70f, 16f / 9f, 0, 64, playerZ, spot);
		return spot;
	}

	@Test
	void anOpenFieldGetsTheIdealSpot() {
		CineLyricSpot.Spot spot = find(100.0);
		assertFalse(spot.fallback);
		assertTrue(spot.unit > 0f);
	}

	@Test
	void noFreeSpotStillGetsASpot() {
		// a wall right in front of the camera: nothing is open at all
		CineLyricSpot.Spot spot = find(0.0);
		assertTrue(spot.fallback);
		assertTrue(Double.isFinite(spot.x) && Double.isFinite(spot.y) && Double.isFinite(spot.z));
		assertTrue(spot.unit > 0f);
		// below the camera's eye line: the lower third
		assertTrue(spot.y < 64);
	}

	@Test
	void aSmallRoomGetsTheSafeSpotInsideIt() {
		CineLyricSpot.Spot spot = find(4.0);
		assertTrue(spot.fallback);
		double away = Math.sqrt(spot.x * spot.x + (spot.y - 64) * (spot.y - 64) + spot.z * spot.z);
		assertTrue(away <= 4.0, "the spot stays inside the room: " + away);
	}

	@Test
	void theSafeSpotStandsInFrontOfThePlayer() {
		// over the shoulder: the player three blocks ahead, nothing open far enough for the ideal spot
		CineLyricSpot.Spot spot = find(5.0, 3.0);
		assertTrue(spot.fallback);
		assertTrue(spot.z < 3.0, "nearer than the player: " + spot.z);
		assertTrue(spot.z > 0.0, "still ahead of the camera: " + spot.z);
	}
}
