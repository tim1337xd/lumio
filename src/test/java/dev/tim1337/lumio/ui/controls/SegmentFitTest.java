package dev.tim1337.lumio.ui.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text of a segmented control fits at every width, and shrinks only when it has to. */
class SegmentFitTest {
	private static final float CLOSE = 0.0001f;

	@Test
	void textThatFitsKeepsItsSize() {
		assertEquals(1f, SegmentFit.scale(200f, 175f), CLOSE);
		assertEquals(1f, SegmentFit.scale(175f, 175f), CLOSE);
	}

	@Test
	void textShrinksOnlyAsFarAsNeeded() {
		assertEquals(0.5f, SegmentFit.scale(100f, 200f), CLOSE);
	}

	@Test
	void scaledTextNeverPassesItsRoom() {
		for (float room = 1f; room < 300f; room += 7f) {
			assertTrue(SegmentFit.scale(room, 233f) * 233f <= room + CLOSE);
		}
	}

	private static final int INSET = 2;
	private static final int PADDING = 8;
	private static final int LEAST = 4;
	/** "Like the menu", "Inter" and "Minecraft", measured in bold. */
	private static final int[] TEXTS = {68, 26, 50};

	@Test
	void aControlIsAsWideAsItsTextPlusPadding() {
		assertEquals(2 * INSET + 68 + 26 + 50 + 6 * PADDING, SegmentFit.width(TEXTS, INSET, PADDING));
	}

	@Test
	void fitsDecidesWhereTheDropdownTakesOver() {
		int least = SegmentFit.width(TEXTS, INSET, LEAST);
		assertTrue(SegmentFit.fits(least, TEXTS, INSET, LEAST));
		assertFalse(SegmentFit.fits(least - 1, TEXTS, INSET, LEAST));
	}

	@Test
	void aRowGivesTheUsualWidthAllItsRoomOrTheDropdownsWidth() {
		int usual = SegmentFit.width(TEXTS, INSET, PADDING);
		int least = SegmentFit.width(TEXTS, INSET, LEAST);
		int dropdown = 108;
		assertEquals(usual, SegmentFit.wanted(400, usual, least, dropdown));
		assertEquals(least + 3, SegmentFit.wanted(least + 3, usual, least, dropdown));
		assertEquals(dropdown, SegmentFit.wanted(least - 1, usual, least, dropdown));
		assertEquals(80, SegmentFit.wanted(80, usual, least, dropdown));
	}

	@Test
	void whatARowGivesEitherFitsOrIsADropdown() {
		int usual = SegmentFit.width(TEXTS, INSET, PADDING);
		int least = SegmentFit.width(TEXTS, INSET, LEAST);
		for (int available = 40; available < 300; available++) {
			int given = SegmentFit.wanted(available, usual, least, 108);
			assertTrue(given <= available);
			assertEquals(available >= least, SegmentFit.fits(given, TEXTS, INSET, LEAST));
		}
	}

	@Test
	void labelsNeverOverlapAtAnyWidthThatFits() {
		int[] edges = new int[TEXTS.length + 1];
		for (int width = SegmentFit.width(TEXTS, INSET, LEAST); width < 500; width++) {
			SegmentFit.edges(10, width, TEXTS, INSET, edges);
			assertEquals(10 + INSET, edges[0]);
			assertEquals(10 + width - INSET, edges[TEXTS.length]);
			for (int i = 0; i < TEXTS.length; i++) {
				// each part holds its text with the padding, so the pill behind it holds the whole label
				assertTrue(edges[i + 1] - edges[i] >= TEXTS[i] + 2 * LEAST, "width " + width + " part " + i);
			}
		}
	}

	@Test
	void extraRoomIsSharedEqually() {
		int[] edges = new int[TEXTS.length + 1];
		SegmentFit.edges(0, SegmentFit.width(TEXTS, INSET, PADDING), TEXTS, INSET, edges);
		for (int i = 0; i < TEXTS.length; i++) {
			assertEquals(TEXTS[i] + 2 * PADDING, edges[i + 1] - edges[i]);
		}
	}

	@Test
	void noRoomAndNoTextStayValid() {
		assertEquals(0f, SegmentFit.scale(-20f, 100f), CLOSE);
		assertEquals(1f, SegmentFit.scale(50f, 0f), CLOSE);
	}
}
