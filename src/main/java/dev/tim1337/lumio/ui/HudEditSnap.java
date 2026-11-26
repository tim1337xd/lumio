package dev.tim1337.lumio.ui;

import net.minecraft.util.Mth;

import java.util.Collection;
import java.util.List;

/**
 * Snapping of a dragged or resized HUD element. By default ({@link HudSnapMode#CENTER_GRID}) an element's center and edges snap to the screen's
 * center lines, to the lines of the editor's grid, and to the screen's edges with the usual margin; {@link HudSnapMode#GRID_ONLY} keeps only the
 * grid. Other elements are no target unless asked for: then their edges and centers, and the gap beside them, come on top, as before.
 * <p>
 * The snap distance is small (3 GUI units, so 6 pixels at GUI scale 2): placing freely near a line stays easy. A target holds the element until
 * it is pulled twice as far away, so it does not flicker. Holding Shift turns snapping off for the drag. All in GUI units, so it works the same
 * at every GUI scale. The lines are gathered once when a drag starts and kept in fixed arrays: moving the mouse allocates nothing.
 */
final class HudEditSnap {
	/** An element snaps to a target closer than this, in GUI units, and only lets go beyond {@link #SNAP_OUT}. */
	static final int SNAP_IN = 3;
	static final int SNAP_OUT = 6;
	/** Gap left between two elements placed side by side, and the margin kept from the screen edges. */
	static final int GAP = 4;
	static final int MARGIN = 8;
	/** How far apart two elements may be across for the gap next to one to count. */
	private static final int LEVEL = 24;
	private static final int MOST_LINES = 256;
	private static final int MOST_BOXES = 64;

	/** What holds an axis. */
	static final int NONE = 0;
	static final int CENTER = 1;
	static final int EDGE = 2;
	static final int GRID = 3;
	static final int ELEMENT = 4;

	/** Which part of the element is on the target: its start (left or top), center or end. */
	static final int START = 0;
	static final int MIDDLE = 1;
	static final int END = 2;
	/** Any of the three may be on the line. */
	private static final int ANY = -1;

	// the lines of both axes (0 across, 1 down): where, which part of the element snaps to it, what kind it is, whose box it belongs to (or -1),
	// and whether it is the gap next to that box
	private final int[][] at = new int[2][MOST_LINES];
	private final int[][] part = new int[2][MOST_LINES];
	private final int[][] kind = new int[2][MOST_LINES];
	private final int[][] owner = new int[2][MOST_LINES];
	private final boolean[][] gap = new boolean[2][MOST_LINES];
	private final int[] count = new int[2];
	private final int[][] boxes = new int[MOST_BOXES][4];
	private final String[] names = new String[MOST_BOXES];
	/** The elements of the boxes, to see whether one moved since the drag began (one moving along with the dragged one); null in tests. */
	private final EditableHud[] boxOwners = new EditableHud[MOST_BOXES];
	private int boxCount;
	private int gridStep;
	private boolean useGrid;

	// what holds each axis now: its kind, where it is, the part of the element on it and, for a line, which one
	private final int[] heldKind = {NONE, NONE};
	private final int[] heldAt = new int[2];
	private final int[] heldPart = new int[2];
	private final int[] heldLine = {-1, -1};
	/** The snapped position from the last move. */
	final int[] snapped = new int[2];

	/** Gathers the targets for dragging one element: the screen's, the grid's, and the other elements' when they are asked for. */
	void start(List<EditableHud> elements, EditableHud dragged, int screenWidth, int screenHeight, HudSnapMode mode, int grid, boolean others) {
		start(elements, java.util.Set.of(dragged), screenWidth, screenHeight, mode, grid, others);
	}

	/** Same for several elements moved together, which do not snap to each other. */
	void start(List<EditableHud> elements, Collection<EditableHud> moving, int screenWidth, int screenHeight, HudSnapMode mode, int grid, boolean others) {
		begin(screenWidth, screenHeight, mode, grid);
		if (!others) {
			return;
		}
		for (int i = 0; i < elements.size() && boxCount < MOST_BOXES; i++) {
			EditableHud other = elements.get(i);
			if (moving.contains(other) || !other.hudShown()) {
				continue;
			}
			addBox(other.hudBounds(), other.hudName(), other);
		}
	}

	/** Starts a drag with the screen's and the grid's targets only; boxes of other elements are added with {@link #addBox}. */
	void begin(int screenWidth, int screenHeight, HudSnapMode mode, int grid) {
		begin(screenWidth, screenHeight, mode, grid, true);
	}

