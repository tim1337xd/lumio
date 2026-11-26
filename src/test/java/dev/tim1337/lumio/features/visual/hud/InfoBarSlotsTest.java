package dev.tim1337.lumio.features.visual.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The info bar's value slots: sized for the usual largest value, and narrowing only after a shorter value held. */
class InfoBarSlotsTest {
	@Test
	void slotsKeepTheRoomOfTheUsualLargestValue() {
		// 99 and 100 FPS keep the same slot; above 999 it is four digits
		assertEquals("000", InfoBarSlots.template(InfoBarSegment.Kind.FPS, "99"));
		assertEquals("000", InfoBarSlots.template(InfoBarSegment.Kind.FPS, "100"));
		assertEquals("0000", InfoBarSlots.template(InfoBarSegment.Kind.FPS, "1240"));
		assertEquals("000", InfoBarSlots.template(InfoBarSegment.Kind.PING, "8"));
		assertEquals("00.0", InfoBarSlots.template(InfoBarSegment.Kind.TPS, "9.8"));
		assertEquals("00.0", InfoBarSlots.template(InfoBarSegment.Kind.TPS, "20.0"));
		assertEquals("00:00", InfoBarSlots.template(InfoBarSegment.Kind.CLOCK, "9:59"));
		assertEquals("00:00 PM", InfoBarSlots.template(InfoBarSegment.Kind.CLOCK, "9:05 PM"));
		assertEquals("-000, -000, -000", InfoBarSlots.template(InfoBarSegment.Kind.COORDS, "12, 64, -250"));
		assertEquals("-0000, -000", InfoBarSlots.template(InfoBarSegment.Kind.COORDS, "-1234, 5"));
	}

	@Test
	void growsAtOnceAndShrinksOnlyAfterTheShorterValueHeld() {
		InfoBarSlots slots = new InfoBarSlots(2);
		assertEquals(30f, slots.hold(0, 30f, 0L));
		// a wider value: at once
		assertEquals(40f, slots.hold(0, 40f, 100L));
		// back and forth between two widths: it stays wide
		assertEquals(40f, slots.hold(0, 30f, 200L));
		assertEquals(40f, slots.hold(0, 40f, 300L));
		assertEquals(40f, slots.hold(0, 30f, 400L));
		assertEquals(400L + InfoBarSlots.SHRINK_AFTER_MILLIS, slots.nextChange());
		assertEquals(40f, slots.hold(0, 30f, 400L + InfoBarSlots.SHRINK_AFTER_MILLIS - 1));
		// held long enough: it narrows
		assertEquals(30f, slots.hold(0, 30f, 400L + InfoBarSlots.SHRINK_AFTER_MILLIS));
		assertEquals(Long.MAX_VALUE, slots.nextChange());
		// segments are held on their own
		assertEquals(12f, slots.hold(1, 12f, 0L));
		slots.reset();
		assertEquals(10f, slots.hold(0, 10f, 0L));
	}
}
