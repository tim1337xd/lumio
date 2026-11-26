package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.visual.screen.chat.ChatRules;

import org.junit.jupiter.api.Test;

/**
 * Chat waypoints: only players' messages in the clear share formats get a "+ Waypoint" (never Hypixel's own lines), and a waypoint from chat is
 * temporary: never in a group, gone after its time or when reached, a few at most, kept only when saved.
 */
class ChatWaypointsTest {
	private static final long MINUTE = 60_000L;

	/** @return the coordinates the chat would offer for a line, as the chat finds them */
	private static CoordParser.Found offered(String line) {
		String said = ChatRules.playerMessage(line);
		return said == null ? null : CoordParser.inChat(said);
	}

	@Test
	void hypixelsOwnLinesGetNoButton() {
		assertNull(offered("Archer Milestone ❷: You have dealt 125,000,000 Ranged Damage so far! 05m 08s"), "the false positive");
		assertNull(offered("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE! 12 70 -30"));
		assertNull(offered("[NPC] Elle: Head over to 12 70 -30!"));
		assertNull(offered("  Team Score: 302 (S+)"));
		assertNull(offered("+125 Combat Experience 12 70 -30"), "no player wrote it");
	}

	@Test
	void playersSharesGetAButton() {
		CoordParser.Found labelled = offered("Party > [MVP+] Kiwi: x: 12, y: 70, z: -30");
		assertNotNull(labelled);
		assertEquals(12, labelled.x());
		assertEquals(-30, labelled.z());
		assertNotNull(offered("Party > Kiwi: x: 12 y: 70 z: -30"), "without commas");
		assertNotNull(offered("Guild > [VIP] Rae: corpse at 12 70 -30"));
		assertNotNull(offered("Co-op > Nox: 12, 70, -30"));
		assertNotNull(offered("From [MVP] Kiwi: here 100 64 -200"));
		assertNotNull(offered("[MVP++] Kiwi: 12 70 -30"), "all chat");
	}

	@Test
	void unclearNumbersAreRejected() {
		assertNull(offered("Party > Kiwi: dealt 125,000,000 damage"), "thousands separators");
		assertNull(offered("Party > Kiwi: 05m 08s 12s"), "times with units");
		assertNull(offered("Party > Kiwi: split 1:23 4:56 7:89"), "times with colons");
		assertNull(offered("Party > Kiwi: 12k 70 30"), "a suffix");
		assertNull(offered("Party > Kiwi: 50% 60% 70%"), "percent");
		assertNull(offered("Party > Kiwi: x3 12 70"), "a multiplier");
		assertNull(offered("Party > Kiwi: 1 2 3 4 5"), "more than three numbers in a row");
		assertNull(offered("Party > Kiwi: 12 400 -30"), "above the world's height");
		assertNull(offered("Party > Kiwi: x: 31000000, y: 70, z: 0"), "outside the world");
		assertNull(offered("Party > Kiwi: 12.5 70 -30"), "decimals");
	}

	@Test
	void temporaryWaypointsExpireAfterTheirTime() {
		TemporaryWaypoints temps = new TemporaryWaypoints();
		temps.add(12, 70, -30, "Corpse", "Kiwi", 0, 5);
		assertEquals("Corpse · from Kiwi", temps.list().getFirst().point().name());
		temps.tick(3 * MINUTE - 1, 3 * MINUTE, false, 1000, 70, 1000);
		assertEquals(1, temps.list().size());
		assertTrue(TemporaryWaypoints.fading(temps.list().getFirst(), 3 * MINUTE - 1, 3 * MINUTE), "softer near the end");
		temps.tick(3 * MINUTE, 3 * MINUTE, false, 1000, 70, 1000);
		assertTrue(temps.list().isEmpty(), "gone after three minutes");
		assertTrue(temps.group().points().isEmpty(), "and no longer drawn");
	}

	@Test
	void reachedAndTheMaxCount() {
		TemporaryWaypoints temps = new TemporaryWaypoints();
		temps.add(0, 70, 0, "A", "Kiwi", 0, 5);
		temps.tick(1, 3 * MINUTE, true, 2, 70, 1);
		assertTrue(temps.list().isEmpty(), "within three blocks: gone");
		for (int i = 0; i < 7; i++) {
			temps.add(i * 10, 70, 0, "P" + i, "Kiwi", i, 5);
		}
		assertEquals(5, temps.list().size());
		assertEquals("P2 · from Kiwi", temps.list().getFirst().point().name(), "the oldest went first");
	}

	@Test
	void neverInAGroupUntilSaved() {
		WaypointGroup active = new WaypointGroup("g1", "Waypoints", 0x5AC8FA);
		TemporaryWaypoints temps = new TemporaryWaypoints();
		TemporaryWaypoints.Temp temp = temps.add(12, 70, -30, "Corpse", "Kiwi", 0, 5);
		assertTrue(active.points().isEmpty(), "a waypoint from chat is in no group of the player's");
		assertEquals("temporary", temps.group().id());
		// Save takes it out of the temporary ones; the feature then adds it to the chosen group
		assertEquals(temp, temps.take(temp));
		assertTrue(temps.list().isEmpty());
		assertNull(temps.take(temp), "saved once only");
		temps.add(1, 70, 1, "B", "", 0, 5);
		temps.clear();
		assertTrue(temps.list().isEmpty(), "a world change clears them");
	}
}
