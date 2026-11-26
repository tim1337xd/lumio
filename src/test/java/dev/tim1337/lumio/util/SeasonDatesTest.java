package dev.tim1337.lumio.util;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonDatesTest {
	@Test
	void seasonsFollowTheMonths() {
		assertEquals(SeasonDates.WINTER, SeasonDates.seasonOf(LocalDate.of(2026, 1, 15)));
		assertEquals(SeasonDates.WINTER, SeasonDates.seasonOf(LocalDate.of(2026, 2, 28)));
		assertEquals(SeasonDates.SPRING, SeasonDates.seasonOf(LocalDate.of(2026, 3, 1)));
		assertEquals(SeasonDates.SPRING, SeasonDates.seasonOf(LocalDate.of(2026, 5, 31)));
		assertEquals(SeasonDates.SUMMER, SeasonDates.seasonOf(LocalDate.of(2026, 6, 1)));
		assertEquals(SeasonDates.SUMMER, SeasonDates.seasonOf(LocalDate.of(2026, 8, 31)));
		assertEquals(SeasonDates.AUTUMN, SeasonDates.seasonOf(LocalDate.of(2026, 9, 1)));
		assertEquals(SeasonDates.AUTUMN, SeasonDates.seasonOf(LocalDate.of(2026, 11, 30)));
		assertEquals(SeasonDates.WINTER, SeasonDates.seasonOf(LocalDate.of(2026, 12, 1)));
	}
}
