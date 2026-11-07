package dev.tim1337.lumio.ui.tooltip;

import dev.tim1337.lumio.api.TooltipLine;

import java.util.List;

/**
 * The sizes of the block of added lines, in tooltip pixels. A row has its name on the left, then its value in a column aligned on the right, and a short word
 * after that. The text's widths come from outside, so it is unit tested.
 */
public final class AddedBlock {
	static final int LINE_GAP = 4;

	static final int HEADING_HEIGHT = 15;
	/** A heading keeps apart what stands above it. */
	static final int HEADING_TOP = 4;
	static final int ROW_HEIGHT = 11;
	static final int FOOTNOTE_HEIGHT = 10;
	/** Between a row's name and its value, and between the value and the word after it. */
	static final int NAME_GAP = 14;
	static final int NOTE_GAP = 5;
	/** The least room of the values: about a price as it is usually written. */
	static final String VALUE_ROOM = "888.8k";

	/** How wide a text is, in the size it is drawn. */
	@FunctionalInterface
	public interface Measure {
		int width(String text, boolean small);
	}

	private AddedBlock() {
	}

	/** @return the height of the block, with its line */
	public static int height(List<TooltipLine> lines) {
		int height = LINE_GAP + 1 + LINE_GAP;
		for (TooltipLine line : lines) {
			height += lineHeight(line);
		}
		return height;
	}

	static int lineHeight(TooltipLine line) {
		return switch (line.kind()) {
			case HEADING -> HEADING_HEIGHT;
			case ROW -> ROW_HEIGHT;
			case FOOTNOTE -> FOOTNOTE_HEIGHT;
		};
	}

	/** @return the columns of the rows: the widest name, value and word after it */
	public static int[] columns(List<TooltipLine> lines, Measure measure) {
		int name = 0;
		int value = 0;
		int note = 0;
		for (TooltipLine line : lines) {
			if (line.kind() == TooltipLine.Kind.ROW) {
				name = Math.max(name, measure.width(line.text(), false));
				value = Math.max(value, measure.width(line.value(), false));
				note = Math.max(note, line.note().isEmpty() ? 0 : measure.width(line.note(), true));
			}
		}
		// a value still on its way ("…") keeps the room of a number, so the words after it stand where they will stay
		if (value > 0) {
			value = Math.max(value, measure.width(VALUE_ROOM, false));
		}
		return new int[]{name, value, note};
	}

	/** @return the width the block needs */
	public static int width(List<TooltipLine> lines, Measure measure) {
		int[] columns = columns(lines, measure);
		int rows = columns[0] + NAME_GAP + columns[1] + (columns[2] > 0 ? NOTE_GAP + columns[2] : 0);
		int width = 0;
		for (TooltipLine line : lines) {
			switch (line.kind()) {
				case HEADING -> width = Math.max(width, measure.width(line.text(), true) + (line.value().isEmpty() ? 0 : NAME_GAP + measure.width(line.value(), true)));
				case ROW -> width = Math.max(width, rows);
				case FOOTNOTE -> width = Math.max(width, measure.width(line.text(), true));
			}
		}
		return width;
	}
}
