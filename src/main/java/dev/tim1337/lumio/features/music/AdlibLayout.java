package dev.tim1337.lumio.features.music;

import java.util.function.ToDoubleFunction;

/**
 * Where adlibs stand around their line, the same in every place the lyrics show (the world, the sky, the cinematic camera, the players and the
 * info bar): smaller and a bit dimmer than the line, always a clear gap away from its real bounds (its glow included: half a row, a quarter in
 * the world), never on the line, its
 * highlight box or another adlib. They stack below the line; where there is no room below they go beside it. A long adlib wraps to at most two
 * balanced rows (see {@link LyricWrap}) and gets a little smaller rather than taking a third row. The line keeps the room for its adlibs from when
 * it is laid out, so an adlib never moves it. Plain numbers in, plain numbers out, so it is unit tested.
 */
final class AdlibLayout {
	/** An adlib's size as a share of its line's, and the smallest a long one gets instead of a third row. */
	static final float SCALE = 0.65f;
	static final float LEAST_SCALE = 0.55f;
	/** How bright an adlib is next to its line. */
	static final float ALPHA = 0.75f;
	/** The gap between the line's bounds and its adlibs, as a share of the line's height; between two adlibs half of it. */
	static final float GAP_SHARE = 0.5f;
	/** The gap in the world (around you, in the sky and in the film), where the adlibs stand closer to their line: a quarter of a row. */
	static final float WORLD_GAP_SHARE = 0.25f;
	static final float BETWEEN_SHARE = 0.25f;
	/** The most rows an adlib wraps to. */
	static final int MOST_ROWS = 2;
	/** Rounding the room below a line may cost, as a share of a row: an adlib that fits the room kept for it exactly still counts as fitting. */
	private static final float FIT_SLACK = 0.01f;
	/** Steps of the search for the narrowest width that keeps an adlib to two rows, in text units. */
	private static final double PRECISION = 0.5;

	private AdlibLayout() {
	}

	/** @return the gap kept between a line of this height (its glow included) and its adlibs */
	static float gap(float lineHeight) {
		return lineHeight * GAP_SHARE;
	}

	/** @return the gap between two adlibs of a line of this height */
	static float between(float lineHeight) {
		return lineHeight * BETWEEN_SHARE;
	}

	/**
	 * @return the width to break an adlib at, at its own size: the room when it takes at most two rows there, else the narrowest width at which it
	 *         does, so a long adlib never gets a third row
	 * @param width the width of a text at the adlib's size
	 */
	static double wrapWidth(String text, double room, ToDoubleFunction<String> width) {
		if (LyricWrap.balanced(text, room, MOST_ROWS, width).size() <= MOST_ROWS) {
			return room;
		}
		double low = room;
		double high = Math.max(room, width.applyAsDouble(text));
		while (high - low > PRECISION) {
			double middle = (low + high) / 2;
			if (LyricWrap.balanced(text, middle, MOST_ROWS, width).size() <= MOST_ROWS) {
				high = middle;
			} else {
				low = middle;
			}
		}
		return high;
	}

	/**
	 * @return the adlib's size as a share of the line's: the usual one, or smaller (down to {@link #LEAST_SCALE}) when its wider row is still too
	 *         wide for the room at the usual size
	 * @param widestAtUsual the adlib's wider row at the usual size
	 */
	static float fitScale(float widestAtUsual, float room) {
		if (widestAtUsual <= room || widestAtUsual <= 0f) {
			return SCALE;
		}
		return Math.max(LEAST_SCALE, SCALE * room / widestAtUsual);
	}

	/**
	 * Places a line's adlibs: a column below the line, a clear gap below its bottom and a smaller one between them, each with its end aligned to
	 * the line's end; an adlib that does not fit below (past {@code lowest}) stands beside the line instead, a gap to its right, stacked from its
	 * top. Writes each adlib's left and top into {@code out} (two values per adlib).
	 *
	 * @param lineLeft the line's bounds, its glow included, in any units with y downwards
	 * @param rowHeight the height of one of the line's rows: the gaps follow it, so a line broken into rows keeps the same gaps as
	 *                  one that is not (and as the room kept below it, see {@link #room})
	 * @param widths   the adlibs' widths, glow included
	 * @param heights  their heights, glow included
	 * @param lowest   how far down adlibs may reach, or {@link Float#MAX_VALUE} for no limit
	 */
	static void place(float lineLeft, float lineTop, float lineRight, float lineBottom, float rowHeight, float[] widths, float[] heights, int count,
					  float lowest, float[] out) {
		place(lineLeft, lineTop, lineRight, lineBottom, rowHeight, GAP_SHARE, widths, heights, count, lowest, out);
	}

	/** The same, with the gap to the line as a share of a row of its own (the world's, see {@link #WORLD_GAP_SHARE}). */
	static void place(float lineLeft, float lineTop, float lineRight, float lineBottom, float rowHeight, float gapShare, float[] widths, float[] heights,
					  int count, float lowest, float[] out) {
		float gap = rowHeight * gapShare;
		float between = between(rowHeight);
		float below = lineBottom + gap;
		float side = lineTop;
		float sideLeft = lineRight + gap;
		boolean beside = false;
		for (int i = 0; i < count; i++) {
			// once one goes beside the line the rest follow it, so none below reaches into them
			beside |= below + heights[i] > lowest + rowHeight * FIT_SLACK;
			if (!beside) {
				out[2 * i] = Math.max(lineLeft, lineRight - widths[i]);
				out[2 * i + 1] = below;
				below += heights[i] + between;
				// an adlib below that reaches past the line's end pushes the ones beside it further out
				sideLeft = Math.max(sideLeft, out[2 * i] + widths[i] + gap);
			} else {
				out[2 * i] = sideLeft;
				out[2 * i + 1] = side;
				side += heights[i] + between;
			}
		}
	}

	/** @return the height a line keeps below it for adlibs of these heights, the gap included; 0 without adlibs */
	static float room(float lineHeight, float[] heights, int count) {
		return room(lineHeight, GAP_SHARE, heights, count);
	}

	/** The same, with the gap to the line as a share of a row of its own. */
	static float room(float lineHeight, float gapShare, float[] heights, int count) {
		if (count <= 0) {
			return 0f;
		}
		float room = lineHeight * gapShare;
		for (int i = 0; i < count; i++) {
			room += heights[i] + (i > 0 ? between(lineHeight) : 0f);
		}
		return room;
	}
}
