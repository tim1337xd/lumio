package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Arrays;

/** Colors from sample covers for the Colored theme. */
class CoverColorsTest {
	private static final int SIDE = CoverColors.SIDE;

	/** A cover of bands, top to bottom, each as tall as its share. */
	private static int[] bands(int... colors) {
		int[] pixels = new int[SIDE * SIDE];
		for (int y = 0; y < SIDE; y++) {
			int color = colors[y * colors.length / SIDE];
			for (int x = 0; x < SIDE; x++) {
				pixels[y * SIDE + x] = color;
			}
		}
		return pixels;
	}

	private static float hue(int rgb) {
		return Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null)[0];
	}

	private static float saturation(int rgb) {
		return Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null)[1];
	}

	private static void assertHue(float wanted, int rgb) {
		float distance = Math.abs(hue(rgb) - wanted);
		assertTrue(Math.min(distance, 1f - distance) < 0.03f, "the hue of " + Integer.toHexString(rgb) + " is not " + wanted);
	}

	@Test
	void oneColor() {
		int[] colors = CoverColors.pick(bands(0xC81E3A));
		assertEquals(1, colors.length);
		assertHue(hue(0xC81E3A), colors[0]);
	}

	@Test
	void twoColors() {
		int[] colors = CoverColors.pick(bands(0xC81E3A, 0x1D4FC4));
		assertEquals(2, colors.length, Arrays.toString(colors));
		assertTrue(hue(colors[0]) != hue(colors[1]));
	}

	@Test
	void threeColors() {
		int[] colors = CoverColors.pick(bands(0xC81E3A, 0x1D4FC4, 0x2BC48A));
		assertEquals(3, colors.length, Arrays.toString(colors));
	}

	@Test
	void atMostThree() {
		assertEquals(3, CoverColors.pick(bands(0xC81E3A, 0x1D4FC4, 0x2BC48A, 0xE7B53C, 0xB026FF)).length);
	}

	@Test
	void shadesOfOneColorAreOneColor() {
		assertEquals(1, CoverColors.pick(bands(0xC81E3A, 0xD0203C, 0xB81A34)).length);
	}

	@Test
	void aGrayCoverHasNoColors() {
		assertEquals(0, CoverColors.pick(bands(0x808080, 0xA0A0A0, 0x606060)).length);
	}

	@Test
	void blackAndWhiteDoNotCount() {
		assertEquals(0, CoverColors.pick(bands(0x000000, 0xFFFFFF, 0x050505, 0xFAFAFA)).length);
		int[] colors = CoverColors.pick(bands(0x000000, 0xFFFFFF, 0x1D4FC4, 0x030303));
		assertEquals(1, colors.length);
		assertHue(hue(0x1D4FC4), colors[0]);
	}

	@Test
	void aSpeckIsNoColor() {
		int[] pixels = bands(0x1D4FC4);
		pixels[0] = 0xFF0000;
		assertEquals(1, CoverColors.pick(pixels).length);
	}

	@Test
	void madeRicher() {
		int source = 0x8A5A5A;
		int[] colors = CoverColors.pick(bands(source));
		assertEquals(1, colors.length);
		assertTrue(saturation(colors[0]) > saturation(source));
	}

	@Test
	void scalesALargerCoverDown() {
		int side = 64;
		int[] pixels = new int[side * side];
		Arrays.fill(pixels, 0xFF1D4FC4);
		int[] small = CoverColors.small(pixels, side);
		assertEquals(SIDE * SIDE, small.length);
		assertEquals(0x1D4FC4, small[0]);
	}
}
