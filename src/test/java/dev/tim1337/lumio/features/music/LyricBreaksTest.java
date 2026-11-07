package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.List;

class LyricBreaksTest {
	/** A line sung from start to end, its words evenly in between. */
	private static LyricLine sung(long start, long end, String text) {
		return new LyricLine(start, text, List.of(new Word(0, text.length(), start, end)));
	}

	@Test
	void aLongIntroAndALongGapBecomeBreaks() {
		List<LyricLine> lines = List.of(sung(9_000, 11_000, "first"), sung(11_500, 13_000, "second"), sung(20_000, 22_000, "after the solo"));
		List<LyricLine> shown = LyricBreaks.withBreaks(lines);
		assertEquals(5, shown.size());
		assertTrue(LyricBreaks.is(shown.get(0)), "the intro");
		assertEquals(0L, shown.get(0).timeMs());
		assertTrue(LyricBreaks.is(shown.get(3)), "the solo");
		assertEquals(13_000L, shown.get(3).timeMs(), "it starts where the line before ends");
		assertEquals(20_000L, LyricBreaks.nextStart(shown, shown.get(3)));
	}

	@Test
	void shortGapsAndAShortIntroStayAsTheyAre() {
		List<LyricLine> lines = List.of(sung(2_000, 4_000, "first"), sung(6_500, 8_000, "second"));
		assertSame(lines, LyricBreaks.withBreaks(lines));
	}

	@Test
	void linesWithoutWordTimesAreGivenAnEnd() {
		// three seconds for a short line, more for a long one to be read
		assertEquals(13_000L, LyricBreaks.end(new LyricLine(10_000, "so short")));
		assertTrue(LyricBreaks.end(new LyricLine(10_000, "a long line with very many words that takes a while to read through")) > 14_000L);
		List<LyricLine> lines = List.of(new LyricLine(1_000, "short"), new LyricLine(6_500, "next"));
		assertSame(lines, LyricBreaks.withBreaks(lines), "the line counts as sung for three seconds: 2.5 seconds is no break");
		List<LyricLine> longer = List.of(new LyricLine(1_000, "short"), new LyricLine(9_000, "next"));
		assertTrue(LyricBreaks.is(LyricBreaks.withBreaks(longer).get(1)));
	}

	@Test
	void theDotsFillOneAfterAnotherAndTheLastBeforeTheNextLine() {
		long start = 10_000;
		long end = 20_000;
		assertEquals(0f, LyricBreaks.progress(start, end, start), 1e-6f);
		assertEquals(1f, LyricBreaks.progress(start, end, end - LyricBreaks.LEAVE_MS), 1e-6f, "full just before the next line");
		assertEquals(1f, LyricBreaks.dot(0, 0.5f), 1e-6f);
		assertEquals(0.5f, LyricBreaks.dot(1, 0.5f), 1e-6f);
		assertEquals(0f, LyricBreaks.dot(2, 0.5f), 1e-6f);
		assertEquals(1f, LyricBreaks.leaving(end, end - 1_000), 1e-6f);
		assertEquals(0f, LyricBreaks.leaving(end, end), 1e-6f);
	}

	@Test
	void aBreakIsNeverARepeatedLine() {
		List<LyricLine> shown = LyricBreaks.withBreaks(List.of(sung(9_000, 10_000, "la la la"), sung(20_000, 21_000, "la la la"), sung(30_000, 31_000, "la la la")));
		boolean[] chorus = LyricChorus.of(shown);
		for (int i = 0; i < shown.size(); i++) {
			if (LyricBreaks.is(shown.get(i))) {
				assertFalse(chorus[i], "break " + i);
			}
		}
		assertFalse(LyricChorus.sameWords(LyricBreaks.MARK, LyricBreaks.MARK));
	}
}
