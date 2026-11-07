package dev.tim1337.lumio.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A dragged menu window still snaps to the screen's edges and middle and to the other window, now without any lines. */
class WindowSnapTest {
	private static final int SCREEN_WIDTH = 960;
	private static final int SCREEN_HEIGHT = 540;
	private static final float WIDTH = 400f;
	private static final float HEIGHT = 300f;
	private static final float EXACT = 0.001f;

	private static WindowSnap snapped(float left, float top) {
		WindowSnap snap = new WindowSnap();
		snap.find(left, top, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, false, 0f, 0f, 0f, 0f);
		return snap;
	}

	@Test
	void snapsToTheMiddleOfTheScreen() {
		WindowSnap snap = snapped(283f, 117f);
		assertEquals((SCREEN_WIDTH - WIDTH) / 2f, snap.left, EXACT);
		assertEquals((SCREEN_HEIGHT - HEIGHT) / 2f, snap.top, EXACT);
	}

	@Test
	void snapsToTheEdgesWithItsMargin() {
		WindowSnap snap = snapped(11f, 5f);
		assertEquals(WindowSnap.DISTANCE, snap.left, EXACT);
		assertEquals(WindowSnap.DISTANCE, snap.top, EXACT);
		snap = snapped(SCREEN_WIDTH - WIDTH - 11f, SCREEN_HEIGHT - HEIGHT - 5f);
		assertEquals(SCREEN_WIDTH - WIDTH - WindowSnap.DISTANCE, snap.left, EXACT);
		assertEquals(SCREEN_HEIGHT - HEIGHT - WindowSnap.DISTANCE, snap.top, EXACT);
	}

	@Test
	void farFromEverythingItStaysWhereItIs() {
		WindowSnap snap = snapped(120f, 70f);
		assertEquals(120f, snap.left, EXACT);
		assertEquals(70f, snap.top, EXACT);
	}

	@Test
	void snapsBesideTheOtherWindow() {
		WindowSnap snap = new WindowSnap();
		snap.find(310f, 70f, 200f, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, true, 100f, 200f, 200f, 100f);
		assertEquals(300f + WindowSnap.DISTANCE, snap.left, EXACT);
	}

	@Test
	void shiftDragsFreely() {
		WindowSnap snap = new WindowSnap();
		snap.free(11f, 5f);
		assertEquals(11f, snap.left, EXACT);
		assertEquals(5f, snap.top, EXACT);
	}
}
