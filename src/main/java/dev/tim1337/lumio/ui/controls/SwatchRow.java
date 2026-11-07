package dev.tim1337.lumio.ui.controls;

/** How a row of color swatches fits its width: its cells never get smaller and never pass the edge, so what does not fit goes on the next line. */
final class SwatchRow {
	private SwatchRow() {
	}

	/** @return the width of all cells in one line */
	static int fullWidth(int count, int cell) {
		return count * cell;
	}

	/** @return how many cells a line of this width holds, at least one */
	static int perLine(int width, int cell) {
		return Math.max(1, width / Math.max(1, cell));
	}

	/** @return how many lines the cells take */
	static int lines(int count, int width, int cell) {
		int perLine = perLine(width, cell);
		return Math.max(1, (count + perLine - 1) / perLine);
	}

	/** @return the left of a cell, from the row's left */
	static int cellX(int index, int width, int cell) {
		return index % perLine(width, cell) * cell;
	}

	/** @return which line a cell is on */
	static int line(int index, int width, int cell) {
		return index / perLine(width, cell);
	}

	/** @return the cell at a place in the row, or -1 where there is none */
	static int cellAt(int offsetX, int offsetY, int count, int width, int cell, int lineHeight) {
		if (offsetX < 0 || offsetY < 0 || offsetX >= width) {
			return -1;
		}
		int perLine = perLine(width, cell);
		int column = offsetX / Math.max(1, cell);
		if (column >= perLine) {
			return -1;
		}
		int index = offsetY / Math.max(1, lineHeight) * perLine + column;
		return index < count ? index : -1;
	}
}
