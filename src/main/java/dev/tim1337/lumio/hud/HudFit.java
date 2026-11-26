package dev.tim1337.lumio.hud;

/**
 * The auto-fit of the HUD, as plain numbers so it is unit tested: how far an element shrinks to fit its share of the screen, and where the movable
 * panels go so that none covers one of higher priority. Nothing here is saved: the player's own places and sizes stay as they are, and the fit
 * follows the screen, so it goes back by itself once there is room again.
 */
public final class HudFit {
	/** The share of the screen's width the info bar may take at most. */
	public static final float INFO_BAR_WIDTH_SHARE = 0.9f;
	/** The share of the screen's width and height any other panel may take at most. */
	public static final float PANEL_WIDTH_SHARE = 0.9f;
	public static final float PANEL_HEIGHT_SHARE = 0.45f;
	/** The room kept from the screen's edges and between moved panels, in GUI units. */
	public static final float MARGIN = 2f;
	/** The least an element shrinks to, as a share of its own size. */
	public static final float LEAST = 0.3f;
	/** A fit factor at or above this keeps the element's own size: rounding, not a shrink worth telling. */
	public static final float KEPT = 0.995f;
	/** Rounding room for a box that ends exactly at the margin. */
	private static final float EPSILON = 0.001f;

	/** The axes a movable panel may move along: along the edge it is anchored at. */
	public static final int ALONG_Y = 1;
	public static final int ALONG_X = 2;
	/** A corner: first along the side edge (up or down), then along the top or bottom edge. */
	public static final int ALONG_BOTH = ALONG_Y | ALONG_X;
	/** Kinds of boxes that may overlap: a box that is drawn {@link #OVER} the others never moves off one {@link #UNDER} it, nor that one off it. */
	public static final int OVER = 1;
	public static final int UNDER = 2;

	private HudFit() {
	}

	/**
	 * @return the factor (at most 1) an element of a size, at its own scale already, shrinks by so it takes at most the given shares of the screen;
	 *         never grows it
	 */
	public static float shrink(float width, float height, float screenWidth, float screenHeight, float widthShare, float heightShare) {
		float room = Math.min(widthShare * screenWidth / Math.max(1f, width), heightShare * screenHeight / Math.max(1f, height));
		return Math.min(1f, Math.max(LEAST, room));
	}

	/** @return the axis a panel moves along, from its anchors (start, middle or end across and down) */
	public static int along(int anchorAcross, int anchorDown) {
		boolean side = anchorAcross != HudAnchors.MIDDLE;
		boolean topOrBottom = anchorDown != HudAnchors.MIDDLE;
		if (side && topOrBottom) {
			return ALONG_BOTH;
		}
		return side || !topOrBottom ? ALONG_Y : ALONG_X;
	}

	/**
	 * Lays the boxes out: every fixed box stays where it is; then, in the given order (the higher priority first), each movable box is moved onto the
	 * screen and off every box placed before it, by the least distance along its axis. A box that finds no free place on the screen stays where it
	 * fits best on the screen.
	 *
	 * @param boxes   left, top, width and height of every box, four values each, at the places the player set
	 * @param movable along which axis each box may move ({@link #ALONG_Y}, {@link #ALONG_X}, {@link #ALONG_BOTH}), or 0 for a fixed box
	 * @param count   how many boxes there are
	 * @param shifts  receives how far each box moves, across and down, two values each
	 */
	public static void resolve(float[] boxes, int[] movable, int count, float screenWidth, float screenHeight, float[] shifts) {
		resolve(boxes, movable, null, count, screenWidth, screenHeight, shifts);
	}

