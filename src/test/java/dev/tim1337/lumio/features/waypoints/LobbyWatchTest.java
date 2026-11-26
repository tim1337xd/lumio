package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When a route restarts at 1: in a lobby with another server id, not after rejoining the same one, and on any world change without an id. */
class LobbyWatchTest {
	private final Object lobbyA = new Object();
	private final Object lobbyB = new Object();

	@Test
	void anotherServerIdIsANewLobby() {
		LobbyWatch watch = new LobbyWatch();
		assertTrue(watch.update(lobbyA, "m12AB", 0), "the first lobby joined");
		assertFalse(watch.update(lobbyA, "m12AB", 1_000), "the same lobby, a moment later");
		// a world change: the id comes a moment later, and it is another
		assertFalse(watch.update(lobbyB, null, 2_000), "waits for the id");
		assertTrue(watch.update(lobbyB, "m7C", 2_600));
	}

	@Test
	void theSameServerIdAgainIsNoNewLobby() {
		LobbyWatch watch = new LobbyWatch();
		watch.update(lobbyA, "m12AB", 0);
		// out (a world change: limbo, another island) and back to the same server
		assertFalse(watch.update(lobbyB, null, 1_000));
		assertFalse(watch.update(lobbyA, "m12AB", 2_000), "rejoining the same server id does not reset");
		assertFalse(watch.update(null, null, 3_000), "no world, no lobby");
		assertFalse(watch.update(lobbyB, "m12AB", 4_000));
	}

	@Test
	void withoutAnIdAWorldChangeIsANewLobby() {
		LobbyWatch watch = new LobbyWatch();
		assertFalse(watch.update(lobbyA, null, 0));
		assertTrue(watch.update(lobbyA, null, LobbyWatch.WAIT_MS), "the first world, after waiting for an id that never came");
		assertFalse(watch.update(lobbyA, null, LobbyWatch.WAIT_MS * 3), "and only once");
		assertFalse(watch.update(lobbyB, null, 100_000));
		assertTrue(watch.update(lobbyB, null, 100_000 + LobbyWatch.WAIT_MS), "the next world change too");
	}

	@Test
	void theServerIdIsReadFromTheScoreboardsDateLine() {
		assertEquals("m12AB", LobbyWatch.serverId("11/15/26 m12AB"));
		assertEquals("L42", LobbyWatch.serverId("  11/15/2026  L42 "));
		assertEquals("mega7C", LobbyWatch.serverId("11/15/26 mega7C"));
		assertNull(LobbyWatch.serverId("Purse: 1,234"));
		assertNull(LobbyWatch.serverId("Late Winter 4th"));
	}

	@Test
	void aRestartStartsTheRouteAtOneAndKeepsThePoints() {
		WaypointGroup group = new WaypointGroup("g", "Route", 0xFFFFFF);
		for (int i = 0; i < 5; i++) {
			group.add(new Waypoint(i * 10, 64, 0, "p" + i));
		}
		group.next(3);
		group.progress().hold();
		group.restart();
		assertEquals(0, group.next());
		assertFalse(group.progress().held(), "nothing is held any more");
		assertEquals(5, group.points().size(), "the points stay");
	}
}
