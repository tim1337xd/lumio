package dev.tim1337.lumio.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Anchor and offset in GUI units: the same distance from the same edge on any screen. */
class HudAnchorsTest {
	private static final float EPS = 0.001f;

	@Test
	void anElementKeepsItsDistanceFromItsEdgeOnAnyScreen() {
		// 8 GUI units from the right: the same on 1080p at GUI scale 2 (960 wide) and at scale 4 (480 wide)
		assertEquals(960 - 8 - 100, HudAnchors.start(HudAnchors.END, 8, 100, 960), EPS);
		assertEquals(480 - 8 - 100, HudAnchors.start(HudAnchors.END, 8, 100, 480), EPS);
		assertEquals(8, HudAnchors.start(HudAnchors.START, 8, 100, 480), EPS);
	}

	@Test
	void theMiddleAnchorCountsFromTheCenter() {
		assertEquals(480 / 2f + 10 - 50, HudAnchors.start(HudAnchors.MIDDLE, 10, 100, 480), EPS);
	}

	@Test
	void offsetIsTheInverseOfStart() {
		for (int anchor = HudAnchors.START; anchor <= HudAnchors.END; anchor++) {
			float start = HudAnchors.start(anchor, 17, 64, 640);
			assertEquals(17, HudAnchors.offset(anchor, start, 64, 640), EPS);
		}
	}

	@Test
	void theNearestAnchorIsTheThirdTheCenterIsIn() {
		assertEquals(HudAnchors.START, HudAnchors.nearest(10, 50, 480));
		assertEquals(HudAnchors.MIDDLE, HudAnchors.nearest(200, 80, 480));
		assertEquals(HudAnchors.END, HudAnchors.nearest(381, 90, 480));
	}
}
