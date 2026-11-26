package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Estimated word timings for a line that only has its start: in order, inside the line's time, longer words longer, a pause at a comma. */
class LyricEstimateTest {
	private static final String[] LINES = {"I believe in the children, listen", "and this one only knows when it starts", "go", "Wir fahren durch die Nacht und mein Herz brennt",
		"unbelievable, extraordinary nights with you", "oh  oh   oh"};

	@Test
	void theTimesRunInOrderAndStayInsideTheLine() {
		for (String text : LINES) {
			for (long length : new long[] {300, 1500, 4000, 12_000, 60_000}) {
				long start = 10_000;
				List<Word> words = LyricEstimate.words(text, start, start + length);
				assertEquals(text.trim().split("\\s+").length, words.size(), text);
				long before = start;
				for (Word word : words) {
					assertTrue(word.startMs() >= before, text + ": a word never starts before the one before it");
					assertTrue(word.endMs() > word.startMs(), text);
					assertTrue(word.endMs() <= start + length, text + " in " + length + " ms: a word runs past the line's time");
					assertEquals(text.substring(word.from(), word.to()).trim(), text.substring(word.from(), word.to()), "a whole word");
					before = word.startMs();
				}
				assertEquals(start, words.getFirst().startMs(), "the first word starts with the line");
			}
		}
	}

	@Test
	void aLongerWordHasMoreTime() {
		List<Word> words = LyricEstimate.words("go unbelievable now", 0, 4000);
		long go = words.get(0).endMs() - words.get(0).startMs();
		long long5 = words.get(1).endMs() - words.get(1).startMs();
		long now = words.get(2).endMs() - words.get(2).startMs();
		assertTrue(long5 > 3 * go, "five syllables against one: " + long5 + " and " + go);
		assertEquals(go, now, 2, "words of one syllable take the same");
		assertEquals(5, LyricEstimate.syllables("unbelievable"));
		assertEquals(1, LyricEstimate.syllables("night"));
		assertEquals(2, LyricEstimate.syllables("Übergang".substring(0, 5)));
		assertEquals(1, LyricEstimate.syllables("hmm"), "at least one");
	}

	@Test
	void aCommaIsAShortPause() {
		List<Word> with = LyricEstimate.words("children, listen", 0, 0);
		List<Word> without = LyricEstimate.words("children listen", 0, 0);
		assertEquals(LyricEstimate.PAUSE_MS, with.get(1).startMs() - without.get(1).startMs(), 2);
		assertEquals(with.get(0).endMs(), without.get(0).endMs(), "the pause is after the word, not part of it");
	}

	@Test
	void aShortLineBeforeALongPauseIsNotStretchedOverIt() {
		List<Word> words = LyricEstimate.words("hold on now", 0, 30_000);
		long natural = 3 * LyricEstimate.SYLLABLE_MS;
		assertTrue(words.getLast().endMs() <= (long) (natural * LyricEstimate.SLOWEST) + 2, "done early: " + words.getLast().endMs());
		List<Word> endless = LyricEstimate.words("la ".repeat(200).trim(), 0, 600_000);
		assertTrue(endless.getLast().endMs() <= LyricEstimate.MOST_MS, "and never longer than the most");
		// with no next line known: the natural pace
		assertEquals(natural, LyricEstimate.words("hold on now", 0, 0).getLast().endMs(), 2);
	}
}
