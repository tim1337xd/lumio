package dev.tim1337.lumio.util;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The meteorological season of a date, for the main menu's world when it follows the date: winter December to February, spring March to May,
 * summer June to August, autumn September to November. Plain logic without the game, so it can be tested on its own.
 */
public final class SeasonDates {
	// season numbers, in the order of the menu world's seasons
	public static final int WINTER = 0;
	public static final int SPRING = 1;
	public static final int SUMMER = 2;
	public static final int AUTUMN = 3;

	private SeasonDates() {
	}

	/** @return the season a date falls in */
	public static int seasonOf(LocalDate date) {
		int month = date.getMonthValue();
		if (month == 12 || month <= 2) {
			return WINTER;
		}
		return month <= 5 ? SPRING : month <= 8 ? SUMMER : AUTUMN;
	}

	/** @return the last day of the season a date falls in: winter that starts in December ends in February of the next year */
	public static LocalDate lastDay(LocalDate date) {
		int month = date.getMonthValue();
		int season = seasonOf(date);
		int endMonth = season == WINTER ? 2 : season == SPRING ? 5 : season == SUMMER ? 8 : 11;
		int year = month == 12 ? date.getYear() + 1 : date.getYear();
		return YearMonth.of(year, endMonth).atEndOfMonth();
	}
}
