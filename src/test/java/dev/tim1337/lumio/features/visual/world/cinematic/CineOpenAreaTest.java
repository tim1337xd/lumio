package dev.tim1337.lumio.features.visual.world.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A shot's lyric takes the open sky only where it is free both from the shot's first view and from where its move ends. */
class CineOpenAreaTest {
	private static final float ASPECT = 16f / 9f;

	/** Open to a distance everywhere; from the end camera the left of the picture (+x, as it looks along +z) is closed when asked. */
	private record Space(double distance, CinePose end, boolean endLeftClosed) implements CineLyricSpot.Space {
		@Override
		public double reach(double x, double y, double z, double dx, double dy, double dz, double limit) {
			if (endLeftClosed && x == end.x && y == end.y && z == end.z && dx > 0.0) {
				return 2.0;
			}
			return Math.min(limit, distance);
		}

		@Override
		public boolean clear(double x0, double y0, double z0, double x1, double y1, double z1) {
			return true;
		}
	}

	private static CinePose pose(double x, float yaw) {
		CinePose pose = new CinePose();
		pose.x = x;
		pose.y = 64.0;
		pose.z = 0.0;
		pose.yaw = yaw;
		pose.pitch = 0f;
		pose.fov = 60f;
		return pose;
	}

	/** The player stands behind the cameras, out of the picture. */
	private static boolean find(CineOpenArea area, Space space, CinePose start, CinePose end) {
		return area.find(space, start, end, ASPECT, 1f, 0.0, 64.0, -10.0);
	}

	@Test
	void anOpenViewHasAnAreaFarAway() {
		CinePose start = pose(0.0, 0f);
		CineOpenArea area = new CineOpenArea();
		assertTrue(find(area, new Space(500.0, start, false), start, start));
		double away = Math.sqrt(area.x * area.x + (area.y - 64.0) * (area.y - 64.0) + area.z * area.z);
		assertEquals(CineOpenArea.DISTANCE, away, 1.0);
		assertTrue(area.halfWidth > 1.0 && area.halfHeight > 1.0);
	}

	@Test
	void aClosedViewHasNone() {
		CinePose start = pose(0.0, 0f);
		assertFalse(find(new CineOpenArea(), new Space(10.0, start, false), start, start));
	}

	@Test
	void anEndViewSharingNothingLeavesTheFirstViewsOwnArea() {
		CinePose start = pose(0.0, 0f);
		CinePose end = pose(0.0, 180f);
		CineOpenArea area = new CineOpenArea();
		assertTrue(find(area, new Space(500.0, end, false), start, end), "the first view's own area");
		assertTrue(area.startOnly);
		CineOpenArea both = new CineOpenArea();
		assertTrue(find(both, new Space(500.0, start, false), start, start));
		assertFalse(both.startOnly, "shared by both views where they share one");
	}

	@Test
	void theAreaKeepsToWhatTheEndViewSeesOpen() {
		CinePose start = pose(0.0, 0f);
		CinePose end = pose(1.0, 0f);
		CineOpenArea area = new CineOpenArea();
		assertTrue(find(area, new Space(500.0, end, true), start, end));
		// the camera's right is -x: the area lies on the right, where the end view is open
		assertTrue(area.x < 0.0, "on the open side: " + area.x);
	}
}
