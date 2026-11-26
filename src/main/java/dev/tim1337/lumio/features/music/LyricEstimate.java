package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.Word;

import java.util.ArrayList;
import java.util.List;

/**
 * Word timings for a line that has none (line-synced lyrics), the same in the world, the HUD and the info bar: the words are spread over the
 * line's time, each by how many syllables it has, with a short pause after a comma or other punctuation inside the line. The line's time is
 * until the next line starts, but never much longer than people take to sing it: after a short line with a long pause behind it the words are
 * done early instead of crawling. With these the line is shown exactly as one with real word timings is. Plain logic, tested without the game.
 */
final class LyricEstimate {
	/** A natural singing pace: the time of a syllable, and of the pause after punctuation inside a line. */
	static final long SYLLABLE_MS = 230L;
	static final long PAUSE_MS = 170L;
	/** The words take at most this many times their natural pace, and never longer than this in all. */
	static final float SLOWEST = 1.8f;
	static final long MOST_MS = 9_000L;

	private LyricEstimate() {
	}

	/**
	 * @param text        the line
	 * @param startMs     when it starts
	 * @param nextStartMs when the next line starts; not after the start when that is not known
	 * @return its words with estimated times: in order, none before the line's start or after the next line's, a longer word with more time
	 */
	static List<Word> words(String text, long startMs, long nextStartMs) {
		List<int[]> spans = new ArrayList<>();
		int from = -1;
		for (int i = 0; i <= text.length(); i++) {
			boolean letter = i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != LyricGroups.BREAK;
			if (letter && from < 0) {
				from = i;
			} else if (!letter && from >= 0) {
				spans.add(new int[] {from, i});
				from = -1;
			}
		}
		List<Word> words = new ArrayList<>(spans.size());
		if (spans.isEmpty()) {
			return words;
		}
		long[] sung = new long[spans.size()];
		long[] pause = new long[spans.size()];
		long natural = 0L;
		for (int i = 0; i < sung.length; i++) {
			String word = text.substring(spans.get(i)[0], spans.get(i)[1]);
			sung[i] = syllables(word) * SYLLABLE_MS;
			pause[i] = i + 1 < sung.length && pauses(word) ? PAUSE_MS : 0L;
			natural += sung[i] + pause[i];
		}
		long most = Math.min(MOST_MS, (long) (natural * SLOWEST));
		long length = nextStartMs > startMs ? Math.min(nextStartMs - startMs, most) : natural;
		double pace = length / (double) natural;
		long passed = 0L;
		for (int i = 0; i < sung.length; i++) {
			long wordStart = startMs + Math.round(passed * pace);
			long wordEnd = startMs + Math.round((passed + sung[i]) * pace);
			words.add(new Word(spans.get(i)[0], spans.get(i)[1], wordStart, Math.max(wordStart + 1L, wordEnd)));
			passed += sung[i] + pause[i];
		}
		return words;
	}

	/** @return how many syllables a word has, as far as its letters tell: its runs of vowels, at least one */
	static int syllables(String word) {
		int count = 0;
		boolean inVowels = false;
		for (int i = 0; i < word.length(); i++) {
			boolean vowel = "aeiouyäöüáàâéèêíìîóòôúùûAEIOUYÄÖÜ".indexOf(word.charAt(i)) >= 0;
			count += vowel && !inVowels ? 1 : 0;
			inVowels = vowel;
		}
		return Math.max(1, count);
	}

	/** @return whether a word ends with punctuation the singer pauses at */
	private static boolean pauses(String word) {
		return !word.isEmpty() && ",;:.!?–—".indexOf(word.charAt(word.length() - 1)) >= 0;
	}
}
