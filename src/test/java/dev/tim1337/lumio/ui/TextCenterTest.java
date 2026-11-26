package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A centered title lies on the progress bar's center in both fonts: it is placed by the width of the face it is drawn in. The fonts are
 * modelled by their glyph advances (the game has no fonts without its resources): the mod's font with a semibold face of its own, the
 * game's font with a bold that makes every glyph one pixel wider.
 */
class TextCenterTest {
	private static final String[] TITLES = {"Loading terrain", "Joining world", "Reloading resources", "Lumio", "Übergänge laden", "WWWW mmmm kkkk"};
	private static final int[] SCREENS = {854, 1280, 1920, 2560, 3840};
	private static final int BAR_WIDTH = 220;
	/** The title's size against the font's own, as on a wide screen. */
	private static final float[] SCALES = {1f, 1.6f, 2.5f};

	private interface Face {
		float width(String text, boolean bold);
	}

	/** The game's pixel font: narrow and wide glyphs, and a bold of one more pixel a glyph. */
	private static final Face GAME = (text, bold) -> {
		float width = 0f;
		for (int i = 0; i < text.length(); i++) {
			char letter = text.charAt(i);
			width += (letter == 'i' || letter == 'l' ? 2 : letter == ' ' || letter == 't' ? 4 : 6) + (bold ? 1 : 0);
		}
		return width;
	};
	/** The mod's font: proportional, with a semibold face a little wider. */
	private static final Face MOD = (text, bold) -> {
		float width = 0f;
		for (int i = 0; i < text.length(); i++) {
			char letter = text.charAt(i);
			float advance = letter == 'i' || letter == 'l' ? 2.4f : letter == 'w' || letter == 'm' || letter == 'W' ? 8.1f : letter == ' ' ? 2.8f : 5.6f;
			width += advance * (bold ? 1.06f : 1f);
		}
		return width;
	};

	private static void centered(Face face, boolean gameFont, int alpha) {
		for (int screen : SCREENS) {
			int barLeft = (screen - BAR_WIDTH) / 2;
			float barCenter = barLeft + BAR_WIDTH / 2f;
			for (float scale : SCALES) {
				for (String title : TITLES) {
					boolean bold = TextCenter.drawnBold(gameFont, alpha, false);
					float placedBy = TextCenter.drawnWidth(bold, face.width(title, false) * scale, face.width(title, true) * scale);
					float left = TextCenter.left(screen / 2f, placedBy);
					// what is drawn: the face the drawing chooses for this opacity
					float drawn = face.width(title, bold) * scale;
					float middle = left + drawn / 2f;
					assertEquals(barCenter, middle, 1f, title + " at " + screen + ", size " + scale + ", opacity " + alpha);
				}
			}
		}
	}

	@Test
	void theTitleIsOnTheBarsCenterInTheGamesFont() {
		centered(GAME, true, 255);
	}

	@Test
	void theTitleIsOnTheBarsCenterInTheModsFont() {
		centered(MOD, false, 255);
	}

	@Test
	void aFadingTitleStaysCenteredInBothFonts() {
		for (int alpha : new int[] {0, 40, 128, 243, 244, 255}) {
			centered(GAME, true, alpha);
			centered(MOD, false, alpha);
		}
	}

	/** The mistake this guards against: bold text placed by the regular face's width sits right of the middle, by 40 px and more on a wide screen. */
	@Test
	void placedByTheRegularWidthTheBoldTitleIsOffCenter() {
		float scale = 5f;
		float regular = GAME.width("Loading terrain", false) * scale;
		float bold = GAME.width("Loading terrain", true) * scale;
		float middle = TextCenter.left(1280f, regular) + bold / 2f;
		assertTrue(middle - 1280f > 30f, "the old placing: " + (middle - 1280f));
		assertEquals(1280f, TextCenter.left(1280f, bold) + bold / 2f, 0.001f);
	}

	@Test
	void theGamesFontDrawsSeeThroughBoldOnce() {
		assertTrue(TextCenter.drawnBold(true, 255, false));
		assertTrue(TextCenter.drawnBold(true, TextCenter.OPAQUE_ENOUGH, false));
		assertFalse(TextCenter.drawnBold(true, TextCenter.OPAQUE_ENOUGH - 1, false));
		assertTrue(TextCenter.drawnBold(true, 10, true), "text with symbols is drawn run by run, in the bold face");
		assertTrue(TextCenter.drawnBold(false, 10, false), "the mod's font has a real semibold face at every opacity");
	}
}
