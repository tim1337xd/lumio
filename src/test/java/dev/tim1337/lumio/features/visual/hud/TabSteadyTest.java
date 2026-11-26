package dev.tim1337.lumio.features.visual.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tab list's steady size: steps, growing at once, shrinking late and one step at a time, and the size remembered per place. */
class TabSteadyTest {
	private static final long TEN_SECONDS = 10_000L;

	@Test
	void theRoomIsWholeSteps() {
		TabSteady steady = new TabSteady(5);
		assertEquals(5, steady.stepped(1));
		assertEquals(5, steady.stepped(5));
		assertEquals(10, steady.stepped(6));
		assertEquals(25, steady.stepped(23));
		// an empty list still has one step
		assertEquals(5, steady.stepped(0));
	}

	@Test
	void aSmallLobbyReservesTheSmallestStepThatFits() {
		// a dungeon party of four does not get twenty rows
		TabSteady steady = new TabSteady(5);
		assertEquals(5, steady.open("dungeon", 4));
	}

	@Test
	void itGrowsAtOnceWhenPlayersDoNotFit() {
		TabSteady steady = new TabSteady(5);
		steady.open("hub", 18);
		assertEquals(20, steady.reserved());
		// two more still fit: nothing changes
		assertEquals(20, steady.update(20, 1_000, TEN_SECONDS));
		// the 21st does not: the next step, now
		assertEquals(25, steady.update(21, 1_100, TEN_SECONDS));
		// several at once: straight to the step that holds them
		assertEquals(40, steady.update(38, 1_200, TEN_SECONDS));
	}

	@Test
	void itShrinksOnlyAfterTheDelayAndOneStepAtATime() {
		TabSteady steady = new TabSteady(5);
		steady.open("hub", 38);
		assertEquals(40, steady.reserved());
		// players leave: the room stays while the wait runs
		assertEquals(40, steady.update(12, 0, TEN_SECONDS));
		assertEquals(40, steady.update(12, 9_999, TEN_SECONDS));
		// after the delay one step, not all the way down
		assertEquals(35, steady.update(12, 10_000, TEN_SECONDS));
		assertEquals(35, steady.update(12, 15_000, TEN_SECONDS));
		assertEquals(30, steady.update(12, 20_000, TEN_SECONDS));
	}

	@Test
	void leavingAndJoiningAgainNeverMovesIt() {
		TabSteady steady = new TabSteady(5);
		steady.open("hub", 20);
		// someone leaves and comes back within the delay, over and over: the size stays
		for (long time = 0; time < 60_000; time += 4_000) {
			assertEquals(20, steady.update(time / 4_000 % 2 == 0 ? 14 : 20, time, TEN_SECONDS));
		}
		// fewer players, but not below the next smaller step: no shrinking however long
		assertEquals(20, steady.update(16, 100_000, TEN_SECONDS));
		assertEquals(20, steady.update(16, 500_000, TEN_SECONDS));
	}

	@Test
	void neverWhileOpenNeverShrinks() {
		TabSteady steady = new TabSteady(5);
		steady.open("hub", 38);
		assertEquals(40, steady.update(3, 0, TabSteady.NEVER));
		assertEquals(40, steady.update(3, 10_000_000, TabSteady.NEVER));
	}

	@Test
	void theLargestSizeOfAPlaceIsRememberedForTheSession() {
		TabSteady steady = new TabSteady(5);
		steady.open("hub", 12);
		steady.update(33, 0, TEN_SECONDS);
		// opened again in the hub with a few players fewer: it starts from the size it had, so it does not jump on opening
		assertEquals(35, steady.open("hub", 27));
		// another kind of place has its own
		assertEquals(5, steady.open("dungeon", 5));
		assertEquals(35, steady.open("hub", 27));
		// after it shrank there, the smaller size is what is remembered
		steady.update(22, 0, TEN_SECONDS);
		steady.update(22, 10_000, TEN_SECONDS);
		assertEquals(30, steady.open("hub", 22));
		// a nearly empty hub does not open with the room of a full one
		assertEquals(10, steady.open("hub", 9));
	}

	@Test
	void theInfoSidesHeightFollowsTheSameRulesInPixels() {
		TabSteady height = new TabSteady(12);
		assertEquals(132, height.open("hub", 130));
		assertEquals(144, height.update(140, 0, TEN_SECONDS));
		assertEquals(144, height.update(100, 5_000, TEN_SECONDS));
		assertEquals(132, height.update(100, 15_000, TEN_SECONDS));
	}

	@Test
	void playersThatFitOneColumnAreNotSpreadOverTwo() {
		// a column holds twelve: eleven or twelve players reserve twelve, not fifteen, which would need a second column
		TabSteady steady = new TabSteady(5, 12);
		assertEquals(12, steady.stepped(11));
		assertEquals(12, steady.stepped(12));
		assertEquals(15, steady.stepped(13));
		assertEquals(24, steady.stepped(23));
		assertEquals(25, steady.stepped(25));
		assertEquals(5, steady.stepped(4));
	}
}
