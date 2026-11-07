package dev.tim1337.lumio.config.setting;

import java.util.Locale;

/**
 * Amounts of coins as a slider shows and reads them: "750k", "2.5M", "1.25B". Plain strings and numbers, so it is unit tested without the game.
 */
public final class CoinAmount {
	private static final double THOUSAND = 1_000d;
	private static final double MILLION = 1_000_000d;
	private static final double BILLION = 1_000_000_000d;
	/** The most characters read, so a pasted text cannot make the reading slow. */
	private static final int MOST_LENGTH = 16;

	private CoinAmount() {
	}

	/** @return the amount short, with at most two decimals and none that are zero: "100k", "2.5M", "1.25B" */
	public static String format(double coins) {
		double size = Math.abs(coins);
		String sign = coins < 0 ? "-" : "";
		if (size < THOUSAND) {
			return sign + Math.round(size);
		}
		if (size < MILLION) {
			return sign + trimmed(size / THOUSAND) + "k";
		}
		if (size < BILLION) {
			return sign + trimmed(size / MILLION) + "M";
		}
		return sign + trimmed(size / BILLION) + "B";
	}

	/** @return a number with up to two decimals, without trailing zeros, without String.format */
	private static String trimmed(double value) {
		long hundredths = Math.round(value * 100d);
		long whole = hundredths / 100;
		long rest = hundredths % 100;
		if (rest == 0) {
			return Long.toString(whole);
		}
		if (rest % 10 == 0) {
			return whole + "." + rest / 10;
		}
		return whole + "." + (rest < 10 ? "0" : "") + rest;
	}

	/**
	 * Reads a typed amount: a number with an optional k, M or B behind it ("750k", "2.5M", "2,5m", "1b"), or the coins written out with or
	 * without separators ("2500000", "2,500,000").
	 *
	 * @return the amount, or NaN when the text is none
	 */
	public static double parse(String text) {
		if (text == null) {
			return Double.NaN;
		}
		String clean = text.strip().replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
		if (clean.isEmpty() || clean.length() > MOST_LENGTH) {
			return Double.NaN;
		}
		double factor = switch (clean.charAt(clean.length() - 1)) {
			case 'k' -> THOUSAND;
			case 'm' -> MILLION;
			case 'b' -> BILLION;
			default -> 1d;
		};
		String number = factor == 1d ? clean : clean.substring(0, clean.length() - 1);
		if (factor == 1d) {
			// written out: commas and dots between groups of three are separators ("2,500,000", "2.500.000")
			number = separatorsRemoved(number);
		} else {
			number = number.replace(',', '.');
		}
		if (number.isEmpty()) {
			return Double.NaN;
		}
		for (int i = 0; i < number.length(); i++) {
			char c = number.charAt(i);
			if ((c < '0' || c > '9') && c != '.') {
				return Double.NaN;
			}
		}
		try {
			return Double.parseDouble(number) * factor;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** @return the number without the separators between groups of three digits; a single comma or dot before fewer digits is a decimal point */
	private static String separatorsRemoved(String number) {
		int separators = 0;
		int last = -1;
		for (int i = 0; i < number.length(); i++) {
			char c = number.charAt(i);
			if (c == ',' || c == '.') {
				separators++;
				last = i;
			}
		}
		if (separators == 0) {
			return number;
		}
		if (separators == 1 && number.length() - last - 1 != 3) {
			return number.replace(',', '.');
		}
		return number.replace(",", "").replace(".", "");
	}
}
