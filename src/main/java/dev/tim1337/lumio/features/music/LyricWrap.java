package dev.tim1337.lumio.features.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Breaks a lyric line that is too wide into balanced rows: as few rows as fit, all about equally wide, broken at spaces, and never with one word
 * alone in a row when the row before could give it company. A word wider than a row (a long drawn-out word, a line in a script without spaces)
 * is broken inside, into even pieces, so it is read instead of running out of its row. Works with any width measure, so it is tested without
 * the game.
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
		if (text.indexOf(LyricGroups.BREAK) >= 0) {
			// a block of lines: each line keeps rows of its own, in order
			List<String> rows = new ArrayList<>();
			for (String line : text.split(String.valueOf(LyricGroups.BREAK))) {
				rows.addAll(balanced(line, maxWidth, mostRows, width));
			}
			if (rows.size() <= mostRows) {
				return rows;
			}
			// too many rows that way: as one line
			text = text.replace(LyricGroups.BREAK, ' ');
		}
		if (width.applyAsDouble(text) <= maxWidth) {
			return List.of(text);
		}
		// the pieces of a word broken inside stand together without a space
		List<String> pieces = new ArrayList<>();
		BitSet joined = new BitSet();
		for (String word : words(text)) {
			int before = pieces.size();
			pieces(word, maxWidth, width, pieces);
			joined.set(before + 1, pieces.size());
		}
		String[] words = pieces.toArray(new String[0]);
		if (words.length <= 1) {
			return List.of(text);
		}
		List<String> greedy = greedy(words, joined, maxWidth, width);
		int rows = greedy.size();
		if (rows > mostRows) {
			return greedy;
		}
		// the narrowest width at which the line still takes no more rows: then the rows are as even as breaking at spaces allows
		double low = 0;
		double high = maxWidth;
		while (high - low > PRECISION) {
			double middle = (low + high) / 2;
			if (greedy(words, joined, middle, width).size() <= rows) {
				high = middle;
			} else {
				low = middle;
			}
		}
		List<String> balanced = greedy(words, joined, high, width);
		// moving a word between rows puts a space before it: not for the pieces of one word
		return joined.isEmpty() ? company(balanced, maxWidth, width) : balanced;
	}

	/**
	 * Adds a word to the list: as it is when it fits a row (or cannot be broken), else in as few pieces as fit, all about as long, never cut
	 * inside a pair of chars that make one letter.
	 */
	private static void pieces(String word, double maxWidth, ToDoubleFunction<String> width, List<String> out) {
		double whole = width.applyAsDouble(word);
		int letters = word.codePointCount(0, word.length());
		if (whole <= maxWidth || letters < 2 || maxWidth <= 0) {
			out.add(word);
			return;
		}
		for (int count = (int) Math.ceil(whole / maxWidth); count <= letters; count++) {
			int start = out.size();
			boolean fits = true;
			int from = 0;
			for (int k = 1; k <= count && fits; k++) {
				int to = k == count ? word.length() : word.offsetByCodePoints(0, (int) ((long) letters * k / count));
				String piece = word.substring(from, to);
				// letter by letter is the last there is
				fits = count == letters || width.applyAsDouble(piece) <= maxWidth;
				out.add(piece);
				from = to;
			}
			if (fits) {
				return;
			}
			out.subList(start, out.size()).clear();
		}
		out.add(word);
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
	private static List<String> greedy(String[] words, BitSet joined, double maxWidth, ToDoubleFunction<String> width) {
		List<String> rows = new ArrayList<>();
		StringBuilder row = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			String candidate = row.isEmpty() ? word : row + (joined.get(i) ? "" : " ") + word;
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
