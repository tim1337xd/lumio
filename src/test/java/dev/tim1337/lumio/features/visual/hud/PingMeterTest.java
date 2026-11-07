package dev.tim1337.lumio.features.visual.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The info bar's ping: one request every four seconds, one at a time, given up after ten, the average of the last ten answers. */
class PingMeterTest {
	private static final Object SERVER = new Object();
	private static final Object WORLD = new Object();

	/** Sends a request at a time and answers it after a round trip. */
	private static void roundTrip(PingMeter meter, long at, long millis) {
		assertTrue(meter.due(SERVER, WORLD, at), "a request goes out at " + at);
		meter.answered(at, at + millis);
	}

	@Test
	void oneRequestEveryFourSeconds() {
		PingMeter meter = new PingMeter();
		assertTrue(meter.due(SERVER, WORLD, 0L));
		meter.answered(0L, 40L);
		assertFalse(meter.due(SERVER, WORLD, 1000L));
		assertFalse(meter.due(SERVER, WORLD, PingMeter.INTERVAL_MILLIS - 1));
		assertTrue(meter.due(SERVER, WORLD, PingMeter.INTERVAL_MILLIS));
	}

	@Test
	void neverMoreThanOneWaiting() {
		PingMeter meter = new PingMeter();
		assertTrue(meter.due(SERVER, WORLD, 0L));
		// no answer yet: no second request, also after the interval
		assertFalse(meter.due(SERVER, WORLD, PingMeter.INTERVAL_MILLIS));
		assertFalse(meter.due(SERVER, WORLD, PingMeter.TIMEOUT_MILLIS));
		// given up after ten seconds: the next one goes out, and the late answer to the old one does not count
		assertTrue(meter.due(SERVER, WORLD, PingMeter.TIMEOUT_MILLIS + 1));
		meter.answered(0L, PingMeter.TIMEOUT_MILLIS + 2);
		assertEquals(PingMeter.NONE, meter.average());
	}

	@Test
	void theAverageOfTheLastTenAnswers() {
		PingMeter meter = new PingMeter();
		assertEquals(PingMeter.NONE, meter.average());
		long at = 0L;
		// ten slow answers, then ten fast ones: only the fast ones count
		for (int i = 0; i < PingMeter.SAMPLES; i++, at += PingMeter.INTERVAL_MILLIS) {
			roundTrip(meter, at, 300L);
		}
		assertEquals(300, meter.average());
		for (int i = 0; i < PingMeter.SAMPLES; i++, at += PingMeter.INTERVAL_MILLIS) {
			roundTrip(meter, at, 40L + i % 2 * 20L);
		}
		assertEquals(50, meter.average());
	}

	@Test
	void answersToOtherRequestsAreIgnored() {
		PingMeter meter = new PingMeter();
		assertTrue(meter.due(SERVER, WORLD, 1000L));
		// the game's own F3 chart sends requests too: an answer with another timestamp is not this meter's
		meter.answered(990L, 1030L);
		assertEquals(PingMeter.NONE, meter.average());
		meter.answered(1000L, 1080L);
		assertEquals(80, meter.average());
		// the same answer twice counts once
		meter.answered(1000L, 1500L);
		assertEquals(80, meter.average());
	}

	@Test
	void anotherServerOrWorldStartsOver() {
		PingMeter meter = new PingMeter();
		roundTrip(meter, 0L, 200L);
		assertEquals(200, meter.average());
		// another world on the same server (a server change on a network): forgotten, and a request goes out at once
		assertTrue(meter.due(SERVER, new Object(), 1000L));
		assertEquals(PingMeter.NONE, meter.average());
		meter.answered(1000L, 1030L);
		assertEquals(30, meter.average());
		assertTrue(meter.due(new Object(), new Object(), 2000L));
		assertEquals(PingMeter.NONE, meter.average());
	}
}
