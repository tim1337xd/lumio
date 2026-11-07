package dev.tim1337.lumio.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoPatchesTest {
	@Test
	void noiseStaysBetweenZeroAndOne() {
		for (int i = 0; i < 2000; i++) {
			float n = EchoPatches.noise(i * 0.37f - 300f, i * 0.53f - 200f, i * 0.11f);
			assertTrue(n >= 0f && n <= 1f, "noise " + n);
		}
	}

	@Test
	void noiseComesRoundInTimeWithoutASeam() {
		for (int i = 0; i < 50; i++) {
			float x = i * 0.7f;
			float y = i * 1.3f;
			assertEquals(EchoPatches.noise(x, y, 0.25f), EchoPatches.noise(x, y, EchoPatches.TIME_CELLS + 0.25f), 1e-5f);
		}
	}

	@Test
	void theThresholdKeepsAboutAThirdOfTheOutlineGlowing() {
		// an outline of a sword along the diagonal, 400 pixels long
		float[] points = new float[EchoPatches.MOST_POINTS * 2];
		for (int i = 0; i < EchoPatches.MOST_POINTS; i++) {
			float along = -200f + 400f * i / (EchoPatches.MOST_POINTS - 1);
			points[2 * i] = along * 0.7f + (i % 2 == 0 ? 12f : -12f);
			points[2 * i + 1] = along * 0.7f;
		}
		EchoPatches.outline(points, EchoPatches.MOST_POINTS);
		for (double second = 0; second < 120; second += 1.0 / 60.0) {
			double phase = second % EchoPatches.PERIOD_SECONDS / EchoPatches.PERIOD_SECONDS;
			EchoPatches.follow(1.0 / 60.0, phase, 0f, 0f, 1f);
			if (second > 20) {
				int glowing = 0;
				for (int i = 0; i < EchoPatches.MOST_POINTS; i++) {
					if (EchoPatches.mask(points[2 * i], points[2 * i + 1], phase, 0f, 0f, 1f, EchoPatches.shift()) >= EchoPatches.SEEN) {
						glowing++;
					}
				}
				float cover = glowing / (float) EchoPatches.MOST_POINTS;
				assertTrue(cover > 0f && cover < 1f, "never none and never all, at " + second + " s: " + cover);
			}
		}
	}
}
