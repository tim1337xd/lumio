package dev.tim1337.lumio.ui.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One click on Rejoin is one connection; it stays closed until the connect screen is gone, and for a minute after a refused login. */
class RejoinGateTest {
	private int connects;

	private boolean click(RejoinGate gate, long now) {
		return gate.click(now, () -> connects++);
	}

	@Test
	void oneClickGivesExactlyOneConnect() {
		RejoinGate gate = new RejoinGate();
		assertTrue(click(gate, 0L));
		// a double click, a second event of the same press
		assertFalse(click(gate, 0L));
		assertFalse(click(gate, 40L));
		assertEquals(1, connects);
	}

	@Test
	void itOpensAgainWhenTheConnectScreenIsGone() {
		RejoinGate gate = new RejoinGate();
		click(gate, 0L);
		assertFalse(gate.ready(1_000L));
		gate.connectEnded();
		assertTrue(gate.ready(1_000L));
		assertTrue(click(gate, 1_000L));
		assertEquals(2, connects);
	}

	@Test
	void aConnectThatThrowsLeavesItOpen() {
		RejoinGate gate = new RejoinGate();
		try {
			gate.click(0L, () -> {
				throw new IllegalStateException();
			});
		} catch (IllegalStateException expected) {
			assertTrue(gate.ready(0L));
			return;
		}
		throw new AssertionError("the failure is passed on");
	}

	@Test
	void aBusyLoginServerClosesItForAMinute() {
		RejoinGate gate = new RejoinGate();
		click(gate, 0L);
		gate.loginBusy(1_000L);
		assertEquals(60, gate.secondsLeft(1_000L));
		assertEquals(1, gate.secondsLeft(60_999L));
		assertFalse(click(gate, 60_999L));
		assertEquals(0, gate.secondsLeft(61_000L));
		assertTrue(click(gate, 61_000L));
		assertEquals(2, connects);
	}

	@Test
	void onlyARefusedLoginCountsAsBusy() {
		assertTrue(RejoinGate.busyReason("Failed to log in: Status: 429"));
		assertTrue(RejoinGate.busyReason("Anmeldung fehlgeschlagen: Status: 429"));
		assertFalse(RejoinGate.busyReason("Failed to log in: Status: 4290"));
		assertFalse(RejoinGate.busyReason("You earned 429 coins"));
		assertFalse(RejoinGate.busyReason(null));
	}

	@Test
	void theCountsStartAnewWithEachClick() {
		RejoinGate gate = new RejoinGate();
		click(gate, 0L);
		assertEquals(1, gate.countConnectScreen());
		assertEquals(1, gate.countSessionJoin());
		gate.connectEnded();
		click(gate, 1L);
		assertEquals(1, gate.countConnectScreen());
		assertEquals(1, gate.countSessionJoin());
	}
}
