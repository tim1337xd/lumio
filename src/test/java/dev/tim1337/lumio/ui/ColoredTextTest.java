package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import org.junit.jupiter.api.Test;

/** Colored text (danger buttons, keys in use elsewhere, chips in their own color) reads at 3:1 at least in the light theme, and is untouched elsewhere. */
class ColoredTextTest {
	/** A card on the light window: a little darker than the window itself, the least contrast colored text has. */
	private static final int LIGHT_CARD = 0xFFEBEBED;
	private static final int[] COLORS = {SystemColors.RED, SystemColors.ORANGE, SystemColors.YELLOW, SystemColors.GREEN, SystemColors.BLUE,
		SystemColors.TEAL, SystemColors.PINK, SystemColors.PURPLE, 0xFF000000 | SystemColors.RED_TEXT_RGB, 0xFF4B9DC3};

	@Test
	void colorsReadOnTheLightTheme() {
		Palette light = MacTheme.LIGHT.palette();
		for (int color : COLORS) {
			int text = light.coloredText(color);
			double ratio = contrast(text, LIGHT_CARD);
			assertTrue(ratio >= 3.0, Integer.toHexString(color) + " reads at " + ratio);
		}
	}

	@Test
	void theDarkThemeKeepsTheColor() {
		assertEquals(SystemColors.RED, MacTheme.DARK.palette().coloredText(SystemColors.RED));
	}

	@Test
	void onGlassColoredTextIsTheThemesText() {
		for (MacTheme theme : new MacTheme[] {MacTheme.GLASS, MacTheme.COLORED}) {
			assertEquals(theme.palette().text(), theme.palette().coloredText(SystemColors.RED));
		}
	}

	@Test
	void aColorWithoutItsAlphaIsOpaque() {
		assertEquals(0xFF, MacTheme.DARK.palette().coloredText(SystemColors.ORANGE_RGB) >>> 24);
		assertEquals(0xFF, MacTheme.LIGHT.palette().coloredText(SystemColors.ORANGE_RGB) >>> 24);
	}

	private static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(int rgb) {
		return 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
	}

	private static double channel(int value) {
		double c = (value & 0xFF) / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}
}
