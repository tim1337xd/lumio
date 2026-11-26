package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Very long lines of the lyrics in the world: split in time where they can be cut, never dropped, and never changed when they fit. */
class LyricSplitTest {
	/** A text fits when it has at most this many letters. */
	private static final Predicate<String> EIGHTY = text -> text.length() <= 80;
	private static final String LONG = "we were running through the city in the middle of the night with nothing but the sound of our own "
		+ "footsteps and the echo of a song that nobody remembered the words to anymore but us";

	@Test
	void linesThatFitStayTheSameList() {
		List<LyricLine> lines = List.of(new LyricLine(1_000, "a short line", List.of()), new LyricLine(3_000, "another one", List.of()));
		assertSame(lines, LyricSplit.split(lines, EIGHTY));
	}

	@Test
	void aVeryLongLineBecomesPartsThatFitInOrderAndKeepsEveryWord() {
		assertTrue(LONG.length() > 150);
		List<LyricLine> lines = List.of(new LyricLine(1_000, LONG, List.of()), new LyricLine(9_000, "the next line", List.of()));
		List<LyricLine> out = LyricSplit.split(lines, EIGHTY);
		assertTrue(out.size() >= 3 && out.size() <= 1 + LyricSplit.MOST_PARTS, "parts: " + out.size());
		StringBuilder joined = new StringBuilder();
		long before = 0;
		for (LyricLine part : out.subList(0, out.size() - 1)) {
			assertTrue(EIGHTY.test(part.text()), "a part that does not fit: " + part.text());
			assertTrue(part.timeMs() > before, "parts start one after the other");
			assertTrue(part.timeMs() < 9_000, "and before the next line");
			before = part.timeMs();
			joined.append(joined.isEmpty() ? "" : " ").append(part.text());
		}
		assertEquals(LONG, joined.toString());
		assertEquals(1_000, out.get(0).timeMs());
		assertEquals("the next line", out.get(out.size() - 1).text());
	}

	@Test
	void withoutTimingsThePartsShareTheLinesTimeByTheirLetters() {
		String text = "aaaa bbbb cccc dddd";
		List<LyricLine> out = LyricSplit.split(List.of(new LyricLine(0, text, List.of()), new LyricLine(1_900, "next", List.of())), t -> t.length() <= 10);
		assertEquals("aaaa bbbb", out.get(0).text());
		assertEquals("cccc dddd", out.get(1).text());
		assertEquals(1_000, out.get(1).timeMs());
	}

	@Test
	void withTimingsItIsCutWhereTheSingerPausesAndThePartStartsWithItsFirstWord() {
		String text = "one two three four five six";
		List<Word> words = new ArrayList<>();
		// a long pause after "four", not at the exact middle
		words.add(new Word(0, 3, 0, 300));
		words.add(new Word(4, 7, 300, 600));
		words.add(new Word(8, 13, 600, 900));
		words.add(new Word(14, 18, 950, 1_200));
		words.add(new Word(19, 23, 3_000, 3_300));
		words.add(new Word(24, 27, 3_300, 3_600));
		List<LyricLine> out = LyricSplit.split(List.of(new LyricLine(0, text, words), new LyricLine(5_000, "next", List.of())), t -> t.length() <= 20);
		assertEquals("one two three four", out.get(0).text());
		assertEquals("five six", out.get(1).text());
		assertEquals(3_000, out.get(1).timeMs());
		assertEquals(4, out.get(0).words().size());
		Word five = out.get(1).words().get(0);
		assertEquals(0, five.from());
		assertEquals(4, five.to());
		assertEquals(3_000, five.startMs());
	}

	@Test
	void oneVeryLongWordAndTextWithoutSpacesAreCutInTheMiddle() {
		String word = "y" + "e".repeat(160) + "ah";
		String noSpaces = "あ".repeat(170);
		List<LyricLine> lines = List.of(new LyricLine(0, word, List.of()), new LyricLine(4_000, noSpaces, List.of()), new LyricLine(8_000, "end", List.of()));
		List<LyricLine> out = LyricSplit.split(lines, EIGHTY);
		StringBuilder first = new StringBuilder();
		StringBuilder second = new StringBuilder();
		for (LyricLine part : out.subList(0, out.size() - 1)) {
			assertTrue(EIGHTY.test(part.text()), "a part that does not fit");
			(part.timeMs() < 4_000 ? first : second).append(part.text());
		}
		assertEquals(word, first.toString());
		assertEquals(noSpaces, second.toString());
		// a short word that fits is left alone
		List<LyricLine> shortOnes = List.of(new LyricLine(0, "yeah", List.of()));
		assertSame(shortOnes, LyricSplit.split(shortOnes, EIGHTY));
	}

	@Test
	void theLastLineOfASongIsSplitToo() {
		List<LyricLine> out = LyricSplit.split(List.of(new LyricLine(2_000, LONG, List.of())), EIGHTY);
		assertTrue(out.size() >= 2);
		for (int i = 1; i < out.size(); i++) {
			assertTrue(out.get(i).timeMs() > out.get(i - 1).timeMs());
		}
	}

	@Test
	void aBlockOfFastLinesIsLeftAlone() {
		List<LyricLine> lines = List.of(new LyricLine(0, LONG + LyricGroups.BREAK + LONG, List.of()));
		assertSame(lines, LyricSplit.split(lines, EIGHTY));
	}
}
