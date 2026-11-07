package dev.tim1337.lumio.util;

/**
 * Times of the day for the main menu's world: an hour from 0 to 24 as minutes of the day, its clock text and part of the day, snapping to steps,
 * and the glide from one hour to another the short way round midnight. Plain logic without the game, so it can be tested on its own.
 */
public final class TimeOfDay {
	public static final int MINUTES_PER_DAY = 24 * 60;

	private TimeOfDay() {
	}

	/** @return the minute of the day, from 0 to 1439, nearest to an hour from 0 to 24; 24 is midnight again */
	public static int minutes(float hour) {
		return Math.floorMod(Math.round(hour * 60f), MINUTES_PER_DAY);
	}

	/** @return an hour from 0 up to 24 on a step of minutes (5, or 1 with Shift); the end of the day stays 24, so a slider can reach its end */
	public static float snap(float hour, int stepMinutes) {
		float clamped = Math.max(0f, Math.min(24f, hour));
		int steps = Math.round(clamped * 60f / stepMinutes);
		return Math.min(MINUTES_PER_DAY, steps * stepMinutes) / 60f;
	}

	/** @return the clock text of a minute of the day: "23:08", "6:05" */
	public static String clock(int minutes) {
		int hour = minutes / 60;
		int minute = minutes % 60;
		return hour + (minute < 10 ? ":0" : ":") + minute;
	}

	/** @return the part of the day a minute of it falls in: "Night", "Morning", "Noon", "Afternoon" or "Evening" */
	public static String partOfDay(int minutes) {
		int hour = minutes / 60;
		if (hour < 5 || hour >= 22) {
			return "Night";
		}
		if (hour < 11) {
			return "Morning";
		}
		if (hour < 14) {
			return "Noon";
		}
		return hour < 18 ? "Afternoon" : "Evening";
	}

	/**
	 * @param from the hour the glide starts at, from 0 to 24
	 * @param to the hour it ends at
	 * @param progress how far it is, from 0 to 1, eased so it starts and ends softly
	 * @return the hour on the way, going the short way round midnight
	 */
	public static float glide(float from, float to, float progress) {
		float t = Math.max(0f, Math.min(1f, progress));
		float eased = t * t * (3f - 2f * t);
		float way = ((to - from) % 24f + 36f) % 24f - 12f;
		return ((from + way * eased) % 24f + 24f) % 24f;
	}
}
