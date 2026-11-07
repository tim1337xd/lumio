package dev.tim1337.lumio.api;

/**
 * A line an add-on adds to an item's tooltip, in the one separate block Lumio keeps apart from what the game sends.
 *
 * @param kind     what the line is
 * @param text     the title of a heading, the name of a row, or the text of a footnote
 * @param value    the value of a row or what stands at the right of a heading; "" for none
 * @param note     a short word after a row's value, such as "per day"; "" for none
 * @param valueRgb the color of the value as 0xRRGGBB, or {@link #PLAIN} for the usual one
 */
public record TooltipLine(Kind kind, String text, String value, String note, int valueRgb) {
	/** What a value shows while it is not known: never a made-up one. */
	public static final String MISSING = "—";
	/** The usual color of a value. */
	public static final int PLAIN = -1;

	/** What a line is. */
	public enum Kind {
		HEADING,
		ROW,
		FOOTNOTE
	}

	/** @return a small title of a part of the block, with something beside it, or "" */
	public static TooltipLine heading(String title, String aside, int asideRgb) {
		return new TooltipLine(Kind.HEADING, title, aside, "", asideRgb);
	}

	/** @return a row: a name, its value and a short word after it, or "" */
	public static TooltipLine row(String name, String value, String note) {
		return new TooltipLine(Kind.ROW, name, value, note, PLAIN);
	}

	/** @return a small line, such as where the values come from */
	public static TooltipLine footnote(String text) {
		return new TooltipLine(Kind.FOOTNOTE, text, "", "", PLAIN);
	}
}
