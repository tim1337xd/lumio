package dev.tim1337.lumio.ui.motion;

import dev.tim1337.lumio.api.experimental.motion.Easing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The shared curves give exactly what the copies they replaced gave, and stay within 0..1 outside their range. */
class EasingTest {
	private static final int STEPS = 1000;

	@Test
	void easeOutMatchesTheOldCubic() {
		for (int i = 0; i <= STEPS; i++) {
			float t = i / (float) STEPS;
			float inverse = 1f - t;
			assertEquals(1f - inverse * inverse * inverse, Easing.easeOut(t), 0f);
		}
	}

	@Test
	void smoothMatchesTheOldSmoothstep() {
		for (int i = 0; i <= STEPS; i++) {
			float t = i / (float) STEPS;
			assertEquals(t * t * (3f - 2f * t), Easing.smooth(t), 0f);
		}
	}

	@Test
	void bothAreClamped() {
		assertEquals(0f, Easing.easeOut(-0.5f), 0f);
		assertEquals(1f, Easing.easeOut(1.5f), 0f);
		assertEquals(0f, Easing.smooth(-0.5f), 0f);
		assertEquals(1f, Easing.smooth(1.5f), 0f);
	}
}
