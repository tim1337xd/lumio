package dev.tim1337.lumio.ui.tooltip;

/**
 * How far a long tooltip is scrolled, for one hovered item at a time: within its limits, reset when the item changes. Plain numbers, so it is unit tested.
 */
public final class TooltipScroll {
	/** How far one notch of the wheel goes, in tooltip pixels, and how much further with Shift. */
	static final int STEP = 12;
	static final int SHIFT_FACTOR = 4;

	private int offset;
	private int most;
	private Object item;

	/** Tells it what is shown: which item, and how much of it does not fit. A new item starts at the top. */
	public void show(Object shownItem, int contentHeight, int visibleHeight) {
		if (shownItem != item) {
			item = shownItem;
			offset = 0;
		}
		most = Math.max(0, contentHeight - visibleHeight);
		offset = Math.clamp(offset, 0, most);
	}

	/** Forgets the item, so that the next one starts at the top. */
	public void clear() {
		item = null;
		offset = 0;
		most = 0;
	}

	/** Moves by notches of the wheel; up is positive, as the game has them. */
	public void wheel(double notches, boolean fast) {
		long moved = Math.round(notches * STEP * (fast ? SHIFT_FACTOR : 1));
		offset = (int) Math.clamp(offset - moved, 0L, most);
	}

	public int offset() {
		return offset;
	}

	/** @return whether there is anything to scroll */
	public boolean canScroll() {
		return most > 0;
	}

	/** @return whether more lies below what is shown */
	public boolean moreBelow() {
		return offset < most;
	}

	/** @return how far it is scrolled, from 0 to 1 */
	public float share() {
		return most == 0 ? 0f : (float) offset / most;
	}
}
