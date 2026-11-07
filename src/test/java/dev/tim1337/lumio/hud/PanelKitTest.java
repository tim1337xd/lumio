package dev.tim1337.lumio.hud;

import dev.tim1337.lumio.api.experimental.draw.PanelKit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The panel family's numbers: grouped, short, with fixed decimals, and as a clock. */
class PanelKitTest {
	@Test
	void groupedNumbersHaveThousandsSeparators() {
		assertEquals("0", PanelKit.grouped(0));
		assertEquals("999", PanelKit.grouped(999));
		assertEquals("1,000", PanelKit.grouped(1000));
		assertEquals("4,821,330", PanelKit.grouped(4_821_330));
		assertEquals("-12,400", PanelKit.grouped(-12_400));
	}

	@Test
	void shortNumbersAsTheMockupsWriteThem() {
		assertEquals("214", PanelKit.compact(214));
		assertEquals("1.8k", PanelKit.compact(1840));
		assertEquals("34.2k", PanelKit.compact(34_200));
		assertEquals("5.33M", PanelKit.compact(5_330_000));
		assertEquals("860k", PanelKit.compact(860_000));
		assertEquals("1.20B", PanelKit.compact(1_200_000_000));
		assertEquals("-2.40M", PanelKit.compact(-2_400_000));
		// a value that rounds up to the next unit moves to it, instead of 1000.0k
		assertEquals("1.00M", PanelKit.compact(999_960));
	}

	@Test
	void decimalsArePaddedAndRounded() {
		assertEquals("18.4", PanelKit.decimals(18.44, 1));
		assertEquals("5.30", PanelKit.decimals(5.3, 2));
		assertEquals("5.07", PanelKit.decimals(5.07, 2));
		assertEquals("0.0", PanelKit.decimals(0.01, 1));
	}

	@Test
	void clocksShowMinutesAndHours() {
		assertEquals("0:00", PanelKit.clock(0));
		assertEquals("0:38", PanelKit.clock(38));
		assertEquals("2:41", PanelKit.clock(161));
		assertEquals("54:12", PanelKit.clock(54 * 60 + 12));
		assertEquals("1:02:03", PanelKit.clock(3723));
		assertEquals("0:00", PanelKit.clock(-5));
	}

	@Test
	void simpleRowsStackWithTheirGaps() {
		assertEquals(0, PanelKit.simpleHeight(0));
		assertEquals(PanelKit.SIMPLE_ROW, PanelKit.simpleHeight(1));
		assertEquals(3 * PanelKit.SIMPLE_ROW + 2 * PanelKit.SIMPLE_GAP, PanelKit.simpleHeight(3));
	}
}
