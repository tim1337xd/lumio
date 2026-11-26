package dev.tim1337.lumio.features.music;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Long lyric lines break into balanced rows near the middle, never leaving one word alone, and a line that fits stays whole. */
class LyricWrapTest {
	/** Every character is one unit wide. */
	private static double width(String text) {
		return text.length();
	}

	@Test
	void aLineThatFitsStaysWhole() {
		assertEquals(List.of("we keep on driving"), LyricWrap.balanced("we keep on driving", 40, 3, LyricWrapTest::width));
	}

	@Test
	void twoBalancedRows() {
		String line = "slowly drifting over silver water underneath the sleeping city lights";
		List<String> rows = LyricWrap.balanced(line, 50, 3, LyricWrapTest::width);
		assertEquals(2, rows.size());
		assertEquals(line, String.join(" ", rows));
		// near the middle: the two rows differ by at most a word
		assertTrue(Math.abs(rows.get(0).length() - rows.get(1).length()) <= 10, rows.toString());
	}

	@Test
	void neverOneWordAlone() {
		// greedy at 30 would leave "tonight" alone in the second row
		List<String> rows = LyricWrap.balanced("I got the flow and I go tonight", 30, 3, LyricWrapTest::width);
		assertEquals(2, rows.size());
		assertTrue(rows.get(1).contains(" "), rows.toString());
		for (String row : rows) {
			assertTrue(row.length() <= 30, rows.toString());
		}
	}

	@Test
	void threeRowsWhenTwoAreNotEnough() {
		String line = "one two three four five six seven eight nine ten eleven twelve thirteen";
		List<String> rows = LyricWrap.balanced(line, 26, 3, LyricWrapTest::width);
		assertEquals(3, rows.size());
		assertEquals(line, String.join(" ", rows));
	}

	@Test
	void oneLongWordIsBrokenIntoEvenPieces() {
		assertEquals(List.of("supercalif", "ragilistic"), LyricWrap.balanced("supercalifragilistic", 10, 3, LyricWrapTest::width));
		// as few pieces as fit, all about as long
		String word = "y" + "e".repeat(70) + "ah";
		List<String> rows = LyricWrap.balanced(word, 30, 4, LyricWrapTest::width);
		assertEquals(3, rows.size());
		assertEquals(word, String.join("", rows));
		for (String row : rows) {
			assertTrue(row.length() >= 24 && row.length() <= 30, rows.toString());
		}
	}

	@Test
	void aLongWordAmongOthersIsBrokenWithoutASpace() {
		String text = "oh yeeeeeeeeeeeeeeeeeeeeeeeah right";
		List<String> rows = LyricWrap.balanced(text, 12, 4, LyricWrapTest::width);
		// the rows are the text in order; only a space between two words is left out at a row's end, nothing inside the word
		int at = 0;
		for (String row : rows) {
			assertTrue(row.length() <= 12, rows.toString());
			assertEquals(row, text.substring(at, at + row.length()), rows.toString());
			at += row.length();
			at += at < text.length() && text.charAt(at) == ' ' ? 1 : 0;
		}
		assertEquals(text.length(), at);
	}

	@Test
	void aLetterOfTwoCharsIsNeverCut() {
		String word = "🎵".repeat(6);
		List<String> rows = LyricWrap.balanced(word, 5, 4, LyricWrapTest::width);
		assertTrue(rows.size() > 1, rows.toString());
		for (String row : rows) {
			assertEquals(0, row.length() % 2, rows.toString());
			assertTrue(Character.isHighSurrogate(row.charAt(0)), rows.toString());
		}
	}
}