	/**
	 * As {@link #resolve(float[], int[], int, float, float, float[])}, where some boxes may lie over others.
	 *
	 * @param kinds {@link #OVER}, {@link #UNDER} or 0 for each box, or null when none may overlap
	 */
	public static void resolve(float[] boxes, int[] movable, int[] kinds, int count, float screenWidth, float screenHeight, float[] shifts) {
		java.util.Arrays.fill(shifts, 0, count * 2, 0f);
		for (int i = 0; i < count; i++) {
			if (movable[i] == 0) {
				continue;
			}
			float x = boxes[i * 4];
			float y = boxes[i * 4 + 1];
			float w = boxes[i * 4 + 2];
			float h = boxes[i * 4 + 3];
			float onX = onScreen(x, w, screenWidth);
			float onY = onScreen(y, h, screenHeight);
			float bestX = onX;
			float bestY = onY;
			if (blocked(boxes, movable, kinds, count, i, onX, onY, w, h, shifts)) {
				float found = Float.NaN;
				if ((movable[i] & ALONG_Y) != 0) {
					found = freeAlong(boxes, movable, kinds, count, i, onX, onY, w, h, screenHeight, false, shifts);
					if (!Float.isNaN(found)) {
						bestY = found;
					}
				}
				if (Float.isNaN(found) && (movable[i] & ALONG_X) != 0) {
					found = freeAlong(boxes, movable, kinds, count, i, onX, onY, w, h, screenWidth, true, shifts);
					if (!Float.isNaN(found)) {
						bestX = found;
					}
				}
			}
			shifts[i * 2] = bestX - x;
			shifts[i * 2 + 1] = bestY - y;
		}
	}

	/** @return the start that keeps a box of a size on the screen, as near as it can to where it is */
	static float onScreen(float start, float size, float screen) {
		if (size + 2 * MARGIN >= screen) {
			return (screen - size) / 2f;
		}
		return Math.max(MARGIN, Math.min(screen - MARGIN - size, start));
	}

	/** @return whether two boxes may overlap: one is drawn over the other */
	private static boolean passes(int[] kinds, int a, int b) {
		return kinds != null && (kinds[a] | kinds[b]) == (OVER | UNDER) && kinds[a] != kinds[b];
	}

	/** @return whether a box at a place overlaps one placed before it: the fixed ones, and the movable ones of higher priority where they went */
	private static boolean blocked(float[] boxes, int[] movable, int[] kinds, int count, int self, float x, float y, float w, float h, float[] shifts) {
		for (int j = 0; j < count; j++) {
			if (j == self || movable[j] != 0 && j > self || passes(kinds, self, j)) {
				continue;
			}
			float ox = boxes[j * 4] + shifts[j * 2];
			float oy = boxes[j * 4 + 1] + shifts[j * 2 + 1];
			if (boxes[j * 4 + 2] > 0f && boxes[j * 4 + 3] > 0f && x < ox + boxes[j * 4 + 2] && ox < x + w && y < oy + boxes[j * 4 + 3] && oy < y + h) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the free start along one axis nearest to where the box is (beside one of the boxes before it, with the margin), or NaN when there
	 *         is none on the screen
	 */
	private static float freeAlong(float[] boxes, int[] movable, int[] kinds, int count, int self, float x, float y, float w, float h, float screen,
		boolean across, float[] shifts) {
		float at = across ? x : y;
		float size = across ? w : h;
		float best = Float.NaN;
		float bestDistance = Float.MAX_VALUE;
		for (int j = 0; j < count; j++) {
			if (j == self || movable[j] != 0 && j > self || passes(kinds, self, j)) {
				continue;
			}
			float start = (across ? boxes[j * 4] + shifts[j * 2] : boxes[j * 4 + 1] + shifts[j * 2 + 1]);
			float length = across ? boxes[j * 4 + 2] : boxes[j * 4 + 3];
			// before the box and after it
			for (int side = 0; side < 2; side++) {
				float candidate = side == 0 ? start - size - MARGIN : start + length + MARGIN;
				if (candidate < MARGIN - EPSILON || candidate + size > screen - MARGIN + EPSILON) {
					continue;
				}
				float distance = Math.abs(candidate - at);
				if (distance < bestDistance && !blocked(boxes, movable, kinds, count, self, across ? candidate : x, across ? y : candidate, w, h, shifts)) {
					best = candidate;
					bestDistance = distance;
				}
			}
		}
		return best;
	}
}
