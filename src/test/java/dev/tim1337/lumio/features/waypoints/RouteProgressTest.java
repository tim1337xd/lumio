package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** When a route goes on: near a point, past it, at a later point or when its block changed, and never back by itself. */
class RouteProgressTest {
	private static final int REACH = RouteProgress.DEFAULT_REACH;

	/** A straight route along x, a point every 20 blocks, all at height 64. */
	private static List<Waypoint> line(int count) {
		Waypoint[] points = new Waypoint[count];
		for (int i = 0; i < count; i++) {
			points[i] = new Waypoint(i * 20, 64, 0, "P" + i);
		}
		return List.of(points);
	}

	@Test
	void reachedWithinTheRadiusAcrossAndTheHeightTolerance() {
		Waypoint point = new Waypoint(0, 64, 0, "");
		assertTrue(RouteProgress.reached(point, 0.5 + 4.9, 64.5, 0.5, REACH));
		assertFalse(RouteProgress.reached(point, 0.5 + 5.1, 64.5, 0.5, REACH));
		assertTrue(RouteProgress.reached(point, 0.5 + 3, 64.5, 0.5 + 3, REACH));
		assertTrue(RouteProgress.reached(point, 0.5, 64.5 + 4, 0.5, REACH));
		assertFalse(RouteProgress.reached(point, 0.5, 64.5 + 4.5, 0.5, REACH));
		assertFalse(RouteProgress.reached(point, 0.5, 64.5 - 4.5, 0.5, REACH));
		assertTrue(RouteProgress.reached(point, 0.5 + 11.9, 64.5, 0.5, RouteProgress.MAX_REACH));
		assertFalse(RouteProgress.reached(point, 0.5 + 2.1, 64.5, 0.5, RouteProgress.MIN_REACH));
	}

	@Test
	void theRouteGoesOnWhenThePlayerIsNearTheNextPoint() {
		RouteProgress progress = new RouteProgress();
		List<Waypoint> points = line(4);
		assertEquals(0, progress.step(points, 0, -10, 64.5, 0.5, REACH, true));
		assertEquals(1, progress.step(points, 0, -3, 64.5, 0.5, REACH, true));
	}

	@Test
	void passedAlongTheWayToTheNextPoint() {
		Waypoint current = new Waypoint(0, 64, 0, "");
		Waypoint next = new Waypoint(20, 64, 0, "");
		// beyond the point along the way, off to the side of its reach
		assertTrue(RouteProgress.passed(current, next, 0.5 + 2, 64.5, 0.5 + 7));
		// still before it
		assertFalse(RouteProgress.passed(current, next, 0.5 - 3, 64.5, 0.5 + 7));
		// beyond it, but too far from it
		assertFalse(RouteProgress.passed(current, next, 0.5 + 2, 64.5, 0.5 + 12));
		// a route that turns back: past the point towards the next one, or off the other way
		Waypoint back = new Waypoint(-6, 64, 8, "");
		assertTrue(RouteProgress.passed(current, back, -4, 64.5, 6));
		assertFalse(RouteProgress.passed(current, back, 6.5, 64.5, 0.5));

		RouteProgress progress = new RouteProgress();
		assertEquals(1, progress.step(line(3), 0, 0.5 + 2, 64.5, 0.5 + 7, REACH, true));
	}

	@Test
	void skipsAheadToALaterPointTheyArriveAt() {
		RouteProgress progress = new RouteProgress();
		List<Waypoint> points = line(6);
		assertEquals(0, progress.step(points, 0, 200, 64.5, 50, REACH, true));
		// arrives at point 3: 0 to 3 are done, 4 is next
		assertEquals(4, progress.step(points, 0, 60.5, 64.5, 0.5, REACH, true));
	}

	@Test
	void aLoopDoesNotGoAroundWhenItsLastPointIsWhereItStarts() {
		RouteProgress progress = new RouteProgress();
		List<Waypoint> points = List.of(new Waypoint(0, 64, 0, "start"), new Waypoint(20, 64, 0, "a"), new Waypoint(20, 64, 20, "b"),
			new Waypoint(1, 64, 1, "back"));
		progress.step(points, 0, 100, 64.5, 100, REACH, true);
		int next = progress.step(points, 0, 0.5, 64.5, 0.5, REACH, true);
		assertEquals(1, next);
		// standing there, the last point (at the same place) is not newly arrived at
		assertEquals(1, progress.step(points, next, 0.5, 64.5, 0.5, REACH, true));
	}

	@Test
	void aPointChosenByHandIsNotLeftUntilTheyArriveAnew() {
		RouteProgress progress = new RouteProgress();
		List<Waypoint> points = line(4);
		int next = progress.step(points, 1, 20.5, 64.5, 0.5, REACH, true);
		assertEquals(2, next);
		// Previous: back to 1 while standing at it; it stays, tick after tick
		progress.hold();
		for (int tick = 0; tick < 20; tick++) {
			assertEquals(1, progress.step(points, 1, 20.5 + tick * 0.05, 64.5, 0.5, REACH, true));
		}
		// away and back: then it counts again
		assertEquals(1, progress.step(points, 1, 20.5, 64.5, 9, REACH, true));
		assertEquals(2, progress.step(points, 1, 20.5, 64.5, 0.5, REACH, true));
	}

	@Test
	void onceMovedOnTheRouteNeverGoesBackByItself() {
		RouteProgress progress = new RouteProgress();
		List<Waypoint> points = line(4);
		int next = progress.step(points, 0, 0.5, 64.5, 0.5, REACH, true);
		assertEquals(1, next);
		for (int tick = 0; tick < 40; tick++) {
			// wandering around the first point
			next = progress.step(points, next, 0.5 + Math.sin(tick) * 4, 64.5, 0.5 + Math.cos(tick) * 4, REACH, true);
			assertEquals(1, next);
		}
	}

	@Test
	void withoutMovingOnOnlyTheWalkIsFollowed() {
		RouteProgress progress = new RouteProgress();
		assertEquals(0, progress.step(line(3), 0, 0.5, 64.5, 0.5, REACH, false));
	}

	@Test
	void minedWhenTheBlockChangesWhileThePlayerIsClose() {
		RouteProgress progress = new RouteProgress();
		assertFalse(progress.mined(0, "stone", true));
		assertFalse(progress.mined(0, "stone", true));
		assertTrue(progress.mined(0, "air", true));
		// a block placed there counts the same
		assertTrue(progress.mined(0, "cobblestone", true));
	}

	@Test
	void notMinedWhenTheBlockChangedFarAwayOrIsNotLoaded() {
		RouteProgress progress = new RouteProgress();
		assertFalse(progress.mined(0, "stone", false));
		assertFalse(progress.mined(0, "air", false));
		// back near: what is there now is the start
		assertFalse(progress.mined(0, "air", true));
		assertFalse(progress.mined(0, null, true));
		assertFalse(progress.mined(0, "air", true));
		// another point: its block is new, nothing changed yet
		assertFalse(progress.mined(1, "stone", true));
		assertTrue(progress.mined(1, "air", true));
	}

	@Test
	void minedOnlyCountsClose() {
		Waypoint point = new Waypoint(0, 64, 0, "");
		assertTrue(RouteProgress.nearForMined(point, 5.5, 64.5, 0.5));
		assertFalse(RouteProgress.nearForMined(point, 7, 64.5, 0.5));
	}

	@Test
	void afterAndBeforeGoAround() {
		assertEquals(0, RouteProgress.after(3, 4));
		assertEquals(3, RouteProgress.before(0, 4));
	}
}
