package dev.tim1337.lumio.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A thin line stands out from the bright, colored field of the Colored theme at least as much as from the Dark theme's panel. */
class MacThemeLineTest {
	private static final int DARK_PANEL = 0x1C1C1E;
	/** The field in the colors of a real config: saturated purple and blue, as measured in the panels and at their brightest. */
	private static final int[] FIELDS = {0xA23BFF, 0x2F6BFF, 0x793FB3, 0x7A41B3};
	/** Very bright colors a player can pick: a low-opacity line cannot match Dark there, and stays visible. */
	private static final int[] BRIGHT_FIELDS = {0xFF7AD9, 0x30D158};
	private static final double STILL_VISIBLE = 1.1;
	private static final float[] OPACITIES = {0.08f, 0.1f, 0.13f, 0.14f, 0.16f};

	private static double linear(int channel) {
		double value = channel / 255.0;
		return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static double luminance(int rgb) {
		return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
	}

	/** @return white at an opacity over a color */
	private static int lit(int rgb, float opacity) {
		int out = 0;
		for (int shift = 16; shift >= 0; shift -= 8) {
			int channel = rgb >> shift & 0xFF;
			out |= Math.round(channel + (255 - channel) * opacity) << shift;
		}
		return out;
	}

	private static double standsOut(int under, float opacity) {
		return (luminance(lit(under, opacity)) + 0.05) / (luminance(under) + 0.05);
	}

	@Test
	void onTheFieldAsVisibleAsOnDark() {
		for (float opacity : OPACITIES) {
			double dark = standsOut(DARK_PANEL, MacTheme.DARK.lineOpacity(opacity));
			for (int field : FIELDS) {
				double colored = standsOut(field, MacTheme.COLORED.lineOpacity(opacity));
				assertTrue(colored >= dark, Integer.toHexString(field) + " at " + opacity + ": " + colored + " against " + dark);
			}
		}
	}

	@Test
	void onAVeryBrightFieldStillVisible() {
		for (float opacity : OPACITIES) {
			for (int field : BRIGHT_FIELDS) {
				assertTrue(standsOut(field, MacTheme.COLORED.lineOpacity(opacity)) >= STILL_VISIBLE, Integer.toHexString(field));
			}
		}
	}

	@Test
	void theOtherThemesKeepTheirLines() {
		assertEquals(0.08f, MacTheme.DARK.lineOpacity(0.08f));
		assertEquals(0.08f, MacTheme.GLASS.lineOpacity(0.08f));
		assertEquals(0.08f, MacTheme.LIGHT.lineOpacity(0.08f));
	}

	@Test
	void itStaysALightLine() {
		assertTrue(MacTheme.COLORED.lineOpacity(0.16f) < 0.5f);
		assertEquals(0f, MacTheme.COLORED.lineOpacity(0f));
	}
}
