package dev.tim1337.lumio.api.experimental.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Server ticks from the server's tick pings: each ping once, the same ping twice is one tick, pings with id 0 are no ticks. */
class ServerTicksTest {
	@Test
	void aRepeatedPingIsOneTick() {
		assertEquals(3, ticks(-101, -101, -102, -102, -103));
	}

	@Test
	void pingsWithIdZeroAreSkipped() {
		assertEquals(2, ticks(0, -5, 0, 0, -6, 0));
		assertEquals(0, ticks(0, 0, 0));
	}

	private static int ticks(int... pings) {
		int last = 0;
		int ticks = 0;
		for (int id : pings) {
			if (ServerTicks.newTick(id, last)) {
				ticks++;
				last = id;
			}
		}
		return ticks;
	}
}
