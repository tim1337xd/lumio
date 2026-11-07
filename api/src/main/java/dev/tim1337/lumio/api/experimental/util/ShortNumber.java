package dev.tim1337.lumio.api.experimental.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Writes a big number short, as the game's bank line does: at most three significant digits with K, M, B or T, no trailing zeros. 100,081,263 is 100M, 12,345,678 is 12.3M, 1,250,000,000 is 1.25B, under 1,000 as it is. */
public final class ShortNumber {
	private static final Pattern NUMBER = Pattern.compile("-?[0-9][0-9,]*(?:\\.[0-9]+)?");
	private static final String[] UNITS = {"", "K", "M", "B", "T"};
	private static final int UNIT_DIGITS = 3;
	private static final int LONGEST_TEXT = 40;
	private static final MathContext THREE_DIGITS = new MathContext(UNIT_DIGITS, RoundingMode.HALF_UP);
	private static final BigDecimal LEAST = BigDecimal.valueOf(1000);

	private ShortNumber() {
	}

	/** @return a number written short; a text that is no number, and a number under 1,000, as it is */
	public static String of(String text) {
		if (text.length() > LONGEST_TEXT || !NUMBER.matcher(text).matches()) {
			return text;
		}
		BigDecimal number = new BigDecimal(text.replace(",", ""));
		if (number.abs().compareTo(LEAST) < 0) {
			return text;
		}
		BigDecimal rounded = number.round(THREE_DIGITS);
		// from the rounded number: 999,999 is 1M, not 1000K
		int unit = Math.min(UNITS.length - 1, (rounded.precision() - rounded.scale() - 1) / UNIT_DIGITS);
		return rounded.movePointLeft(unit * UNIT_DIGITS).stripTrailingZeros().toPlainString() + UNITS[unit];
	}
}