	/** @param edges whether the screen's edges (with their margin) are targets too, beside its center lines; the HUD editor has them */
	void begin(int screenWidth, int screenHeight, HudSnapMode mode, int grid, boolean edges) {
		count[0] = 0;
		count[1] = 0;
		boxCount = 0;
		heldKind[0] = NONE;
		heldKind[1] = NONE;
		heldLine[0] = -1;
		heldLine[1] = -1;
		gridStep = Math.max(1, grid);
		useGrid = mode != HudSnapMode.OFF;
		if (mode == HudSnapMode.CENTER_GRID) {
			for (int axis = 0; axis < 2; axis++) {
				int size = axis == 0 ? screenWidth : screenHeight;
				addLine(axis, size / 2, ANY, CENTER, -1, false);
				if (edges) {
					addLine(axis, MARGIN, START, EDGE, -1, false);
					addLine(axis, size - MARGIN, END, EDGE, -1, false);
				}
			}
		}
	}

	/** Adds another element as a target: its edges and center, and the gap beside it. */
	void addBox(int[] box, String name, EditableHud element) {
		if (boxCount >= MOST_BOXES) {
			return;
		}
		int index = boxCount++;
		System.arraycopy(box, 0, boxes[index], 0, 4);
		names[index] = name;
		boxOwners[index] = element;
		for (int axis = 0; axis < 2; axis++) {
			int start = box[axis];
			int size = box[axis + 2];
			addLine(axis, start, ANY, ELEMENT, index, false);
			addLine(axis, start + size / 2, ANY, ELEMENT, index, false);
			addLine(axis, start + size, ANY, ELEMENT, index, false);
			addLine(axis, start - GAP, END, ELEMENT, index, true);
			addLine(axis, start + size + GAP, START, ELEMENT, index, true);
		}
	}

	private void addLine(int axis, int position, int snapPart, int lineKind, int box, boolean isGap) {
		int i = count[axis];
		if (i < MOST_LINES) {
			at[axis][i] = position;
			part[axis][i] = snapPart;
			kind[axis][i] = lineKind;
			owner[axis][i] = box;
			gap[axis][i] = isGap;
			count[axis] = i + 1;
		}
	}

	/**
	 * Snaps a position for the element's box (unless free, as with Shift held) and keeps it on the screen. The result is in {@link #snapped}.
	 *
	 * @return whether anything holds the element on either axis
	 */
	boolean snap(int x, int y, int width, int height, boolean free, int screenWidth, int screenHeight) {
		snapped[0] = x;
		snapped[1] = y;
		for (int axis = 0; axis < 2; axis++) {
			int start = snapped[axis];
			int size = axis == 0 ? width : height;
			if (free) {
				release(axis);
			} else if (find(axis, start, size, snapped[1 - axis], axis == 0 ? height : width, START, END)) {
				start += heldAt[axis] - partOf(start, size, heldPart[axis]);
			}
			snapped[axis] = Mth.clamp(start, 0, Math.max(0, (axis == 0 ? screenWidth : screenHeight) - size));
		}
		return heldKind[0] != NONE || heldKind[1] != NONE;
	}

	/**
	 * Snaps one edge of an element being resized, its end across (the right edge, or for an element that grows from its center, its right edge
	 * too), to the same targets. @return the snapped position of that edge, or the edge itself when nothing holds it
	 */
	int snapEnd(int end, int top, int height, boolean free) {
		release(1);
		if (free) {
			release(0);
			return end;
		}
		// the edge alone, as a box of no width: only targets an end may sit on count
		return find(0, end, 0, top, height, END, END) ? heldAt[0] : end;
	}

	/**
	 * Snaps one edge pulled along an axis (0 across, 1 down) to the same targets; the other axis keeps what holds it.
	 *
	 * @return the snapped position of that edge, or the edge itself when nothing holds it
	 */
	int snapEdge(int axis, int edge, boolean free) {
		if (free) {
			release(axis);
			return edge;
		}
		return find(axis, edge, 0, 0, 0, END, END) ? heldAt[axis] : edge;
	}

	private void release(int axis) {
		heldKind[axis] = NONE;
		heldLine[axis] = -1;
	}

