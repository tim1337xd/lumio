package dev.tim1337.lumio.ui.motion;

import dev.tim1337.lumio.api.experimental.motion.AnimClock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressSmootherTest {
	private static final float FRAME = 1f / 60f;

	/** Runs the bar at 60 frames per second until it is full. @return the seconds it took */
	private static float secondsUntilFull(ProgressSmoother bar) {
		float seconds = 0f;
		while (!bar.full() && seconds < 10f) {
			bar.update(FRAME);
			seconds += FRAME;
		}
		return seconds;
	}

	@Test
	void jumpFrom20To100TakesAtLeastFourTenths() {
		ProgressSmoother bar = new ProgressSmoother();
		bar.report(0.2f);
		for (int i = 0; i < 120; i++) {
			bar.update(FRAME);
		}
		assertEquals(0.2f, bar.value(), 0.005f);
		bar.report(1f);
		float seconds = secondsUntilFull(bar);
		assertTrue(seconds >= 0.4f, "took " + seconds + " s");
		assertTrue(seconds < 1.5f, "took " + seconds + " s");
	}

	@Test
	void neverMovesBackAndNeverOvershoots() {
		ProgressSmoother bar = new ProgressSmoother();
		float last = 0f;
		float[] reports = {0.1f, 0.5f, 0.45f, 0.3f, 0.9f, 0.6f, 1f};
		for (float report : reports) {
			bar.report(report);
			for (int i = 0; i < 20; i++) {
				float value = bar.update(FRAME);
				assertTrue(value >= last, "moved back from " + last + " to " + value);
				assertTrue(value <= bar.target() + 1e-6f, "passed its target");
				last = value;
			}
		}
	}

	@Test
	void newPhaseCarriesOnFromWhereTheBarIs() {
		ProgressSmoother bar = new ProgressSmoother();
		bar.report(0.8f);
		for (int i = 0; i < 300; i++) {
			bar.update(FRAME);
		}
		float before = bar.value();
		// the next step counts from 0 again: the bar keeps its place and fills the rest
		bar.report(0f);
		assertEquals(before, bar.update(FRAME), 1e-4f);
		bar.report(0.5f);
		assertEquals(before + 0.5f * (1f - before), bar.target(), 1e-4f);
	}

	@Test
	void longFrameStaysWithinTopSpeed() {
		ProgressSmoother bar = new ProgressSmoother();
		bar.report(1f);
		// a frame clamped to a thirtieth of a second, as the animation clock does after a stall
		float value = bar.update(AnimClock.MAX_STEP);
		assertTrue(value <= ProgressSmoother.MAX_SPEED * AnimClock.MAX_STEP + 1e-5f, "jumped to " + value);
	}

	@Test
	void completeFillsTheBar() {
		ProgressSmoother bar = new ProgressSmoother();
		bar.report(0.3f);
		bar.complete();
		secondsUntilFull(bar);
		assertTrue(bar.full());
		bar.reset();
		assertEquals(0f, bar.value());
	}
}
