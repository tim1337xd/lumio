package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The highlight box rests on whole words, and goes when the line is over or the song paused. */
class LyricBoxRestTest {
	private static final String LINE = "Ich rolle auf Chrom";

	@Test
	void theBoxAlwaysFramesAWholeWord() {
		// wherever the singing is, also inside a word, between two, at the very end and past it: never a part of a word
		for (float sung = 0f; sung <= LINE.length() + 3; sung += 0.25f) {
			int[] word = LyricBoxRest.word(LINE, sung);
			assertTrue(word[0] < word[1], "a word at " + sung);
			assertTrue(word[0] == 0 || LINE.charAt(word[0] - 1) == ' ', "starts at a word's first letter at " + sung);
			assertTrue(word[1] == LINE.length() || LINE.charAt(word[1]) == ' ', "ends after a word's last letter at " + sung);
		}
		assertArrayEquals(new int[] {0, 3}, LyricBoxRest.word(LINE, 0f));
		assertArrayEquals(new int[] {4, 9}, LyricBoxRest.word(LINE, 6.5f));
		assertArrayEquals(new int[] {4, 9}, LyricBoxRest.word(LINE, 9f), "between two words it stays on the one sung last");
		assertArrayEquals(new int[] {14, 19}, LyricBoxRest.word(LINE, 15.2f), "inside the last word: the whole of it");
		assertArrayEquals(new int[] {14, 19}, LyricBoxRest.word(LINE, 19f), "the line is sung: the whole last word");
		assertArrayEquals(new int[] {14, 19}, LyricBoxRest.word(LINE, 400f));
		assertArrayEquals(new int[] {0, 0}, LyricBoxRest.word("", 3f));
		assertArrayEquals(new int[] {0, 0}, LyricBoxRest.word("   ", 1f));
	}

	@Test
	void aLongWordFillsInsideItsBox() {
		assertEquals(0f, LyricBoxRest.fill(14, 19, 14f));
		assertEquals(0.4f, LyricBoxRest.fill(14, 19, 16f), 1e-6);
		assertEquals(1f, LyricBoxRest.fill(14, 19, 19f));
		assertEquals(1f, LyricBoxRest.fill(14, 19, 60f));
		assertEquals(0f, LyricBoxRest.fill(14, 19, 2f));
	}

	@Test
	void theBoxGoesWhenTheLineIsOverOrTheSongPaused() {
		assertTrue(LyricBoxRest.shows(-2_000, -1), "while the line is sung");
		assertTrue(LyricBoxRest.shows(0, -1), "the last word just ended: it stays a moment");
		assertTrue(LyricBoxRest.shows(LyricBoxRest.HOLD_MILLIS - 1, -1));
		assertFalse(LyricBoxRest.shows(LyricBoxRest.HOLD_MILLIS, -1), "then it fades: the end of the song, an outro, a long gap");
		assertTrue(LyricBoxRest.shows(-2_000, 100), "just paused");
		assertFalse(LyricBoxRest.shows(-2_000, LyricBoxRest.HOLD_MILLIS), "paused: the word is finished, then the box goes");
	}
}
