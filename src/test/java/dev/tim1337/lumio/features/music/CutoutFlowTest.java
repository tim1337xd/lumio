package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.features.music.CutoutFlow.Mode;

import org.junit.jupiter.api.Test;

/** The flow of a Cut-out column: Auto scrolls while standing still or filmed and turns pages while moving, and changes only at a line start. */
class CutoutFlowTest {
	@Test
	void theSettingWinsUnlessAuto() {
		CutoutFlow flow = new CutoutFlow();
		flow.moved(0L);
		assertEquals(Mode.PAGES, flow.atLineStart(Mode.PAGES, 60_000L, true));
		assertEquals(Mode.SCROLL, flow.atLineStart(Mode.SCROLL, 100L, false));
	}

	@Test
	void autoScrollsWhenStillOrFilmed() {
		CutoutFlow flow = new CutoutFlow();
		flow.moved(10_000L);
		assertEquals(Mode.PAGES, flow.atLineStart(Mode.AUTO, 11_000L, false), "moved a second ago");
		assertEquals(Mode.SCROLL, flow.atLineStart(Mode.AUTO, 11_000L, true), "the cinematic camera films");
		assertEquals(Mode.SCROLL, flow.atLineStart(Mode.AUTO, 10_000L + CutoutFlow.STILL_MS, false), "still for two seconds");
	}

	@Test
	void autoSwitchesOnlyAtALineStart() {
		CutoutFlow flow = new CutoutFlow();
		flow.moved(0L);
		assertEquals(Mode.PAGES, flow.atLineStart(Mode.AUTO, 500L, false));
		// standing still for a long time in the middle of a line: the flow stays what the line started with
		for (long t = 600L; t < 9_000L; t += 16L) {
			assertEquals(Mode.PAGES, flow.shown());
		}
		assertEquals(Mode.SCROLL, flow.atLineStart(Mode.AUTO, 9_000L, false));
		// moving again: still scrolling until the next line starts
		flow.moved(9_500L);
		assertEquals(Mode.SCROLL, flow.shown());
		assertEquals(Mode.PAGES, flow.atLineStart(Mode.AUTO, 10_000L, false));
	}

	@Test
	void walkingAcrossALineStartTurnsPagesStandingScrolls() {
		CutoutFlow flow = new CutoutFlow();
		// walking: a step every tick for three seconds, a line starting in the middle
		for (long t = 0L; t <= 3_000L; t += 50L) {
			flow.moved(t);
			if (t == 1_500L) {
				assertEquals(Mode.PAGES, flow.atLineStart(Mode.AUTO, t, false), "walking");
			}
		}
		assertEquals(true, flow.moving(3_000L));
		// standing still (looking around moves nothing): the next line start after two seconds scrolls
		assertEquals(Mode.PAGES, flow.atLineStart(Mode.AUTO, 4_000L, false), "only one second still");
		assertEquals(Mode.SCROLL, flow.atLineStart(Mode.AUTO, 5_200L, false), "still for more than two seconds");
		assertEquals(false, flow.moving(5_200L));
	}
}
