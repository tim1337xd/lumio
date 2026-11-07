package dev.tim1337.lumio.features.visual.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The order of the bars in the stack above the hotbar, as rows from the top down, each with one to {@link #MOST_PER_ROW} bars side by side.
 * Saved as text: rows separated by {@code ;}, bars of a row by {@code ,} ("food,air;health,mana;xp"). A bar missing from the text floats on its
 * own. The spots left and right of the hotbar hold up to {@link #MOST_PER_SIDE} bars each and are saved apart, one list per side; a bar on a side
 * is never in the stack. Plain logic without the game, so it is unit tested.
 */
public final class BarStackLayout {
	/** The most bars that share a row; they always get equal widths. */
	public static final int MOST_PER_ROW = 3;
	/** The most bars beside the hotbar, on either side. */
	public static final int MOST_PER_SIDE = 3;
	/** The two sides of the hotbar, and no side at all. */
	public static final int LEFT = 0;
	public static final int RIGHT = 1;
	public static final int NO_SIDE = -1;
	private static final String ROW_SEPARATOR = ";";
	private static final String BAR_SEPARATOR = ",";

	private BarStackLayout() {
	}

	/**
	 * @param offhand the room the offhand item takes on that side, 0 for none
	 * @param gap     the room to the hotbar
	 * @return where a bar beside the hotbar starts: directly next to it, and moving and growing with it
	 */
	public static float sideX(int side, float hotbarX, float hotbarWidth, float offhand, float gap, float barWidth) {
		return side == LEFT ? hotbarX - offhand - gap - barWidth : hotbarX + hotbarWidth + offhand + gap;
	}

	/** @return the top of bars beside the hotbar, centered on the hotbar's middle line, and never below the screen */
	public static float sideTop(float middle, float height, float guiHeight, float margin) {
		return Math.min(middle - height / 2f, guiHeight - margin - height);
	}

	/**
	 * @param known the bar ids that exist; others (from a newer or broken config) are dropped, and so is a second mention of a bar
	 * @return the rows from the top down; never contains an empty row
	 */
	public static List<List<String>> parse(String text, Set<String> known) {
		List<List<String>> rows = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return rows;
		}
		List<String> seen = new ArrayList<>();
		for (String rowText : text.split(ROW_SEPARATOR)) {
			List<String> row = new ArrayList<>();
			for (String part : rowText.split(BAR_SEPARATOR)) {
				String id = part.strip();
				if (known.contains(id) && !seen.contains(id) && row.size() < MOST_PER_ROW) {
					row.add(id);
					seen.add(id);
				}
			}
			if (!row.isEmpty()) {
				rows.add(row);
			}
		}
		return rows;
	}

	/**
	 * @param known the bar ids that exist; others are dropped, and so is a second mention of a bar
	 * @param taken bars already on the other side, which stay there
	 * @return the bars of a side from the top down, at most {@link #MOST_PER_SIDE}
	 */
	public static List<String> parseSide(String text, Set<String> known, List<String> taken) {
		List<String> side = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return side;
		}
		for (String part : text.split(BAR_SEPARATOR)) {
			String id = part.strip();
			if (known.contains(id) && !side.contains(id) && !taken.contains(id) && side.size() < MOST_PER_SIDE) {
				side.add(id);
			}
		}
		return side;
	}

	/** @return a side as saved text */
	public static String formatSide(List<String> side) {
		return String.join(BAR_SEPARATOR, side);
	}

	/**
	 * Takes the bars of the sides out of the rows; those of a side that does not fit on the screen now go on top of the stack instead, so a narrow
	 * window never loses them.
	 *
	 * @param alone a bar that keeps a row of its own
	 */
	public static void withSides(List<List<String>> rows, List<String> left, List<String> right, boolean leftFits, boolean rightFits, String alone) {
		for (List<String> side : List.of(left, right)) {
			side.forEach(id -> remove(rows, id));
		}
		if (!rightFits) {
			addOnTop(rows, right, alone);
		}
		if (!leftFits) {
			addOnTop(rows, left, alone);
		}
	}

	private static void addOnTop(List<List<String>> rows, List<String> side, String alone) {
		List<String> row = new ArrayList<>(side);
		if (row.remove(alone)) {
			rows.addFirst(new ArrayList<>(List.of(alone)));
		}
		if (!row.isEmpty()) {
			rows.addFirst(row);
		}
	}

	/** @return how far a point is from a rectangle {x, y, width, height}; 0 inside it */
	public static float distance(float[] rect, float x, float y) {
		float dx = Math.max(0f, Math.max(rect[0] - x, x - (rect[0] + rect[2])));
		float dy = Math.max(0f, Math.max(rect[1] - y, y - (rect[1] + rect[3])));
		return (float) Math.hypot(dx, dy);
	}

	/**
	 * Which spot beside the hotbar a dragged bar lands in: the nearer one within reach, unless the stack above is nearer still.
	 *
	 * @param left  the left spot, or null while it takes no bar
	 * @param right the right spot, or null while it takes no bar
	 * @param above the stack
	 * @return {@link #LEFT}, {@link #RIGHT} or {@link #NO_SIDE}
	 */
	public static int nearestSide(float x, float y, float[] left, float[] right, float[] above, float reach) {
		float toLeft = left == null ? Float.MAX_VALUE : distance(left, x, y);
		float toRight = right == null ? Float.MAX_VALUE : distance(right, x, y);
		float nearest = Math.min(toLeft, toRight);
		if (nearest > reach || nearest >= distance(above, x, y)) {
			return NO_SIDE;
		}
		return toLeft <= toRight ? LEFT : RIGHT;
	}

	/** @return where in a side a bar dropped at a height goes, from the middles of the bars there, from the top down */
	public static int sideIndexAt(float[] middles, int count, float y) {
		int index = 0;
		while (index < count && y > middles[index]) {
			index++;
		}
		return index;
	}

	/** @return the rows as saved text */
	public static String format(List<List<String>> rows) {
		StringBuilder text = new StringBuilder();
		for (List<String> row : rows) {
			if (row.isEmpty()) {
				continue;
			}
			if (!text.isEmpty()) {
				text.append(ROW_SEPARATOR);
			}
			text.append(String.join(BAR_SEPARATOR, row));
		}
		return text.toString();
	}

	/** @return whether the bar is in one of the rows */
	public static boolean contains(List<List<String>> rows, String id) {
		return rowOf(rows, id) >= 0;
	}

	/** @return the index of the row holding the bar, or -1 */
	public static int rowOf(List<List<String>> rows, String id) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).contains(id)) {
				return i;
			}
		}
		return -1;
	}

	/** Takes the bar out of its row; a row left empty is removed. */
	public static void remove(List<List<String>> rows, String id) {
		int row = rowOf(rows, id);
		if (row >= 0) {
			rows.get(row).remove(id);
			if (rows.get(row).isEmpty()) {
				rows.remove(row);
			}
		}
	}

	/** Puts the bar into a row of its own at the index (0 is the top, the number of rows the bottom, next to the hotbar). */
	public static void insertRow(List<List<String>> rows, int index, String id) {
		remove(rows, id);
		List<String> row = new ArrayList<>();
		row.add(id);
		rows.add(Math.max(0, Math.min(index, rows.size())), row);
	}

	/**
	 * Puts the bar at the left or right end of a row, which it then shares.
	 *
	 * @return false when the row is full (or does not exist), and nothing changed
	 */
	public static boolean insertInRow(List<List<String>> rows, int rowIndex, boolean left, String id) {
		if (rowIndex < 0 || rowIndex >= rows.size()) {
			return false;
		}
		List<String> target = rows.get(rowIndex);
		boolean already = target.contains(id);
		if (!already && target.size() >= MOST_PER_ROW) {
			return false;
		}
		// taking the bar out of its old row may remove that row and shift the target's index; the target list itself stays the same
		remove(rows, id);
		if (!rows.contains(target)) {
			rows.add(Math.min(rowIndex, rows.size()), target);
		}
		if (left) {
			target.addFirst(id);
		} else {
			target.add(id);
		}
		return true;
	}

	/**
	 * Where a bar dragged over the stack lands: between two rows, or at an end of a row it then shares.
	 *
	 * @param row     for a new row the index it is inserted at; for sharing the index of the row
	 * @param share   whether the bar shares the row
	 * @param left    for sharing, whether it goes to the left end
	 */
	public record Drop(int row, boolean share, boolean left) {
		public static final Drop NONE = new Drop(-1, false, false);
	}

	/**
	 * Finds where a dragged bar lands, from the rows' places on screen (without the dragged bar).
	 *
	 * @param rowTops    top of every row, from the top down
	 * @param rowBottoms bottom of every row
	 * @param rowLeft    left edge of the rows (they all share the hotbar's edges)
	 * @param rowWidth   width of the rows
	 * @param canShare   per row, whether another bar may join it (not full, not the experience bar)
	 * @param x          the dragged bar's center across
	 * @param y          its center down
	 * @param edge       how far from a row's end the bar has to be to share the row
	 */
	public static Drop dropAt(float[] rowTops, float[] rowBottoms, int rows, float rowLeft, float rowWidth, boolean[] canShare, float x, float y,
		float edge) {
		for (int i = 0; i < rows; i++) {
			// the middle half of a row's height shares it, so a bar between two rows does not jump into one of them
			float quarter = (rowBottoms[i] - rowTops[i]) / 4f;
			boolean onRow = y >= rowTops[i] + quarter && y <= rowBottoms[i] - quarter;
			if (onRow && canShare[i]) {
				if (x <= rowLeft + edge) {
					return new Drop(i, true, true);
				}
				if (x >= rowLeft + rowWidth - edge) {
					return new Drop(i, true, false);
				}
			}
		}
		// otherwise between rows: before the first row whose middle is below the bar
		for (int i = 0; i < rows; i++) {
			if (y < (rowTops[i] + rowBottoms[i]) / 2f) {
				return new Drop(i, false, false);
			}
		}
		return new Drop(rows, false, false);
	}

	/**
	 * Applies a drop to the rows: the bar moves there from wherever it was. The drop's indices count the rows without the bar, as
	 * {@link #dropAt} sees them while it is dragged.
	 */
	public static void apply(List<List<String>> rows, Drop drop, String id) {
		remove(rows, id);
		if (drop.share()) {
			if (!insertInRow(rows, drop.row(), drop.left(), id)) {
				insertRow(rows, drop.row() + 1, id);
			}
		} else {
			insertRow(rows, drop.row(), id);
		}
	}

	/** @return a deep copy of the rows, to change without touching the original */
	public static List<List<String>> copy(List<List<String>> rows) {
		List<List<String>> copy = new ArrayList<>(rows.size());
		for (List<String> row : rows) {
			copy.add(new ArrayList<>(row));
		}
		return copy;
	}
}