	/**
	 * Finds what holds an axis: the target held now while it is close enough, else the nearest within reach, a line before the grid on a tie.
	 * Only the element's parts from {@code firstPart} to {@code lastPart} count. @return whether something holds it
	 */
	private boolean find(int axis, int start, int size, int across, int acrossSize, int firstPart, int lastPart) {
		if (heldKind[axis] != NONE && heldPart[axis] >= firstPart && heldPart[axis] <= lastPart
			&& (heldLine[axis] < 0 || usable(axis, heldLine[axis], across, acrossSize))
			&& (heldKind[axis] != GRID || heldAt[axis] % gridStep == 0)
			&& Math.abs(heldAt[axis] - partOf(start, size, heldPart[axis])) <= (heldKind[axis] == GRID ? Math.min(SNAP_OUT, gridStep / 2) : SNAP_OUT)) {
			return true;
		}
		int bestDistance = SNAP_IN + 1;
		int bestKind = NONE;
		int bestAt = 0;
		int bestPart = 0;
		int bestLine = -1;
		for (int i = 0; i < count[axis]; i++) {
			if (!usable(axis, i, across, acrossSize)) {
				continue;
			}
			for (int p = firstPart; p <= lastPart; p++) {
				if (part[axis][i] != ANY && part[axis][i] != p) {
					continue;
				}
				int d = Math.abs(at[axis][i] - partOf(start, size, p));
				// on a tie, the screen's center beats an edge, and another element's line beats the screen's: the more likely intent
				if (d < bestDistance || d == bestDistance && rank(kind[axis][i]) > rank(bestKind)) {
					bestDistance = d;
					bestKind = kind[axis][i];
					bestAt = at[axis][i];
					bestPart = p;
					bestLine = i;
				}
			}
		}
		if (useGrid) {
			for (int p = firstPart; p <= lastPart; p++) {
				int edge = partOf(start, size, p);
				int line = Math.round(edge / (float) gridStep) * gridStep;
				int d = Math.abs(line - edge);
				if (d < bestDistance) {
					bestDistance = d;
					bestKind = GRID;
					bestAt = line;
					bestPart = p;
					bestLine = -1;
				}
			}
		}
		heldKind[axis] = bestKind;
		heldAt[axis] = bestAt;
		heldPart[axis] = bestPart;
		heldLine[axis] = bestLine;
		return bestKind != NONE;
	}

	private static int rank(int targetKind) {
		return switch (targetKind) {
			case ELEMENT -> 3;
			case CENTER -> 2;
			case EDGE -> 1;
			default -> 0;
		};
	}

	/** @return whether a line counts now: another element's only while it did not move, and the gap beside it only when level with it across */
	private boolean usable(int axis, int line, int across, int acrossSize) {
		int box = owner[axis][line];
		if (box < 0) {
			return true;
		}
		int[] from = boxes[box];
		EditableHud element = boxOwners[box];
		if (element != null) {
			int[] now = element.hudBounds();
			if (now[0] != from[0] || now[1] != from[1]) {
				return false;
			}
		}
		if (!gap[axis][line]) {
			return true;
		}
		int boxStart = from[1 - axis];
		int boxSize = from[3 - axis];
		return across < boxStart + boxSize + LEVEL && across + acrossSize > boxStart - LEVEL;
	}

	private static int partOf(int start, int size, int which) {
		return which == START ? start : which == MIDDLE ? start + size / 2 : start + size;
	}

	// ------------------------------------------------------------------------------------------------------- feedback

	/** @return what holds an axis: {@link #NONE}, {@link #CENTER}, {@link #EDGE}, {@link #GRID} or {@link #ELEMENT} */
	int heldKind(int axis) {
		return heldKind[axis];
	}

	/** @return where the target holding an axis lies, in GUI units */
	int heldAt(int axis) {
		return heldAt[axis];
	}

	/** @return whether the element's own center is on the screen's center line of an axis: exactly centered there */
	boolean centered(int axis) {
		return heldKind[axis] == CENTER && heldPart[axis] == MIDDLE;
	}

	/** @return whether the element sits on a grid point: both axes held by the grid */
	boolean onGridPoint() {
		return heldKind[0] == GRID && heldKind[1] == GRID;
	}

	/** @return what an axis is snapped to, in words: "the screen center", "the top edge", "the grid", "Mining profit"; null if nothing */
	String describe(int axis) {
		return switch (heldKind[axis]) {
			case CENTER -> "the screen center";
			case EDGE -> heldPart[axis] == START ? axis == 0 ? "the left edge" : "the top edge" : axis == 0 ? "the right edge" : "the bottom edge";
			case GRID -> "the grid";
			case ELEMENT -> names[owner[axis][heldLine[axis]]];
			default -> null;
		};
	}
}
