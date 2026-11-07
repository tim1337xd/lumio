package dev.tim1337.lumio.features.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Breaks a lyric line that is too wide into balanced rows: as few rows as fit, all about equally wide, broken at spaces, and never with one word
 * alone in a row when the row before could give it company. Works with any width measure, so it is tested without the game.
 */
final class LyricWrap {
	/** Steps of the search for the narrowest width that keeps the row count, in text units. */
	private static final double PRECISION = 0.5;

	private LyricWrap() {
	}

	/**
	 * @param maxWidth the widest a row may be
	 * @param mostRows the most rows; a line that needs more keeps the greedy rows at the full width
	 * @param width    the width of a text, as drawn
	 * @return the rows of the line: the line itself when it fits
	 */
	static List<String> balanced(String text, double maxWidth, int mostRows, ToDoubleFunction<String> width) {
		String[] words = words(text);
		if (words.length <= 1 || width.applyAsDouble(text) <= maxWidth) {
			return List.of(text);
		}
		List<String> greedy = greedy(words, maxWidth, width);
		int rows = greedy.size();
		if (rows > mostRows) {
			return greedy;
		}
		// the narrowest width at which the line still takes no more rows: then the rows are as even as breaking at spaces allows
		double low = 0;
		double high = maxWidth;
		while (high - low > PRECISION) {
			double middle = (low + high) / 2;
			if (greedy(words, middle, width).size() <= rows) {
				high = middle;
			} else {
				low = middle;
			}
		}
		List<String> balanced = greedy(words, high, width);
		return company(balanced, maxWidth, width);
	}

	/** @return the rows with a last row of one word given the last word of the row before, where that row keeps two or more and it fits */
	private static List<String> company(List<String> rows, double maxWidth, ToDoubleFunction<String> width) {
		int last = rows.size() - 1;
		if (last < 1 || words(rows.get(last)).length != 1) {
			return rows;
		}
		String[] before = words(rows.get(last - 1));
		if (before.length < 3) {
			return rows;
		}
		String moved = before[before.length - 1] + " " + rows.get(last);
		if (width.applyAsDouble(moved) > maxWidth) {
			return rows;
		}
		List<String> out = new ArrayList<>(rows);
		out.set(last - 1, String.join(" ", Arrays.copyOf(before, before.length - 1)));
		out.set(last, moved);
		return out;
	}

	/** @return the words breaking into rows no wider than the width, each row as full as it gets */
	private static List<String> greedy(String[] words, double maxWidth, ToDoubleFunction<String> width) {
		List<String> rows = new ArrayList<>();
		StringBuilder row = new StringBuilder();
		for (String word : words) {
			String candidate = row.isEmpty() ? word : row + " " + word;
			if (!row.isEmpty() && width.applyAsDouble(candidate) > maxWidth) {
				rows.add(row.toString());
				row = new StringBuilder(word);
			} else {
				row = new StringBuilder(candidate);
			}
		}
		if (!row.isEmpty()) {
			rows.add(row.toString());
		}
		return rows;
	}

	private static String[] words(String text) {
		String trimmed = text.strip();
		return trimmed.isEmpty() ? new String[0] : trimmed.split(" +");
	}
}
