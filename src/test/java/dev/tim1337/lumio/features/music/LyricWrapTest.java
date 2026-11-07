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
	void oneLongWordStaysAsItIs() {
		assertEquals(List.of("supercalifragilistic"), LyricWrap.balanced("supercalifragilistic", 10, 3, LyricWrapTest::width));
	}
}
