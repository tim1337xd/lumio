package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ui.controls.SegmentFit;

import org.junit.jupiter.api.Test;

/**
 * The controls of a settings page line up (rule of 2026-11-24): one of the two standard widths for all of them, a choice's options in even cells,
 * each text whole in its cell.
 */
class ControlWidthsTest {
	@Test
	void aPageUsesOneOfTheTwoStandardWidths() {
		for (int need = 0; need <= 400; need += 7) {
			int width = ControlWidths.forPage(need);
			assertTrue(width == ControlWidths.STANDARD || width == ControlWidths.WIDE, need + " gives " + width);
		}
		assertEquals(ControlWidths.STANDARD, ControlWidths.forPage(ControlWidths.STANDARD), "what fits takes the standard width");
		assertEquals(ControlWidths.WIDE, ControlWidths.forPage(ControlWidths.STANDARD + 1), "only what does not fit takes the wider one");
	}

	@Test
	void optionsShareTheWidthEvenlyAndFitWhole() {
		int[] three = {30, 37, 31};
		int[] four = {30, 33, 31, 24};
		int[] edges = new int[5];
		for (int[] texts : new int[][] {three, four}) {
			SegmentFit.evenEdges(100, ControlWidths.STANDARD, texts.length, 2, edges);
			assertEquals(102, edges[0]);
			assertEquals(100 + ControlWidths.STANDARD - 2, edges[texts.length], "the same box, whatever the number of options");
			for (int i = 0; i < texts.length; i++) {
				int cell = edges[i + 1] - edges[i];
				assertTrue(Math.abs(cell - (ControlWidths.STANDARD - 4) / texts.length) <= 1, "even cells: " + cell);
			}
			// a set the standard width shows whole: every text fits its cell with its padding
			int need = SegmentFit.evenWidth(texts, 2, 4);
			assertTrue(need <= ControlWidths.STANDARD);
			for (int i = 0; i < texts.length; i++) {
				assertTrue(edges[i + 1] - edges[i] >= texts[i] + 8, "the text is never cut");
			}
		}
		// a set too wide for its cells asks for more than the standard width: the page takes the wider one
		assertTrue(ControlWidths.forPage(SegmentFit.evenWidth(new int[] {60, 60, 60}, 2, 4)) == ControlWidths.WIDE);
	}
}
