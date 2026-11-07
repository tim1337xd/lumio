package dev.tim1337.lumio.ui.motion;

import dev.tim1337.lumio.api.experimental.motion.AnimClock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The animation clock, the loading screen's fade rules and the startup camera curve. */
class TimingTest {
	private static final long MILLIS = 1_000_000L;

	@Test
	void clockClampsStallsToAThirtieth() {
		AnimClock clock = new AnimClock();
		assertEquals(0f, clock.tick(1_000 * MILLIS));
		assertEquals(0.016f, clock.tick(1_016 * MILLIS), 1e-5f);
		// a stall of two seconds moves the animation by one thirtieth only
		assertEquals(AnimClock.MAX_STEP, clock.tick(3_016 * MILLIS), 1e-6f);
		assertEquals(0.016f + AnimClock.MAX_STEP, clock.seconds(), 1e-5f);
		// time going backwards never moves the clock back
		assertEquals(0f, clock.tick(2_000 * MILLIS));
	}

	@Test
	void shortLoadStaysForTheMinimumTime() {
		// ready and full almost at once: no fade before the minimum time
		assertFalse(LoadingTimeline.mayFade(0f, 0.05f, 0.1f, 0.3f));
		assertTrue(LoadingTimeline.mayFade(0f, 0.05f, 0.1f, LoadingTimeline.MIN_SHOWN_SECONDS));
	}

	@Test
	void fullBarRestsBeforeTheFade() {
		assertFalse(LoadingTimeline.mayFade(0f, 2f, -1f, 2.1f));
		assertFalse(LoadingTimeline.mayFade(0f, 2f, 2.2f, 2.3f));
		assertTrue(LoadingTimeline.mayFade(0f, 2f, 2.2f, 2.2f + LoadingTimeline.REST_SECONDS));
	}

	@Test
	void extraWaitIsCapped() {
		// the bar never got full (a stall): the fade starts at the latest after the cap
		assertFalse(LoadingTimeline.mayFade(0f, 5f, -1f, 5f + LoadingTimeline.MAX_EXTRA_SECONDS - 0.01f));
		assertTrue(LoadingTimeline.mayFade(0f, 5f, -1f, 5f + LoadingTimeline.MAX_EXTRA_SECONDS + 0.001f));
		// nothing fades before the game is ready
		assertFalse(LoadingTimeline.mayFade(0f, 5f, 1f, 4f));
	}

	@Test
	void fadeGoesFromOneToZero() {
		assertEquals(1f, LoadingTimeline.fadeAlpha(1f, 1f));
		assertEquals(0.5f, LoadingTimeline.fadeAlpha(1f, 1f + LoadingTimeline.FADE_SECONDS / 2f), 1e-5f);
		assertEquals(0f, LoadingTimeline.fadeAlpha(1f, 1f + LoadingTimeline.FADE_SECONDS));
	}

	@Test
	void cameraCurveIsSmoothWithoutOvershoot() {
		assertEquals(1f, CameraPath.descent(0f));
		assertEquals(0f, CameraPath.descent(CameraPath.SECONDS));
		assertEquals(0.5f, CameraPath.descent(CameraPath.SECONDS / 2f), 1e-5f);
		float last = 1f;
		float lastTilt = 1f;
		for (float t = 0f; t <= CameraPath.SECONDS + 0.5f; t += 0.01f) {
			float descent = CameraPath.descent(t);
			float tilt = CameraPath.tilt(t);
			assertTrue(descent <= last + 1e-6f && descent >= 0f && descent <= 1f, "descent " + descent + " at " + t);
			assertTrue(tilt <= lastTilt + 1e-6f && tilt >= 0f, "tilt " + tilt + " at " + t);
			// the tilt leads: the camera looks down earlier than it arrives
			assertTrue(tilt <= descent + 1e-6f, "tilt behind the descent at " + t);
			last = descent;
			lastTilt = tilt;
		}
		// slow at both ends
		assertTrue(1f - CameraPath.descent(0.05f) < 0.001f);
		assertTrue(CameraPath.descent(CameraPath.SECONDS - 0.05f) < 0.001f);
	}
}
