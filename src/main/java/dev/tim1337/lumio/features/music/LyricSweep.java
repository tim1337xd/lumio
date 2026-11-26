package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import java.util.ArrayList;
import java.util.List;

/**
 * How far a line is sung at a time, the same in the world, the HUD and the info bar. A line with word timings follows them exactly. A line
 * without (line-synced lyrics) gets estimated ones ({@link LyricEstimate}: its words spread over the line's time by their syllables, with a
 * pause at commas), so its highlight moves through the line as with real timings: from exactly 0 at the line's own start, never from the line
 * before. The estimates of the last few lines are kept, so asking every frame makes nothing new. Plain logic, tested without the game.
 */
final class LyricSweep {
	/** The estimates kept: a few lines are drawn at a time (the world, the HUD, the info bar, a block's lines). */
	private static final int KEPT = 8;
	private static final Estimate[] KEPT_ESTIMATES = new Estimate[KEPT];
	private static int nextSlot;

	/** A line's estimated words, for its text, start and the next line's start. */
	private record Estimate(String text, long startMs, long nextStartMs, List<Word> words) {
	}

	private LyricSweep() {
	}

	/**
	 * @param nextStartMs when the next line starts; only asked for a line without word timings
	 * @return how many characters of a line are sung at a time, fractional: by its word timings where it has them, else by the estimated ones
	 */
	static float sung(LyricLine line, long nextStartMs, long at) {
		return line.words().isEmpty() ? chars(line.text(), line.timeMs(), nextStartMs, at) : along(line.words(), at);
	}

	/**
	 * @param nextStartMs when the next line starts, or anything not after the start when there is none
	 * @return how many characters of a line without word timings are lit at a time: 0 before and at its start, all of them once its last
	 *         estimated word is over
	 */
	static float chars(String text, long startMs, long nextStartMs, long at) {
		if (at <= startMs) {
			return 0f;
		}
		List<Word> words = estimate(text, startMs, nextStartMs);
		if (words.isEmpty() || at >= words.getLast().endMs()) {
			return text.length();
		}
		return along(words, at);
	}

	/** @return how long a line without word timings takes to be sung, by its estimated words */
	static long duration(String text, long startMs, long nextStartMs) {
		List<Word> words = estimate(text, startMs, nextStartMs);
		return words.isEmpty() ? 1L : Math.max(1L, words.getLast().endMs() - startMs);
	}

	/**
	 * The estimated words of a line without word timings, for a line that is part of a block: the block's progress then runs through every line
	 * of it in reading order, each line from its own start.
	 *
	 * @param offset where the line's text starts in the block's text
	 */
	static List<Word> words(String text, long startMs, long nextStartMs, int offset) {
		List<Word> words = new ArrayList<>();
		for (Word word : estimate(text, startMs, nextStartMs)) {
			words.add(new Word(offset + word.from(), offset + word.to(), word.startMs(), word.endMs()));
		}
		return words;
	}

	/** @return how far through its words a line is: within the word being sung by its share of the word's time; between words nothing moves */
	private static float along(List<Word> words, long at) {
		float chars = 0f;
		for (Word word : words) {
			if (at >= word.endMs()) {
				chars = word.to();
			} else if (at >= word.startMs()) {
				// clamp to the word: a held word whose end is not after its start would otherwise run ahead and jump back
				float part = Math.clamp((at - word.startMs()) / (float) Math.max(1L, word.endMs() - word.startMs()), 0f, 1f);
				chars = Math.max(chars, word.from() + (word.to() - word.from()) * part);
				break;
			} else {
				break;
			}
		}
		return chars;
	}

	/** @return the estimated words of a line, kept for the next frames */
	private static synchronized List<Word> estimate(String text, long startMs, long nextStartMs) {
		for (Estimate kept : KEPT_ESTIMATES) {
			if (kept != null && kept.startMs == startMs && kept.nextStartMs == nextStartMs && kept.text.equals(text)) {
				return kept.words;
			}
		}
		List<Word> words = List.copyOf(LyricEstimate.words(text, startMs, nextStartMs));
		KEPT_ESTIMATES[nextSlot] = new Estimate(text, startMs, nextStartMs, words);
		nextSlot = (nextSlot + 1) % KEPT;
		return words;
	}
}
