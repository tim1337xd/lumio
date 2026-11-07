package dev.tim1337.lumio.features.visual.hud;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The slim bars' numbers take their tint and outline from the bar's color: near the mockup's shades for the default colors, and the same hue for any other. */
class SlimNumberColorsTest {
	/** How far a channel may be from the mockup's shade. */
	private static final int CLOSE = 24;

	@Test
	void defaultColorsGiveTheMockupsShades() {
		close(0xFF9A90, StatusBarPainter.numberTint(0xFFF24A40), "health tint");
		close(0x8CC8FF, StatusBarPainter.numberTint(0xFF3A86F8), "mana tint");
		close(0xB6F58A, StatusBarPainter.numberTint(0xFF6FD84B), "experience tint");
		close(0x3B0906, StatusBarPainter.numberEdge(0xFFF24A40), "health outline");
		close(0x061A3B, StatusBarPainter.numberEdge(0xFF3A86F8), "mana outline");
		close(0x0D2A06, StatusBarPainter.numberEdge(0xFF6FD84B), "experience outline");
	}

	@Test
	void anyColorGetsALighterTintAndADarkOutlineOfItsHue() {
		int purple = 0xFF8A2BE2;
		int tint = StatusBarPainter.numberTint(purple);
		int edge = StatusBarPainter.numberEdge(purple);
		assertTrue(brightness(tint) > brightness(purple), "the tint is lighter");
		assertTrue(brightness(edge) < 70, "the outline is very dark");
		// the same hue: blue stays the strongest channel and green the weakest, as in the color
		assertTrue((tint & 0xFF) >= (tint >> 16 & 0xFF) && (tint >> 8 & 0xFF) <= (tint >> 16 & 0xFF));
	}

	private static void close(int expected, int actual, String what) {
		for (int shift = 0; shift <= 16; shift += 8) {
			int difference = Math.abs((expected >> shift & 0xFF) - (actual >> shift & 0xFF));
			assertTrue(difference <= CLOSE, what + ": " + Integer.toHexString(actual & 0xFFFFFF) + " against " + Integer.toHexString(expected));
		}
	}

	private static int brightness(int color) {
		return Math.max(color >> 16 & 0xFF, Math.max(color >> 8 & 0xFF, color & 0xFF));
	}
}
