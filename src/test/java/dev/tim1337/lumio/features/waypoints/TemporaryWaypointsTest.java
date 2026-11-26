package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An add-on's own temporary waypoint (the Hotspot Radar's guess) lives by the add-on's rules, not by the chat's. */
class TemporaryWaypointsTest {
	private static final String KEY = "lumio-skyblock:hotspot";
	private static final long NOW = 1_000_000L;
	private static final long MINUTE = 60_000L;

	private final TemporaryWaypoints temps = new TemporaryWaypoints();

	@Test
	void showingAgainMovesTheSameWaypoint() {
		temps.showOwned(KEY, "Hotspot", 10, 64, 10, 0, true, NOW);
		temps.showOwned(KEY, "Hotspot", 30, 64, -5, 0, true, NOW + 500);
		assertEquals(1, temps.list().size());
		assertEquals(1, temps.group().points().size());
		assertEquals(30, temps.list().getFirst().point().x());
		temps.removeOwned(KEY);
		assertTrue(temps.list().isEmpty());
		assertTrue(temps.group().points().isEmpty());
	}

	@Test
	void theChatsLimitsLeaveItAlone() {
		temps.showOwned(KEY, "Hotspot", 0, 64, 0, 0, true, NOW);
		// more chat waypoints than allowed push out only the chat's oldest
		for (int i = 0; i < 4; i++) {
			temps.add(i, 64, i, "Corpse", "Someone", NOW + i, 2);
		}
		assertEquals(3, temps.list().size());
		assertTrue(temps.list().stream().anyMatch(TemporaryWaypoints.Temp::owned));
		// no time limit, no "reached" for it: standing on it changes nothing
		temps.tick(NOW + 30 * MINUTE, 3 * MINUTE, true, 0, 64, 0);
		assertEquals(1, temps.list().size());
		assertTrue(temps.list().getFirst().owned());
		assertFalse(TemporaryWaypoints.fading(temps.list().getFirst(), NOW + 30 * MINUTE, 3 * MINUTE));
		// a world change lets it go too
		temps.clear();
		assertTrue(temps.list().isEmpty());
	}

	@Test
	void anAddOnsWaypointKeepsItsColorAndCanGoWithoutLabel() {
		TemporaryWaypoints temps = new TemporaryWaypoints();
		temps.showOwned(KEY, "Hotspot", 0, 64, 0, 0x0A84FF, false, NOW);
		Waypoint point = temps.list().getFirst().point();
		assertEquals(0x0A84FF, point.rgb());
		assertTrue(point.noLabel());
		// a new color or the label back is a change, as a move is
		temps.showOwned(KEY, "Hotspot", 0, 64, 0, 0xFF9F0A, true, NOW + 1);
		point = temps.list().getFirst().point();
		assertEquals(1, temps.list().size());
		assertEquals(0xFF9F0A, point.rgb());
		assertFalse(point.noLabel());
	}
}
