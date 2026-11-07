package dev.tim1337.lumio.api.experimental.draw;

/**
 * Text size steps, as scale factors relative to regular text (see the canvas's scaled text). Every panel and screen picks from these, so titles
 * and labels have the same size everywhere. Lyric lines have their own sizes.
 */
public final class TextScale {
	/** Small text: second lines, captions, labels. */
	public static final float SMALL = 0.85f;
	/** Regular text. */
	public static final float BODY = 1f;
	/** Headings inside a page or a panel. */
	public static final float HEADING = 1.25f;
	/** The title of a page. */
	public static final float TITLE = 2f;

	private TextScale() {
	}
}
