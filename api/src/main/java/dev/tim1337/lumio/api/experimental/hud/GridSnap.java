package dev.tim1337.lumio.api.experimental.hud;

/**
 * Snapping with the HUD editor's grid and logic, for something an add-on lets the player move or pull on a screen: a box snaps to the lines of
 * the editor's grid (its spacing as the player set it there) and to the screen's center lines, never to the screen's edges or to other things.
 * A line holds the box until it is pulled twice as far away as it snapped in, so it does not flicker. All in GUI units.
 * <p>
 * Get one with {@link HudEditor#gridSnap()}, call {@link #begin} when a drag starts, then {@link #move} or {@link #edge} while it goes on.
 */
public interface GridSnap {
	/** The axis across (x, left to right). */
	int ACROSS = 0;
	/** The axis down (y, top to bottom). */
	int DOWN = 1;

	/** Starts a drag on a screen of a size: nothing holds the box yet. */
	void begin(int screenWidth, int screenHeight);

	/**
	 * Snaps a moved box: its start, center or end on each axis, whichever is nearest to a line. The result is in {@link #x()} and {@link #y()},
	 * kept on the screen.
	 *
	 * @param free whether the player places it freely (Shift held): nothing snaps
	 * @return whether a line holds the box on either axis
	 */
	boolean move(int x, int y, int width, int height, boolean free);

	/** @return the snapped left of the box of the last {@link #move} */
	int x();

	/** @return the snapped top of the box of the last {@link #move} */
	int y();

	/**
	 * Snaps one edge that is pulled along an axis.
	 *
	 * @param axis {@link #ACROSS} for a left or right edge, {@link #DOWN} for a top or bottom edge
	 * @return where the edge goes: on a line within reach, or where it is
	 */
	int edge(int axis, int edge, boolean free);

	/** @return whether a grid line holds the box on an axis */
	boolean onGrid(int axis);

	/** @return whether the screen's center line holds the box on an axis */
	boolean onCenter(int axis);

	/** @return where the line that holds an axis lies; only of meaning while {@link #onGrid} or {@link #onCenter} */
	int heldAt(int axis);

	/** @return the grid's spacing */
	int step();
}
