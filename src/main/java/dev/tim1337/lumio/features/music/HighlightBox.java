package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.ui.Ui;

/**
 * The highlight box of the lyrics in the info bar, the small player and the window: a rounded box in the lyrics color
 * behind the sung word, gliding to the next word without overshoot and never jumping (see {@link LyricBox}); along a line without word timing it
 * glides evenly.
 * The text on it is white or dark, whichever reads on its color. Positions are in the units of the text it is drawn behind.
 */
final class HighlightBox {
	/** A box color lighter than this gets dark text on it. */
	private static final float LIGHT = 0.6f;
	static final int DARK_TEXT = 0xFF14141C;
	/** How opaque the box is. */
	private static final float FILL = 0.92f;
	/** A word held at least this long starts to fill up as it is sung, and from this long on it does so fully; a quick word is lit and gone. */
	private static final float LONG_WORD_FROM_MS = 500f;
	private static final float LONG_WORD_FULL_MS = 900f;

	private final LyricBox edges = new LyricBox();

	/** Starts again: the next part grows in from its start. */
	void reset() {
		edges.reset();
	}

	/**
	 * Moves the box toward a part of a line.
	 *
	 * @param key   the line (and its row) the part is in; on another the box grows in from the part's start, and the old one fades
	 * @param from  the part's left edge, padding included
	 * @param to    its right edge
	 * @param glide whether it glides softly along a line without word timing
	 */
	void follow(int key, float from, float to, float dt, boolean animated, boolean glide) {
		edges.follow(key, from, to, dt, animated, glide);
	}

	float left() {
		return edges.left();
	}

	float right() {
		return edges.right();
	}

	/** @return whether a point of the text is under the box now */
	boolean covers(float at) {
		return at >= edges.left() && at <= edges.right();
	}

	/** Draws the box, and the old one fading out, with the text's origin at {@code originX}. */
	void draw(Ui ui, float originX, float top, float height, float radius, int color, float alpha) {
		draw(ui, originX, top, height, radius, color, alpha, true);
	}

	/** Same; without the old box where it stood on another row, which is drawn elsewhere. */
	void draw(Ui ui, float originX, float top, float height, float radius, int color, float alpha, boolean withOld) {
		if (withOld && edges.oldAlpha > 0.01f) {
			ui.roundedSmooth(originX + edges.oldLeft, top, Math.max(1f, edges.oldRight - edges.oldLeft), height, radius,
				Ui.withOpacity(color, FILL * alpha * edges.oldAlpha));
		}
		float width = edges.right() - edges.left();
		if (width > 0.5f) {
			ui.roundedSmooth(originX + edges.left(), top, width, height, Math.min(radius, width / 2f), Ui.withOpacity(color, FILL * alpha));
		}
	}

	/**
	 * Draws the box, kept whole within a part of the text: a box wider than that part (a word wider than the room) is shortened there, so it still has
	 * four rounded corners and is never cut off.
	 */
	void drawWithin(Ui ui, float originX, float top, float height, float radius, int color, float alpha, float from, float to) {
		float left = Math.max(edges.left(), from);
		float width = Math.min(edges.right(), to) - left;
		if (width > 0.5f) {
			ui.roundedSmooth(originX + left, top, width, height, Math.min(radius, width / 2f), Ui.withOpacity(color, FILL * alpha));
		}
	}

	/** @return how much a word sung for this long counts as a long word that fills up, from 0 to 1 */
	static float longWord(long wordMs) {
		return Math.max(0f, Math.min(1f, (wordMs - LONG_WORD_FROM_MS) / (LONG_WORD_FULL_MS - LONG_WORD_FROM_MS)));
	}

	/** @return the color of text on a box of this color: dark on a light one, white otherwise */
	static int textOn(int boxColor) {
		return Ui.luminance(boxColor) > LIGHT ? DARK_TEXT : 0xFFFFFFFF;
	}

	/** @return where the word containing the character at {@code index} starts in the text */
	static int wordStart(CharSequence text, int index) {
		int i = Math.max(0, Math.min(index, text.length()));
		while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
			i--;
		}
		return i;
	}

	/** @return where that word ends */
	static int wordEnd(CharSequence text, int index) {
		int i = Math.max(0, Math.min(index, text.length()));
		while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		return i;
	}
}
