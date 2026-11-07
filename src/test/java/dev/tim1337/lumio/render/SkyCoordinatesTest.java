package dev.tim1337.lumio.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyCoordinatesTest {
	/** One step of the fraction byte, the most a coordinate may be off after the round trip. */
	private static final float STEP = 1f / SkyCoordinates.FRACTION_STEPS;
	/** The world border of the game. */
	private static final double BORDER = 29_999_984.0;

	/** The coordinate as the shader reads it back from what is sent. */
	private static float shaderSees(double coordinate) {
		long whole = SkyCoordinates.whole(coordinate);
		return SkyCoordinates.decodeLikeShader(SkyCoordinates.wholeByte(whole, 0), SkyCoordinates.wholeByte(whole, 1), SkyCoordinates.wholeByte(whole, 2),
			SkyCoordinates.wholeByte(whole, 3), SkyCoordinates.fraction(coordinate));
	}

	private static void assertRoundTrip(double coordinate) {
		float seen = shaderSees(coordinate);
		assertTrue(seen <= coordinate + 1e-6 && seen > coordinate - STEP - 1e-6, coordinate + " was read as " + seen);
	}

	@Test
	void negativeZeroAndPositiveComeBack() {
		for (double coordinate : new double[]{-1234.75, -1.0, -0.5, -0.001, 0.0, 0.001, 0.5, 1.0, 1234.75}) {
			assertRoundTrip(coordinate);
		}
	}

	@Test
	void crossingZeroIsOneSmallStep() {
		// walking from -0.2 to 0.2 in steps of a hundredth: every step is at most one fraction step, never a jump
		float before = shaderSees(-0.2);
		for (int i = -19; i <= 20; i++) {
			float now = shaderSees(i / 100.0);
			assertTrue(now - before >= 0f && now - before <= 0.01f + STEP, "a jump at " + i / 100.0 + ": " + before + " to " + now);
			before = now;
		}
	}

	@Test
	void justBelowZeroIsBelowZero() {
		assertEquals(-0.001f, shaderSees(-0.001), STEP);
		assertTrue(shaderSees(-0.001) < 0f, "below zero stays below zero");
		assertEquals(-1f + 0.5f, shaderSees(-0.5), STEP);
	}

	@Test
	void theOldWrapAtPlusAndMinus65536IsGone() {
		for (double coordinate : new double[]{65535.5, 65536.0, 65536.5, -65535.5, -65536.0, -65536.5, 32767.9, 32768.0, -32768.1}) {
			assertRoundTrip(coordinate);
		}
		assertTrue(shaderSees(65536.2) > shaderSees(65535.8), "no wrap back to 0 at 65536");
		assertTrue(shaderSees(-65536.2) < shaderSees(-65535.8), "no wrap at -65536");
	}

	@Test
	void everyByteBoundaryIsSmooth() {
		for (double edge : new double[]{256.0, 65536.0, 1048576.0, -256.0, -65536.0, -1048576.0}) {
			assertEquals(edge, shaderSees(edge), 1e-3, "at " + edge);
			assertTrue(shaderSees(edge - 0.5) < shaderSees(edge), "below " + edge);
			assertTrue(shaderSees(edge + 0.5) > shaderSees(edge), "above " + edge);
		}
	}

	@Test
	void theWholeWorldFitsWithoutWrapping() {
		assertEquals((float) BORDER, shaderSees(BORDER), 2f);
		assertEquals((float) -BORDER, shaderSees(-BORDER), 2f);
		assertTrue(shaderSees(-BORDER) < shaderSees(BORDER));
	}

	@Test
	void theFractionStaysInItsByte() {
		assertEquals(0, SkyCoordinates.fraction(5.0));
		assertEquals(128, SkyCoordinates.fraction(5.5));
		assertEquals(128, SkyCoordinates.fraction(-5.5), "rounded down below zero as well");
		assertEquals(255, SkyCoordinates.fraction(5.99999));
		assertEquals(255, SkyCoordinates.fraction(-0.00001));
	}

	@Test
	void blocksRoundDownBelowZero() {
		long minusOne = SkyCoordinates.whole(-0.25);
		long zero = SkyCoordinates.whole(0.25);
		assertEquals(1L, zero - minusOne, "-0.25 is in block -1, not 0");
	}
}
