package dev.tim1337.lumio.features.music;

/**
 * Where the highlight box of a lyric line rests, and whether it shows. The box always frames whole words: the word that is sung, from its left
 * edge to its right, never starting or ending inside a word (progress through a long word is a fill inside the box, not a shorter box). When the
 * line is over (its last word ended, the song is paused, or nothing more is timed) the box stays on the last word a moment, then fades, and the
 * line rests in its normal color. A line without word timings that is lit as a whole has no box. Plain logic, tested without the game.
 */
final class LyricBoxRest {
	/** How long the box stays on the last word once the line is over or the song paused, before it fades. */
	static final long HOLD_MILLIS = 500L;

	private LyricBoxRest() {
	}

	/**
	 * @param sung how many characters of the line are sung, fractional
	 * @return the word the box frames, as the indices of its first character and of the one after its last; an empty range for a text without words
	 */
	static int[] word(CharSequence text, float sung) {
		int length = text.length();
		int at = Math.max(0, Math.min(length - 1, (int) sung));
		// between two words, and past the last one, the box stays on the word sung last
		while (at > 0 && Character.isWhitespace(text.charAt(at))) {
			at--;
		}
		if (length == 0 || Character.isWhitespace(text.charAt(at))) {
			return new int[] {0, 0};
		}
		return new int[] {HighlightBox.wordStart(text, at), HighlightBox.wordEnd(text, at)};
	}

	/**
	 * @param sinceLineOver how long the line has been over, in milliseconds; negative while it is sung
	 * @param paused        how long the song has been paused, in milliseconds; negative while it plays
	 * @return whether the box shows: not once the line was over or the song paused for longer than the hold
	 */
	static boolean shows(long sinceLineOver, long paused) {
		return sinceLineOver < HOLD_MILLIS && paused < HOLD_MILLIS;
	}

	/**
	 * @return how far a long word is filled, from 0 to 1: the share of the word's characters that are sung; 1 for a word that is over
	 */
	static float fill(int wordStart, int wordEnd, float sung) {
		return wordEnd <= wordStart ? 1f : Math.max(0f, Math.min(1f, (sung - wordStart) / (wordEnd - wordStart)));
	}
}
