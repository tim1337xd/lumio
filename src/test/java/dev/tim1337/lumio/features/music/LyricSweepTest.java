package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Lines without word timings get estimated ones, so their highlight moves through the line as with real timings: from exactly 0 at the line's
 * own start, forward only, by the words' syllables with a pause at commas, done before a long pause, the same in the world, the HUD, the info bar
 * and the Cut-out preset.
 */
class LyricSweepTest {
	private static final String TEXT = "I am extraordinarily happy";

	@Test
	void theHighlightStartsAtZeroAndOnlyMovesForward() {
		assertEquals(0f, LyricSweep.chars(TEXT, 5_000, 7_000, 5_000), "nothing is lit at the line's start");
		assertEquals(0f, LyricSweep.chars(TEXT, 5_000, 7_000, 4_400), "nor before it: the line before's progress is never the line's");
		float before = 0f;
		for (long at = 5_000; at <= 7_200; at += 10) {
			float chars = LyricSweep.chars(TEXT, 5_000, 7_000, at);
			assertTrue(chars >= before, "forward only, at " + at);
			before = chars;
		}
		assertEquals(TEXT.length(), before);
		// a moment after the start only a little of the first word is lit, not half the line
		assertTrue(LyricSweep.chars(TEXT, 5_000, 7_000, 5_060) < 2f);
	}

	@Test
	void wordsTakeTimeByTheirSyllables() {
		List<Word> words = LyricSweep.words(TEXT, 0, 3_000, 0);
		assertEquals(4, words.size());
		long shortWord = words.get(1).endMs() - words.get(1).startMs();
		long longWord = words.get(2).endMs() - words.get(2).startMs();
		assertTrue(longWord > 3 * shortWord, "the long word takes much longer, not the same time per word");
		assertEquals(0, words.get(0).startMs());
		// the words and the highlight agree
		assertEquals(words.get(2).from(), LyricSweep.chars(TEXT, 0, 3_000, words.get(2).startMs()), 1e-3);
	}

	@Test
	void aCommaIsAPause() {
		List<Word> words = LyricSweep.words("run, run away", 0, 1_000, 0);
		assertTrue(words.get(1).startMs() > words.get(0).endMs(), "a pause after the comma");
		assertEquals(words.get(1).endMs(), words.get(2).startMs(), 1, "no pause between the words after it");
		float inPause = LyricSweep.chars("run, run away", 0, 1_000, (words.get(0).endMs() + words.get(1).startMs()) / 2);
		assertEquals(words.get(0).to(), inPause, 1e-3, "nothing moves in the pause");
	}

	@Test
	void theHighlightNeverCrawlsThroughALongPause() {
		// a short line, then nothing for 20 s: done at a singing pace, not after 20 s
		long sung = LyricSweep.duration("Hey you", 1_000, 21_000);
		assertTrue(sung < 2_000, "done at a singing pace: " + sung);
		assertEquals("Hey you".length(), LyricSweep.chars("Hey you", 1_000, 21_000, 1_000 + sung));
		// with the next line close, it ends there at the latest
		assertTrue(LyricSweep.duration("Hey you", 1_000, 1_300) <= 300);
	}

	@Test
	void aWrappedLineHasOneProgress() {
		// the rows of a line share its characters in reading order: the progress through the text is one number, whatever the rows
		String line = "a line long enough to be broken into two rows in the world";
		float half = LyricSweep.chars(line, 0, 6_000, LyricSweep.duration(line, 0, 6_000) / 2);
		assertTrue(half > line.length() * 0.3f && half < line.length() * 0.7f, "half way: " + half);
	}

	@Test
	void eachLineUsesWhatItHas() {
		// word timings are kept exactly; a line beside it without them follows its estimated ones
		LyricLine timed = new LyricLine(1_000, "go now", List.of(new Word(0, 2, 1_000, 1_400), new Word(3, 6, 1_600, 2_000)));
		assertEquals(1f, LyricSweep.sung(timed, 9_000L, 1_200));
		assertEquals(2f, LyricSweep.sung(timed, 9_000L, 1_500), "between its words nothing moves");
		assertEquals(6f, LyricSweep.sung(timed, 9_000L, 2_000));
		LyricLine plain = new LyricLine(3_000, "no timings here");
		assertEquals(0f, LyricSweep.sung(plain, 9_000L, 3_000));
		assertTrue(LyricSweep.sung(plain, 9_000L, 3_200) > 0f);
	}

	/**
	 * A line-synced line moves through its time in every place: the world's Classic preset and the info bar both take
	 * {@link LyricSweep#sung} (through MusicFeature.sungChars), the HUD's lines too; the Cut-out preset shows its words one by one by the same
	 * estimate, a tile only on a strong key word.
	 */
	@Test
	void aLineSyncedLineAdvancesOverItsTimeEverywhere() {
		LyricLine line = new LyricLine(10_000, "we walk along the shining river, singing");
		long next = 14_000L;
		long end = 10_000 + LyricSweep.duration(line.text(), 10_000, next);
		float quarter = LyricSweep.sung(line, next, 10_000 + (end - 10_000) / 4);
		float half = LyricSweep.sung(line, next, 10_000 + (end - 10_000) / 2);
		float most = LyricSweep.sung(line, next, 10_000 + (end - 10_000) * 3 / 4);
		assertTrue(0f < quarter && quarter < half && half < most && most < line.text().length(), quarter + " " + half + " " + most);
		assertEquals(line.text().length(), LyricSweep.sung(line, next, end));
		// Cut-out: the pieces come one after the other over the same time, the same estimate
		List<CutoutWords.Piece> pieces = CutoutWords.pieces(line, line.words(), next, true, false);
		long last = Long.MIN_VALUE;
		for (CutoutWords.Piece piece : pieces) {
			assertTrue(piece.startMs() >= last, "one by one");
			last = piece.startMs();
		}
		assertTrue(pieces.size() > 3 && pieces.getFirst().startMs() == 10_000 && last > 10_000 + (end - 10_000) / 2, "spread over the line");
		assertTrue(pieces.stream().filter(CutoutWords.Piece::tile).allMatch(CutoutWords.Piece::key), "a tile only on the key word");
		List<Word> estimated = LyricSweep.words(line.text(), 10_000, next, 0);
		assertEquals(estimated.getLast().startMs(), pieces.stream().mapToLong(CutoutWords.Piece::startMs).max().orElseThrow(), "the same estimate");
	}
}
