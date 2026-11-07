package dev.tim1337.lumio.ui;

import net.minecraft.util.Mth;

import java.util.List;

/**
 * Snapping of a dragged HUD element: to the screen edges (with a small margin), the screen's center lines, and the edges and centers of the
 * other elements, or next to them with a small gap. The lines are gathered once when a drag starts and kept in fixed arrays, so moving the
 * mouse allocates nothing. A line holds the element until it is pulled further away than it caught it, so it does not flicker.
 */
final class HudEditSnap {
	/** An element snaps to lines closer than this and only lets go beyond {@link #SNAP_OUT}. */
	private static final int SNAP_IN = 6;
	private static final int SNAP_OUT = 12;
	/** Gap left between two elements placed side by side, and the margin kept from the screen edges. */
	static final int GAP = 4;
	static final int MARGIN = 8;
	/** Spacing of the grid positions snap to when the grid is on. */
	static final int GRID = 8;
	/** How far apart two elements may be across for the gap next to one to count. */
	private static final int LEVEL = 24;
	private static final int MOST_LINES = 256;
	private static final int MOST_BOXES = 64;
	private static final int SCREEN = -1;

	// the lines of both axes (0 across, 1 down): where, which edge of the element snaps to it (0 start, 1 center, 2 end, -1 any), whose box
	// it belongs to (or SCREEN), and whether it is the gap next to that box
	private final int[][] at = new int[2][MOST_LINES];
	private final int[][] edge = new int[2][MOST_LINES];
	private final int[][] owner = new int[2][MOST_LINES];
	private final boolean[][] gap = new boolean[2][MOST_LINES];
	private final int[] count = new int[2];
	private final int[][] boxes = new int[MOST_BOXES][4];
	private final String[] names = new String[MOST_BOXES];
	/** The elements of the boxes: one that moves along with the dragged one (the item name above the hotbar) is no snap target. */
	private final EditableHud[] boxOwners = new EditableHud[MOST_BOXES];
	private int boxCount;

	// the line each axis is held by (or -1) and the element edge on it
	private final int[] held = {-1, -1};
	private final int[] heldEdge = new int[2];
	/** The snapped position from the last move. */
	final int[] snapped = new int[2];

	/** Gathers the lines for a drag: the screen's and those of every other element shown. */
	void start(List<EditableHud> elements, EditableHud dragged, int screenWidth, int screenHeight) {
		start(elements, java.util.Set.of(dragged), screenWidth, screenHeight);
	}

	/** Same for several elements moved together, which do not snap to each other. */
	void start(List<EditableHud> elements, java.util.Collection<EditableHud> moving, int screenWidth, int screenHeight) {
		count[0] = 0;
		count[1] = 0;
		boxCount = 0;
		held[0] = -1;
		held[1] = -1;
		addLine(0, MARGIN, 0, SCREEN, false);
		addLine(0, screenWidth / 2, 1, SCREEN, false);
		addLine(0, screenWidth - MARGIN, 2, SCREEN, false);
		addLine(1, MARGIN, 0, SCREEN, false);
		addLine(1, screenHeight / 2, 1, SCREEN, false);
		addLine(1, screenHeight - MARGIN, 2, SCREEN, false);
		for (int i = 0; i < elements.size() && boxCount < MOST_BOXES; i++) {
			EditableHud other = elements.get(i);
			if (moving.contains(other) || !other.hudShown()) {
				continue;
			}
			int[] box = other.hudBounds();
			int index = boxCount++;
			System.arraycopy(box, 0, boxes[index], 0, 4);
			names[index] = other.hudName();
			boxOwners[index] = other;
			for (int axis = 0; axis < 2; axis++) {
				int start = box[axis];
				int size = box[axis + 2];
				addLine(axis, start, -1, index, false);
				addLine(axis, start + size / 2, -1, index, false);
				addLine(axis, start + size, -1, index, false);
				addLine(axis, start - GAP, 2, index, true);
				addLine(axis, start + size + GAP, 0, index, true);
			}
		}
	}

	private void addLine(int axis, int position, int snapEdge, int box, boolean isGap) {
		int i = count[axis];
		if (i < MOST_LINES) {
			at[axis][i] = position;
			edge[axis][i] = snapEdge;
			owner[axis][i] = box;
			gap[axis][i] = isGap;
			count[axis] = i + 1;
		}
	}

