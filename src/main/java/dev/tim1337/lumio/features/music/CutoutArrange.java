package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;

import java.util.List;

/**
 * Where the words of a Cut-out line stand in their rows. A row (a group) has one main word, with its small words beside it on the same
 * baseline: those sung before it on its left, those after the line's last main word on its right; its symbol stands just before it. Rows are
 * centered on the column's axis, each nudged a little sideways (at most {@link #NUDGE} of the column's width, and two rows next to each other
 * never more than that apart), from the line's text; rows are straight and level. A word's place follows from the line alone and never
 * changes, and no word lies over another. All measures are text units; a word's place is its middle, across from the axis and down from the top of its row.
 * Plain logic, tested without the game.
 */
final class CutoutArrange {
	/** The room under a row, between the words of a row, and beside a symbol: in the column. */
	static final float GROUP_GAP = 5f;
	static final float SMALL_GAP = 7f;
	static final float SYMBOL_GAP = 6f;
	/** A symbol is this share of its word's row. */
	static final float SYMBOL_SIZE = 0.78f;
	/** How far a row is nudged from the axis at most, as a share of the column's width; also the most two neighbouring rows differ by. */
	static final float NUDGE = 0.05f;
	/** Where the baseline lies below a word's middle, as a share of its row: so words of different sizes in a row stand on one line. */
	static final float BASELINE = CutoutLyrics.MIDDLE_ABOVE_BASELINE / CutoutLyrics.ROW;

	/** One word as it is arranged. */
	static class Box {
		/** How wide its drawn letters are, its size as a share of a big word's, and how tall its row is. */
		float width;
		float size;
		float tall;
		/** Its drawn letters from its middle (left, top, right, bottom), and its tile around them. */
		float[] ink = new float[4];
		float[] tile = new float[4];
		/** Whether it lies on a tile, and on a pale one. */
		boolean onTile;
		boolean pale;
		/** Its middle: across from the axis, and down from the top of its row. */
		float x;
		float y;
		/** Its symbol's middle across from the axis and half its size; unused without one. */
		float symbolX;
		float symbolHalf;
		/** Its row's middle across from the axis: where the row is nudged to. */
		float pivot;

		/** @return how far it reaches up and down from its middle: its tile where it has one, else its row */
		float up() {
			return onTile ? Math.max(tall / 2f, -tile[1]) : tall / 2f;
		}

		float down() {
			return onTile ? Math.max(tall / 2f, tile[3]) : tall / 2f;
		}

		/** @return how far it reaches to the left and right of its middle */
		float left() {
			return onTile ? Math.max(width / 2f, -tile[0]) : width / 2f;
		}

		float right() {
			return onTile ? Math.max(width / 2f, tile[2]) : width / 2f;
		}

		void shrink(float by) {
			size *= by;
			width *= by;
			tall *= by;
			for (int edge = 0; edge < 4; edge++) {
				ink[edge] *= by;
				tile[edge] *= by;
			}
		}
	}

	private CutoutArrange() {
	}

	/**
	 * The column: one row per group, its words on one level baseline, the row centered on the axis and nudged a little.
	 *
	 * @param column the column's width
	 * @param pad    the room between a word's letters and the edge of its tile, for a word of full size
	 * @param seed   what the line's nudges come from, so the same line always looks the same
	 * @return how tall each row is, with the room under it
	 */
	static float[] column(List<Piece> pieces, Box[] words, float column, float pad, int seed) {
		return rows(pieces, words, column, pad, seed);
	}

	/**
	 * @param before the nudge of the row above, or NaN for a line's first row
	 * @param room   how far this row can be nudged and stay inside the column
	 * @return a row's nudge: a small step from the row above, kept within the nudge and the room, so two rows never jump far apart
	 */
	static float nudge(int seed, int group, float before, float most, float room) {
		float step = (CutoutWords.hash(seed * 37 + group * 7919 + 11) * 2f - 1f) * most;
		float nudge = Float.isNaN(before) ? step : before + step;
		float limit = Math.max(0f, Math.min(most, room));
		return Math.max(-limit, Math.min(limit, nudge));
	}

