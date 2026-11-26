package dev.tim1337.lumio.ui;

/**
 * Centering of text that has more than one face: the place comes from the width of the face that is really drawn. The game's font has no bold
 * of its own (its bold is every glyph drawn twice, one pixel apart, so every glyph is a pixel wider), and see-through bold text is drawn once
 * in it; text centered with another face's width sits off to the side by half the difference. Plain logic, tested without the game.
 */
public final class TextCenter {
	/** From this opacity (of 255) the two copies of the game font's bold are not told apart, and bold text is drawn as bold; below it once, in the regular face. */
	public static final int OPAQUE_ENOUGH = 0xF4;

	private TextCenter() {
	}

	/**
	 * @param gameFont whether the game's own font is the menu's font
	 * @param alpha    the opacity the text is drawn at, from 0 to 255
	 * @param symbols  whether the text has symbols the font lacks, which are drawn run by run in the bold face
	 * @return whether text asked for in a bold face is drawn in it; otherwise it is drawn in the regular face
	 */
	public static boolean drawnBold(boolean gameFont, int alpha, boolean symbols) {
		return symbols || !gameFont || alpha >= OPAQUE_ENOUGH;
	}

	/** @return the width to place the text by: the one of the face it is drawn in */
	public static float drawnWidth(boolean drawnBold, float regularWidth, float boldWidth) {
		return drawnBold ? boldWidth : regularWidth;
	}

	/** @return the left edge of text of a width whose middle lies on the center */
	public static float left(float center, float drawnWidth) {
		return center - drawnWidth / 2f;
	}
}
