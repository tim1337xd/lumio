package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Splits a line that is too long for the lyrics in the world, even in all its rows at the smallest text size, into parts shown one after the
 * other: each part a line of its own that starts when its first word is sung. It is cut between two words near its middle, where the singer
 * pauses longest when the words have timings; without timings the second part starts at its share of the line's time, by its letters. A line
 * of one word, or without any space, is cut in its middle (the rows it is shown in break inside the word anyway). Lines that fit stay as they
 * are, the same objects. Plain logic, tested without the game.
 */
final class LyricSplit {
	/** The most parts a line is split into. */
	static final int MOST_PARTS = 8;
	/** A cut lies this near the line's middle, as a share of its length to either side, when a space is there. */
	private static final double NEAR_MIDDLE = 0.2;
	/** The time a letter takes in a last line without timings, whose end nothing tells. */
	private static final long LETTER_MILLIS = 60;

	private LyricSplit() {
	}

	/**
	 * @param fits whether a text can be shown whole
	 * @return the lines with every line that does not fit split into parts that do, where it can be cut; the list itself when all fit
	 */
	static List<LyricLine> split(List<LyricLine> lines, Predicate<String> fits) {
		return split(lines, fits, new BitSet());
	}

	/**
	 * The same, noting the parts after a line's first one by their index in the result: the player still reports the whole line as sung while
	 * they start, so the lyrics go by the clock for them.
	 */
	static List<LyricLine> split(List<LyricLine> lines, Predicate<String> fits, BitSet later) {
		later.clear();
		List<LyricLine> out = null;
		for (int i = 0; i < lines.size(); i++) {
			LyricLine line = lines.get(i);
			List<LyricLine> parts = List.of(line);
			if (LyricTimes.main(line) && !LyricBreaks.is(line) && line.text().indexOf(LyricGroups.BREAK) < 0 && !fits.test(line.text())) {
				parts = parts(line, end(lines, i), fits, MOST_PARTS);
			}
			if (parts.size() > 1 && out == null) {
				out = new ArrayList<>(lines.subList(0, i));
			}
			if (out != null) {
				if (parts.size() > 1) {
					later.set(out.size() + 1, out.size() + parts.size());
				}
				out.addAll(parts);
			}
		}
		return out == null ? lines : out;
	}

	/** @return when a line is over: the next line's start, or for the last line its last word's end or a guess by its letters */
	private static long end(List<LyricLine> lines, int index) {
		LyricLine line = lines.get(index);
		if (index + 1 < lines.size() && lines.get(index + 1).timeMs() > line.timeMs()) {
			return lines.get(index + 1).timeMs();
		}
		if (!line.words().isEmpty()) {
			return Math.max(line.timeMs(), line.words().get(line.words().size() - 1).endMs());
		}
		return line.timeMs() + line.text().length() * LETTER_MILLIS;
	}

	private static List<LyricLine> parts(LyricLine line, long endMs, Predicate<String> fits, int most) {
		String text = line.text();
		int cut = most < 2 || fits.test(text) ? -1 : cutAt(line);
		if (cut < 0) {
			return List.of(line);
		}
		int second = cut;
		while (second < text.length() && text.charAt(second) == ' ') {
			second++;
		}
		long secondMs = Math.round(line.timeMs() + (double) (endMs - line.timeMs()) * second / text.length());
		List<Word> firstWords = new ArrayList<>();
		List<Word> secondWords = new ArrayList<>();
		for (Word word : line.words()) {
			if (word.from() >= second) {
				if (secondWords.isEmpty()) {
					secondMs = word.startMs();
				}
				secondWords.add(new Word(word.from() - second, word.to() - second, word.startMs(), word.endMs()));
			} else {
				firstWords.add(new Word(word.from(), Math.min(word.to(), cut), word.startMs(), word.endMs()));
			}
		}
		secondMs = Math.max(line.timeMs() + 1, Math.min(secondMs, Math.max(line.timeMs() + 1, endMs)));
		// the adlibs sung alongside stay with the first part, which stands when they start
		LyricLine first = new LyricLine(line.timeMs(), text.substring(0, cut), firstWords, line.backgrounds(), line.singer());
		LyricLine rest = new LyricLine(secondMs, text.substring(second), secondWords, List.of(), line.singer());
		List<LyricLine> out = new ArrayList<>(parts(first, secondMs, fits, most / 2));
		out.addAll(parts(rest, endMs, fits, most - most / 2));
		return out;
	}

	/** @return the index of the space a line is cut at, or -1 when it has none between two words */
	private static int cutAt(LyricLine line) {
		String text = line.text();
		double middle = text.length() / 2.0;
		int nearest = -1;
		int pause = -1;
		long longest = -1;
		for (int i = 1; i < text.length() - 1; i++) {
			if (text.charAt(i) != ' ' || text.charAt(i - 1) == ' ') {
				continue;
			}
			if (nearest < 0 || Math.abs(i - middle) < Math.abs(nearest - middle)) {
				nearest = i;
			}
			if (Math.abs(i - middle) <= text.length() * NEAR_MIDDLE) {
				long gap = pauseAt(line, i);
				if (gap > longest) {
					longest = gap;
					pause = i;
				}
			}
		}
		if (nearest < 0) {
			// no space at all (a script without them, one endless word): cut in the middle, never inside a pair of chars that make one letter
			int half = text.length() / 2;
			half -= half > 0 && Character.isLowSurrogate(text.charAt(half)) ? 1 : 0;
			return half > 0 && text.indexOf(' ') < 0 ? half : -1;
		}
		// a pause counts only when the words say so; else the space nearest the middle
		return pause >= 0 && longest > 0 ? pause : nearest;
	}

	/** @return how long the singer pauses at a space: from the end of the word before it to the start of the word after it; 0 without timings */
	private static long pauseAt(LyricLine line, int space) {
		long before = Long.MIN_VALUE;
		long after = Long.MIN_VALUE;
		for (Word word : line.words()) {
			if (word.to() <= space) {
				before = word.endMs();
			} else if (word.from() > space && after == Long.MIN_VALUE) {
				after = word.startMs();
			}
		}
		return before == Long.MIN_VALUE || after == Long.MIN_VALUE ? 0 : Math.max(0, after - before);
	}
}