	private static float[] rows(List<Piece> pieces, Box[] words, float column, float pad, int seed) {
		int groups = CutoutWords.groups(pieces);
		float[] heights = new float[groups];
		float gap = SMALL_GAP;
		float[] gapBefore = new float[words.length];
		float before = Float.NaN;
		for (int group = 0; group < groups; group++) {
			int main = -1;
			for (int i = 0; i < words.length; i++) {
				// the last full-sized word of the row, or the one with a symbol: a short word that joined the row has none
				if (pieces.get(i).group() == group && !pieces.get(i).small() && (main < 0 || pieces.get(main).symbol() == CutoutLexicon.NO_SYMBOL)) {
					main = i;
				}
			}
			// the main word with its symbol before it (and the room of its tile)
			float mainWidth = 0f;
			float symbolRoom = 0f;
			if (main >= 0) {
				Box word = words[main];
				boolean symbol = pieces.get(main).symbol() != CutoutLexicon.NO_SYMBOL;
				word.symbolHalf = word.tall * SYMBOL_SIZE / 2f;
				symbolRoom = symbol ? 2f * word.symbolHalf + Math.max(SYMBOL_GAP, GAP_EM * em(word)) + (word.onTile ? pad * word.size : 0f) : 0f;
				mainWidth = symbolRoom + word.left() + word.right();
			}
			// the other words of the row: small words, and in a row joined from several (a line of more than three rows) other main words
			float smallWidth = 0f;
			float otherWidth = 0f;
			int others = 0;
			for (int i = 0; i < words.length; i++) {
				if (pieces.get(i).group() == group && i != main) {
					float each = words[i].left() + words[i].right();
					if (pieces.get(i).small()) {
						smallWidth += each;
					} else {
						otherWidth += each;
					}
					others++;
				}
			}
			// the gaps between neighbours, by their drawn letters (a tile by its edge): never less than 0.35 em of the larger, nor a space of the smaller
			float gaps = 0f;
			int previous = -1;
			for (int i = 0; i < words.length; i++) {
				if (pieces.get(i).group() == group) {
					gapBefore[i] = previous >= 0 ? between(words[previous], words[i], gap) : 0f;
					gaps += gapBefore[i];
					previous = i;
				}
			}
			// too wide: the row's words and its symbol give way alike until it fits the column; its gaps never get smaller, and it never reaches out
			float wordsWidth = mainWidth + otherWidth + smallWidth;
			float natural = wordsWidth + gaps;
			float whole = natural > column && wordsWidth > 0f ? Math.max(0f, column - gaps) / wordsWidth : 1f;
			if (whole < 1f && main >= 0) {
				words[main].shrink(whole);
				words[main].symbolHalf *= whole;
				symbolRoom *= whole;
			}
			float width = wordsWidth * whole + gaps;
			// a small nudge sideways, a step from the row above, kept inside the column
			float shift = nudge(seed, group, before, NUDGE * column, (column - width) / 2f);
			before = shift;
			float x = shift - width / 2f;
			float rowUp = 0f;
			float rowDown = 0f;
			Box mainBox = main >= 0 ? words[main] : null;
			previous = -1;
			for (int i = 0; i < words.length; i++) {
				if (pieces.get(i).group() != group) {
					continue;
				}
				Box word = words[i];
				if (i != main) {
					word.shrink(whole);
				}
				// the gap before a word, as it was measured: a row that gave way keeps its gaps whole
				if (previous >= 0) {
					x += gapBefore[i];
				}
				if (i == main) {
					word.symbolX = x + word.symbolHalf;
					x += symbolRoom;
				}
				word.x = x + word.left();
				x += word.left() + word.right();
				previous = i;
			}
			// one level baseline: each word's middle as far above it as its own size sets
			for (int i = 0; i < words.length; i++) {
				if (pieces.get(i).group() != group) {
					continue;
				}
				Box word = words[i];
				word.pivot = shift;
				float below = mainBox == null ? 0f : (mainBox.tall - word.tall) * BASELINE;
				rowUp = Math.max(rowUp, word.up() - below);
				rowDown = Math.max(rowDown, word.down() + below);
			}
			for (int i = 0; i < words.length; i++) {
				if (pieces.get(i).group() != group) {
					continue;
				}
				Box word = words[i];
				word.y = rowUp + (mainBox == null ? 0f : (mainBox.tall - word.tall) * BASELINE);
			}
			heights[group] = (rowUp + rowDown) * ROW_SPACING + GROUP_GAP;
		}
		return heights;
	}

	/**
	 * The gap between two words of a row: at least this share of the larger one's em, and never less than a normal space of the smaller one; how
	 * tall a row is in ems; and how much more room a row takes than its words, so rows never touch.
	 */
	static final float GAP_EM = 0.35f;
	static final float SPACE_EM = 0.28f;
	static final float ROW_SPACING = 1.1f;
	private static final float ROW_PER_EM = 1.35f;

	/** @return a word's em, in text units: from how tall its row is */
	static float em(Box word) {
		return word.tall / ROW_PER_EM;
	}

	/**
	 * @param least the least gap of the layout, in text units
	 * @return the gap between two neighbouring words, between their drawn letters (or a tile's edge): 0.35 em of the larger, never less than a
	 *         space of the smaller or the least
	 */
	static float between(Box before, Box after, float least) {
		return Math.max(least(least), Math.max(GAP_EM * Math.max(em(before), em(after)), SPACE_EM * Math.min(em(before), em(after))));
	}

	/** @return the least gap there is between two words, also in a row that gave way: half the layout's own, which stays some screen pixels */
	private static float least(float gap) {
		return gap * 0.5f;
	}

	/**
	 * @return the box a word takes, from the top of its row: its letters or tile and its symbol; left, top, right, bottom
	 */
	static float[] box(Box word, Piece piece) {
		float left = word.x - word.left();
		float right = word.x + word.right();
		if (piece.symbol() != CutoutLexicon.NO_SYMBOL && !piece.small()) {
			left = Math.min(left, word.symbolX - word.symbolHalf);
		}
		return new float[] {left, word.y - word.up(), right, word.y + word.down()};
	}
}