	/**
	 * Snaps a position for the element's box: to the lines (unless free), then to the grid where no line holds it, and keeps it on screen.
	 * The result is in {@link #snapped}. @return whether a line holds the element on either axis
	 */
	boolean snap(int x, int y, int width, int height, boolean free, boolean grid, int screenWidth, int screenHeight) {
		int[] position = snapped;
		position[0] = x;
		position[1] = y;
		for (int axis = 0; axis < 2; axis++) {
			int start = position[axis];
			int size = axis == 0 ? width : height;
			int other = position[1 - axis];
			int otherSize = axis == 0 ? height : width;
			held[axis] = free ? -1 : hold(axis, start, size, other, otherSize);
			if (held[axis] >= 0) {
				start += at[axis][held[axis]] - edgeOf(start, size, heldEdge[axis]);
			} else if (grid) {
				start = Math.round(start / (float) GRID) * GRID;
			}
			position[axis] = Mth.clamp(start, 0, Math.max(0, (axis == 0 ? screenWidth : screenHeight) - size));
		}
		return held[0] >= 0 || held[1] >= 0;
	}

	/** @return the line the axis snaps to: the held one while it is close enough, otherwise the nearest one within reach; or -1 */
	private int hold(int axis, int start, int size, int across, int acrossSize) {
		int current = held[axis];
		if (current >= 0 && usable(axis, current, across, acrossSize)
			&& Math.abs(at[axis][current] - edgeOf(start, size, heldEdge[axis])) <= SNAP_OUT) {
			return current;
		}
		int best = -1;
		int bestEdge = 0;
		int distance = SNAP_IN;
		for (int i = 0; i < count[axis]; i++) {
			if (!usable(axis, i, across, acrossSize)) {
				continue;
			}
			for (int e = 0; e < 3; e++) {
				if (edge[axis][i] != -1 && edge[axis][i] != e) {
					continue;
				}
				int d = Math.abs(at[axis][i] - edgeOf(start, size, e));
				// on a tie, a line of another element beats a screen line: it is the more likely intent
				if (d < distance || d == distance && best >= 0 && owner[axis][best] == SCREEN && owner[axis][i] != SCREEN) {
					distance = d;
					best = i;
					bestEdge = e;
				}
			}
		}
		if (best >= 0) {
			heldEdge[axis] = bestEdge;
		}
		return best;
	}

	/** @return whether a line counts now: the gap next to a box only when the element is level with it across */
	private boolean usable(int axis, int line, int across, int acrossSize) {
		if (owner[axis][line] == SCREEN) {
			return true;
		}
		int[] box = boxes[owner[axis][line]];
		int[] now = boxOwners[owner[axis][line]].hudBounds();
		if (now[0] != box[0] || now[1] != box[1]) {
			return false;
		}
		if (!gap[axis][line]) {
			return true;
		}
		int boxStart = box[1 - axis];
		int boxSize = box[3 - axis];
		return across < boxStart + boxSize + LEVEL && across + acrossSize > boxStart - LEVEL;
	}

	private static int edgeOf(int start, int size, int which) {
		return which == 0 ? start : which == 1 ? start + size / 2 : start + size;
	}

	// ------------------------------------------------------------------------------------------------------- feedback

	/** @return the line held on an axis, or -1 */
	int held(int axis) {
		return held[axis];
	}

	/** @return what an axis is snapped to, in words: "the screen center", "the top edge", "Mining profit"; null if nothing */
	String describe(int axis) {
		int line = held[axis];
		if (line < 0) {
			return null;
		}
		if (owner[axis][line] == SCREEN) {
			return switch (edge[axis][line]) {
				case 0 -> axis == 0 ? "the left edge" : "the top edge";
				case 1 -> "the screen center";
				default -> axis == 0 ? "the right edge" : "the bottom edge";
			};
		}
		return names[owner[axis][line]];
	}

	/** @return whether the element is centered on the screen on an axis */
	boolean centered(int axis) {
		int line = held[axis];
		return line >= 0 && owner[axis][line] == SCREEN && edge[axis][line] == 1;
	}
}
