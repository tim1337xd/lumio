package dev.tim1337.lumio.ui.tooltip;

import dev.tim1337.lumio.api.TooltipLine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The limits of a scrolled tooltip and the sizes of the block of added lines. */
class TooltipBlockTest {
	private static final AddedBlock.Measure FIVE_PER_LETTER = (text, small) -> text.length() * (small ? 4 : 5);

	@Test
	void aTooltipThatFitsDoesNotScroll() {
		TooltipScroll scroll = new TooltipScroll();
		scroll.show("item", 100, 100);
		assertFalse(scroll.canScroll());
		scroll.wheel(-3, false);
		assertEquals(0, scroll.offset());
		assertFalse(scroll.moreBelow());
	}

	@Test
	void theWheelStaysWithinTheLimits() {
		TooltipScroll scroll = new TooltipScroll();
		scroll.show("item", 200, 100);
		assertTrue(scroll.canScroll());
		assertTrue(scroll.moreBelow());
		scroll.wheel(1, false);
		assertEquals(0, scroll.offset());
		scroll.wheel(-1, false);
		assertEquals(TooltipScroll.STEP, scroll.offset());
		scroll.wheel(-1000, false);
		assertEquals(100, scroll.offset());
		assertFalse(scroll.moreBelow());
		assertEquals(1f, scroll.share());
	}

	@Test
	void shiftScrollsFaster() {
		TooltipScroll scroll = new TooltipScroll();
		scroll.show("item", 1000, 100);
		scroll.wheel(-1, true);
		assertEquals(TooltipScroll.STEP * TooltipScroll.SHIFT_FACTOR, scroll.offset());
	}

	@Test
	void aNewItemStartsAtTheTop() {
		TooltipScroll scroll = new TooltipScroll();
		scroll.show("one", 300, 100);
		scroll.wheel(-5, false);
		scroll.show("one", 300, 100);
		assertEquals(5 * TooltipScroll.STEP, scroll.offset());
		scroll.show("two", 300, 100);
		assertEquals(0, scroll.offset());
	}

	@Test
	void aShorterTooltipPullsTheScrollBackIntoItsLimits() {
		TooltipScroll scroll = new TooltipScroll();
		scroll.show("item", 300, 100);
		scroll.wheel(-100, false);
		assertEquals(200, scroll.offset());
		scroll.show("item", 150, 100);
		assertEquals(50, scroll.offset());
	}

	@Test
	void theBlockIsAsTallAsItsLines() {
		List<TooltipLine> lines = List.of(TooltipLine.heading("MARKET", "", TooltipLine.PLAIN), TooltipLine.row("Median", "1M", ""), TooltipLine.footnote("Coflnet"));
		int rows = AddedBlock.HEADING_HEIGHT + AddedBlock.ROW_HEIGHT + AddedBlock.FOOTNOTE_HEIGHT;
		int line = 2 * AddedBlock.LINE_GAP + 1;
		assertEquals(line + rows, AddedBlock.height(lines));
	}

	@Test
	void rowsLineUpInColumns() {
		List<TooltipLine> lines = List.of(TooltipLine.row("Lowest BIN", "4.20M", ""), TooltipLine.row("Sold", "38", "per day"));
		int[] columns = AddedBlock.columns(lines, FIVE_PER_LETTER);
		assertEquals(50, columns[0]);
		assertEquals(30, columns[1], "at least the room of a usual price");
		assertEquals(28, columns[2]);
		assertEquals(50 + AddedBlock.NAME_GAP + 30 + AddedBlock.NOTE_GAP + 28, AddedBlock.width(lines, FIVE_PER_LETTER));
	}

	@Test
	void aLongLabelOrFootnoteWidensTheBlock() {
		List<TooltipLine> lines = List.of(TooltipLine.row("A", "1", ""));
		List<TooltipLine> withNote = List.of(TooltipLine.footnote("Coflnet · updated 2 min ago"));
		assertEquals(27 * 4, AddedBlock.width(withNote, FIVE_PER_LETTER));
	}

	@Test
	void missingValuesAreADash() {
		assertEquals("—", TooltipLine.MISSING);
	}
}
