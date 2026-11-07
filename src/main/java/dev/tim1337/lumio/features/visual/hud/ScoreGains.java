package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.util.ShortNumber;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the values of the scoreboard just gained, by their label: a small overlay that takes no room in its row, so that a value never moves for it. Gains in a row add up in one label. Plain numbers, so it is unit tested.
 */
final class ScoreGains {
	/** How long a gain shows, how long of that it slides in, and how long it takes to fade at the end. */
	static final long SHOWS_MILLIS = 1500L;
	private static final long SLIDES_MILLIS = 180L;
	private static final long FADES_MILLIS = 500L;
	private static final int MOST_LABELS = 16;
	private static final double THOUSAND = 1000.0;
	/** From here on a sum is written short, as the scoreboard's numbers are. */
	private static final double WRITTEN_SHORT_FROM = 10000.0;

	/** Where an overlay goes: in the gap left of its value, or above the value's right edge where the gap is too small. */
	enum Place {
		BESIDE, ABOVE
	}

	private static final class Entry {
		String lastGain = "";
		double lastValue = Double.NaN;
		double total;
		String text = "";
		long since;
	}

	private final Map<String, Entry> entries = new HashMap<>();

	/**
	 * What a row of the scoreboard says now.
	 *
	 * @param label a value's label
	 * @param gain  what it gained as the server writes it ("+12"), or an empty text
	 * @param value the value as the server writes it
	 */
	void see(String label, String gain, String value, long now) {
		if (gain.isEmpty() && !entries.containsKey(label)) {
			return;
		}
		Entry entry = entries.get(label);
		if (entry == null) {
			if (entries.size() >= MOST_LABELS) {
				entries.clear();
			}
			entry = new Entry();
			entries.put(label, entry);
		}
		double number = amount(value);
		// the same gain written again is a new one only if the value went up with it
		boolean again = gain.equals(entry.lastGain) && number > entry.lastValue;
		if (!gain.isEmpty() && (!gain.equals(entry.lastGain) || again)) {
			double gained = amount(gain);
			boolean running = now - entry.since < SHOWS_MILLIS && !entry.text.isEmpty();
			entry.total = running ? entry.total + gained : gained;
			entry.text = Double.isNaN(entry.total) ? gain : written(entry.total);
			entry.since = now;
		}
		entry.lastGain = gain;
		entry.lastValue = number;
	}

	/** @return what a label shows now, or null when nothing */
	String text(String label, long now) {
		Entry entry = entries.get(label);
		return entry == null || entry.text.isEmpty() || now - entry.since >= SHOWS_MILLIS ? null : entry.text;
	}

	/** @return how far a label's overlay is from having slid in, from 1 at its start to 0 */
	float slide(String label, long now) {
		Entry entry = entries.get(label);
		return entry == null ? 0f : slideAt(now - entry.since);
	}

	/** @return how much of a label's overlay shows, from 0 to 1 */
	float opacity(String label, long now) {
		Entry entry = entries.get(label);
		return entry == null ? 0f : opacityAt(now - entry.since);
	}

	static float slideAt(long age) {
		float left = 1f - (float) age / SLIDES_MILLIS;
		return left <= 0f ? 0f : left * left;
	}

	static float opacityAt(long age) {
		if (age >= SHOWS_MILLIS) {
			return 0f;
		}
		float in = Math.min(1f, (float) age / SLIDES_MILLIS);
		float out = Math.min(1f, (float) (SHOWS_MILLIS - age) / FADES_MILLIS);
		return Math.min(in, out);
	}

	/** @return where a value's text starts: from the row's right edge alone, whatever it gained */
	static float valueLeft(float rowRight, float valueWidth) {
		return rowRight - valueWidth;
	}

	/** @return the room a row with a value takes: an overlay adds none */
	static float rowWidth(float labelWidth, float valueWidth, float gap) {
		return labelWidth + valueWidth + gap;
	}

	/**
	 * @param labelEnd   where the row's label ends
	 * @param valueLeft  where its value starts
	 * @param gainWidth  how wide the overlay is
	 * @param gap        the room it keeps to both
	 * @return where the overlay goes
	 */
	static Place place(float labelEnd, float valueLeft, float gainWidth, float gap) {
		return valueLeft - labelEnd >= gainWidth + 2 * gap ? Place.BESIDE : Place.ABOVE;
	}

	/** @return a number as the server writes it ("+1,500", "1.5K", "689K"), or NaN */
	static double amount(String text) {
		String plain = text.strip().replace("+", "").replace(",", "");
		if (plain.isEmpty()) {
			return Double.NaN;
		}
		double unit = switch (Character.toLowerCase(plain.charAt(plain.length() - 1))) {
			case 'k' -> THOUSAND;
			case 'm' -> THOUSAND * THOUSAND;
			case 'b' -> THOUSAND * THOUSAND * THOUSAND;
			default -> 1.0;
		};
		try {
			return Double.parseDouble(unit == 1.0 ? plain : plain.substring(0, plain.length() - 1)) * unit;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** @return a sum as the overlay writes it: "+17", "+1,512", "+12.3K" */
	static String written(double total) {
		if (total >= WRITTEN_SHORT_FROM) {
			return "+" + ShortNumber.of(String.format(Locale.ROOT, "%.0f", total));
		}
		if (total == Math.rint(total)) {
			return String.format(Locale.ROOT, "+%,.0f", total);
		}
		return String.format(Locale.ROOT, "+%,.1f", total);
	}
}
