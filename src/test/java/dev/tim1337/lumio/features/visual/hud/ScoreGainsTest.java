package dev.tim1337.lumio.features.visual.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A value keeps its place whatever it gained; gains in a row add up in one small label, which goes again. */
class ScoreGainsTest {
	private final ScoreGains gains = new ScoreGains();

	@Test
	void aValuesPlaceComesFromItsRowAlone() {
		// the methods the row is drawn with take no gain: with or without one, it is the same
		assertEquals(ScoreGains.valueLeft(200f, 30f), ScoreGains.valueLeft(200f, 30f));
		assertEquals(170f, ScoreGains.valueLeft(200f, 30f));
		assertEquals(90f, ScoreGains.rowWidth(50f, 30f, 10f), "no room for an overlay");
	}

	@Test
	void aGainShowsAndGoes() {
		gains.see("Purse", "+12", "689K", 1000L);
		assertEquals("+12", gains.text("Purse", 1000L));
		assertEquals("+12", gains.text("Purse", 2000L));
		assertNull(gains.text("Purse", 1000L + ScoreGains.SHOWS_MILLIS));
	}

	@Test
	void writtenAgainItCountsOnce() {
		for (long now = 1000L; now < 1400L; now += 50L) {
			gains.see("Purse", "+12", "689K", now);
		}
		assertEquals("+12", gains.text("Purse", 1400L));
	}

	@Test
	void gainsInARowAddUp() {
		gains.see("Purse", "+12", "1,012", 1000L);
		gains.see("Purse", "+5", "1,017", 1200L);
		assertEquals("+17", gains.text("Purse", 1200L));
		gains.see("Purse", "+3", "1,020", 1400L);
		assertEquals("+20", gains.text("Purse", 1400L));
		assertEquals("+20", gains.text("Purse", 1400L + ScoreGains.SHOWS_MILLIS - 1L), "each new one shows for the whole time");
	}

	@Test
	void theSameGainAgainAddsWhenTheValueWentUp() {
		gains.see("Purse", "+5", "1,005", 1000L);
		gains.see("Purse", "+5", "1,010", 1200L);
		assertEquals("+10", gains.text("Purse", 1200L));
	}

	@Test
	void theSameGainAfterAGapCounts() {
		gains.see("Purse", "+5", "1,005", 1000L);
		gains.see("Purse", "", "1,005", 1100L);
		gains.see("Purse", "+5", "1,010", 1200L);
		assertEquals("+10", gains.text("Purse", 1200L));
	}

	@Test
	void afterItWentANewGainStartsAnew() {
		gains.see("Purse", "+12", "1,012", 1000L);
		gains.see("Purse", "+5", "1,017", 1000L + ScoreGains.SHOWS_MILLIS + 1L);
		assertEquals("+5", gains.text("Purse", 1000L + ScoreGains.SHOWS_MILLIS + 1L));
	}

	@Test
	void valuesAreApart() {
		gains.see("Purse", "+12", "1,012", 1000L);
		gains.see("Bits", "+250", "12,650", 1000L);
		assertEquals("+12", gains.text("Purse", 1000L));
		assertEquals("+250", gains.text("Bits", 1000L));
		assertNull(gains.text("Motes", 1000L));
	}

	@Test
	void shortAndLongNumbers() {
		assertEquals(1500.0, ScoreGains.amount("+1,500"));
		assertEquals(1500.0, ScoreGains.amount("+1.5K"));
		assertEquals(689000.0, ScoreGains.amount("689K"));
		assertTrue(Double.isNaN(ScoreGains.amount("")));
		assertTrue(Double.isNaN(ScoreGains.amount("many")));
		assertEquals("+1,512", ScoreGains.written(1512.0));
		assertEquals("+1.5", ScoreGains.written(1.5));
	}

	@Test
	void whatCannotBeReadIsShownAsItIs() {
		gains.see("Purse", "+a", "1,012", 1000L);
		assertEquals("+a", gains.text("Purse", 1000L));
	}

	@Test
	void itSlidesInAndFades() {
		assertEquals(1f, ScoreGains.slideAt(0L));
		assertEquals(0f, ScoreGains.slideAt(180L));
		assertEquals(0f, ScoreGains.opacityAt(0L));
		assertEquals(1f, ScoreGains.opacityAt(500L));
		assertEquals(0.5f, ScoreGains.opacityAt(1250L), 0.001f);
		assertEquals(0f, ScoreGains.opacityAt(ScoreGains.SHOWS_MILLIS));
	}

	@Test
	void itGoesBesideTheValueWhereThereIsRoomAndAboveWhereNot() {
		assertEquals(ScoreGains.Place.BESIDE, ScoreGains.place(50f, 150f, 30f, 4f));
		assertEquals(ScoreGains.Place.ABOVE, ScoreGains.place(120f, 150f, 30f, 4f));
	}
}
