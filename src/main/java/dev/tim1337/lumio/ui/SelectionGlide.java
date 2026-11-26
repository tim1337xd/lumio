package dev.tim1337.lumio.ui;

/**
 * The selection box of a list (the sidebar's highlight): it lives in the list's content coordinates, so it moves with its row exactly while the
 * list scrolls, pixel for pixel, with no delay. Only a change of the selection animates: the box glides from where it was to the new row in
 * {@link #SECONDS}, easing out, the same at any frame rate; the row may move meanwhile (a group opening above it), and the box ends on it. At rest
 * it is the row itself. Plain logic, tested without the game.
 */
public final class SelectionGlide {
	/** How long the box takes from the old row to the new one. */
	public static final float SECONDS = 0.15f;

	private int selected = -1;
	private float progress = 1f;
	private float fromY;
	private float fromHeight;
	private float fromIndent;
	private float y;
	private float height;
	private float indent;

	/**
	 * Follows the selected row for one frame.
	 *
	 * @param index    the selected row
	 * @param rowY     its top in the list's content coordinates (not moved by the scroll)
	 * @param rowHeight its height, and ({@code rowIndent}) how far it is indented
	 * @param seconds  the time of this frame
	 * @param animated whether a change glides; without, the box is on the new row at once
	 */
	public void follow(int index, float rowY, float rowHeight, float rowIndent, float seconds, boolean animated) {
		if (index != selected) {
			if (selected >= 0 && animated) {
				fromY = y;
				fromHeight = height;
				fromIndent = indent;
				progress = 0f;
			} else {
				progress = 1f;
			}
			selected = index;
		} else {
			progress = Math.min(1f, progress + Math.max(0f, seconds) / SECONDS);
		}
		if (progress >= 1f) {
			y = rowY;
			height = rowHeight;
			indent = rowIndent;
			return;
		}
		float rest = 1f - progress;
		float eased = 1f - rest * rest * rest;
		y = fromY + (rowY - fromY) * eased;
		height = fromHeight + (rowHeight - fromHeight) * eased;
		indent = fromIndent + (rowIndent - fromIndent) * eased;
	}

	/** @return whether a change of the selection is gliding now */
	public boolean gliding() {
		return progress < 1f;
	}

	/** @return the box's top in content coordinates */
	public float y() {
		return y;
	}

	public float height() {
		return height;
	}

	public float indent() {
		return indent;
	}
}
