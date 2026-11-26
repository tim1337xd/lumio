package dev.tim1337.lumio.features.music;

/**
 * Where the rows of one sung line go in a Cut-out column's stack. A line sung again right after itself may take the earlier copy's place
 * instead of standing under it, but only while that copy still stands wholly in the room and each new row fits where its old row stood;
 * otherwise it is stacked as a new line, under the lines before. So no row (and no word) ever lands where the room no longer shows: it was the
 * cause of words missing in a small room, where the earlier copy had already scrolled out over the top and the repeat was put there.
 * Plain logic, tested without the game.
 */
final class CutoutRowStack {
	private CutoutRowStack() {
	}

	/**
	 * Places the rows {@code from} to {@code last} of a line.
	 *
	 * @param tops      every row's top in the stack; this line's are set
	 * @param heights   every row's height with the room under it
	 * @param fits      every row's own height
	 * @param repeatOf  per row, the row of the earlier copy it may stand in, or -1; this line's are cleared where it can not
	 * @param bottom    where the stack ends before this line
	 * @param room      how tall the room is
	 * @return where the stack ends after this line
	 */
	static float placeLine(float[] tops, float[] heights, float[] fits, int[] repeatOf, int from, int last, float bottom, float room) {
		if (from <= last && repeatOf[from] >= 0 && !fitsInPlace(tops, fits, repeatOf, from, last, bottom, room)) {
			for (int r = from; r <= last; r++) {
				repeatOf[r] = -1;
			}
		}
		float end = bottom;
		for (int r = from; r <= last; r++) {
			if (repeatOf[r] >= 0) {
				// sung again right away, where it stood: the stack does not move for it
				tops[r] = tops[repeatOf[r]];
				continue;
			}
			tops[r] = end;
			end += heights[r];
		}
		return end;
	}

	/** @return whether a repeat can stand in its earlier copy's place: that copy still wholly in the room, and every new row no taller than its old one */
	static boolean fitsInPlace(float[] tops, float[] fits, int[] repeatOf, int from, int last, float bottom, float room) {
		for (int r = from; r <= last; r++) {
			int old = repeatOf[r];
			if (old < 0 || fits[r] > fits[old] + 0.5f || tops[old] < bottom - room) {
				return false;
			}
		}
		return true;
	}
}
