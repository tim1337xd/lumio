package dev.tim1337.lumio.render;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the ink of a word really lies, in the face the Cut-out preset's letters are drawn in (Inter Bold, as the lyric sprites lay it out):
 * the tile behind a word is made around its drawn letters, not around the room the word takes in its row, so a letter that reaches past
 * that room (a "k", a "W", an umlaut's dots, what hangs below the line) is still inside the tile. Measured once per word and kept. Only the
 * font is needed, not the game.
 */
public final class CutoutInk {
	/** The most words whose ink is kept. */
	private static final int MOST = 4000;
	private static final Map<String, float[]> BOUNDS = new ConcurrentHashMap<>();
	private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
	private static Font font;
	private static boolean tried;

	private CutoutInk() {
	}

	private static synchronized Font font() {
		if (!tried) {
			tried = true;
			try (InputStream in = CutoutInk.class.getResourceAsStream("/assets/lumio/font/inter_bold.ttf")) {
				font = in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(1000f);
			} catch (Exception | Error e) {
				font = null;
			}
		}
		return font;
	}

	/**
	 * @return the ink of a word at a font size of 1: its left and top edge, its right and bottom edge, from where the word's first letter
	 *         starts on the baseline (down is positive, so the top is negative), and last how far the word advances; null if the font is
	 *         missing or the word has no ink
	 */
	public static float[] bounds(String word) {
		float[] known = BOUNDS.get(word);
		if (known != null) {
			return known.length == 0 ? null : known;
		}
		Font face = font();
		float[] bounds = new float[0];
		if (face != null && !word.isBlank()) {
			try {
				TextLayout layout = new TextLayout(word, face, CONTEXT);
				Rectangle2D ink = layout.getBounds();
				if (ink.getWidth() > 0.0 && ink.getHeight() > 0.0) {
					bounds = new float[] {(float) ink.getMinX() / 1000f, (float) ink.getMinY() / 1000f, (float) ink.getMaxX() / 1000f, (float) ink.getMaxY() / 1000f,
						layout.getAdvance() / 1000f};
				}
			} catch (Exception | Error e) {
				bounds = new float[0];
			}
		}
		if (BOUNDS.size() > MOST) {
			BOUNDS.clear();
		}
		BOUNDS.put(word, bounds);
		return bounds.length == 0 ? null : bounds;
	}
}
