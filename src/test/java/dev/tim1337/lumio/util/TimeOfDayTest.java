package dev.tim1337.lumio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeOfDayTest {
	@Test
	void clockText() {
		assertEquals("23:08", TimeOfDay.clock(TimeOfDay.minutes(23f + 8f / 60f)));
		assertEquals("6:05", TimeOfDay.clock(TimeOfDay.minutes(6f + 5f / 60f)));
		assertEquals("0:00", TimeOfDay.clock(TimeOfDay.minutes(24f)));
	}

	@Test
	void snapsToFiveMinutesOrOne() {
		assertEquals(6f + 5f / 60f, TimeOfDay.snap(6f + 6f / 60f, 5), 1e-5f);
		assertEquals(6f + 6f / 60f, TimeOfDay.snap(6f + 6.2f / 60f, 1), 1e-5f);
		assertEquals(24f, TimeOfDay.snap(23.99f, 5), 1e-5f);
		assertEquals(0f, TimeOfDay.snap(-1f, 5), 1e-5f);
	}

	@Test
	void partsOfTheDay() {
		assertEquals("Night", TimeOfDay.partOfDay(23 * 60 + 8));
		assertEquals("Night", TimeOfDay.partOfDay(3 * 60));
		assertEquals("Morning", TimeOfDay.partOfDay(7 * 60));
		assertEquals("Noon", TimeOfDay.partOfDay(12 * 60));
		assertEquals("Afternoon", TimeOfDay.partOfDay(15 * 60));
		assertEquals("Evening", TimeOfDay.partOfDay(19 * 60));
	}

	@Test
	void glidesTheShortWayRoundMidnight() {
		assertEquals(23f, TimeOfDay.glide(23f, 1f, 0f), 1e-4f);
		assertEquals(0f, TimeOfDay.glide(23f, 1f, 0.5f), 1e-4f);
		assertEquals(1f, TimeOfDay.glide(23f, 1f, 1f), 1e-4f);
		assertEquals(12f, TimeOfDay.glide(8f, 16f, 0.5f), 1e-4f);
	}
}
