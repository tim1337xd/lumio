package dev.tim1337.lumio.features.visual.hud;

/**
 * Keeps the info bar from jumping as its values change: each number gets a slot sized for its usual largest value (FPS and ping three digits, TPS
 * "20.0", the clock with two hour digits, each coordinate with its sign and three digits), drawn in equal digit cells and right-aligned in it. A value
 * that needs more room widens its slot at once; the slot only narrows again once the shorter value has held for a few seconds, so it never flickers
 * between two widths. Texts such as the song and the area keep the same hold. Plain numbers, so it is unit tested.
 */
final class InfoBarSlots {
	/** How long a shorter value has to hold before its slot narrows. */
	static final long SHRINK_AFTER_MILLIS = 3_000L;
	/** How long the bar takes to grow or shrink to a new width. */
	static final float RESIZE_SECONDS = 0.15f;
	/** The digits a number's slot holds at least: FPS and ping. */
	static final int LEAST_DIGITS = 3;
	/** The digits each coordinate holds at least, after its sign. */
	static final int COORD_DIGITS = 3;

	private final float[] held;
	private final long[] shorterSince;

	InfoBarSlots(int count) {
		held = new float[count];
		shorterSince = new long[count];
	}

	/**
	 * @return the text whose width a number's slot keeps at least: the value with every digit a 0, made as long as the value's usual largest (so
	 *         99 FPS keeps the room of 100)
	 */
	static String template(InfoBarSegment.Kind kind, String text) {
		return switch (kind) {
			case FPS, PING, MEMORY, CPS -> padDigits(zeros(text), LEAST_DIGITS);
			case TPS -> longer(zeros(text), "00.0");
			case CLOCK -> clock(zeros(text));
			case COORDS -> coordinates(text);
			default -> zeros(text);
		};
	}

	/** @return the text with every digit a 0 */
	static String zeros(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			out.append(Character.isDigit(c) ? '0' : c);
		}
		return out.toString();
	}

	/** @return a number of zeros with at least so many digits: "00" becomes "000" */
	private static String padDigits(String zeros, int digits) {
		int have = 0;
		for (int i = 0; i < zeros.length(); i++) {
			if (zeros.charAt(i) == '0') {
				have++;
			}
		}
		return have >= digits ? zeros : "0".repeat(digits - have) + zeros;
	}

	/** @return a time with two hour digits: "0:00" keeps the room of "00:00" */
	private static String clock(String zeros) {
		return zeros.indexOf(':') == 1 ? "0" + zeros : zeros;
	}

	private static String longer(String a, String b) {
		return b.length() > a.length() ? b : a;
	}

	/** @return the coordinates with each number as a sign and at least three digits: "12, 64, -250" keeps "-000, -000, -000" */
	private static String coordinates(String text) {
		StringBuilder out = new StringBuilder(text.length() + 8);
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '-' || Character.isDigit(c)) {
				int start = c == '-' ? i + 1 : i;
				int end = start;
				while (end < text.length() && (Character.isDigit(text.charAt(end)) || text.charAt(end) == '.')) {
					end++;
				}
				String number = zeros(text.substring(start, end));
				int digits = number.replace(".", "").length();
				out.append('-').append("0".repeat(Math.max(0, COORD_DIGITS - digits))).append(number);
				i = end;
			} else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	/**
	 * @param needed what the value needs now
	 * @return the width a segment's value keeps: what it needs when that is more than before, at once; less only after the shorter value held
	 */
	float hold(int segment, float needed, long nowMillis) {
		float kept = held[segment];
		if (needed >= kept - 0.01f || kept <= 0f) {
			held[segment] = needed;
			shorterSince[segment] = 0L;
			return needed;
		}
		if (shorterSince[segment] == 0L) {
			shorterSince[segment] = nowMillis;
		}
		if (nowMillis - shorterSince[segment] >= SHRINK_AFTER_MILLIS) {
			held[segment] = needed;
			shorterSince[segment] = 0L;
			return needed;
		}
		return kept;
	}

	/** @return when a held slot may narrow next, so the bar is measured again then; Long.MAX_VALUE while none waits */
	long nextChange() {
		long next = Long.MAX_VALUE;
		for (long since : shorterSince) {
			if (since != 0L) {
				next = Math.min(next, since + SHRINK_AFTER_MILLIS);
			}
		}
		return next;
	}

	/** Forgets what was held: the bar's look or options changed. */
	void reset() {
		java.util.Arrays.fill(held, 0f);
		java.util.Arrays.fill(shorterSince, 0L);
	}
}
