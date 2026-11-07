package dev.tim1337.lumio.features.visual.world;

/**
 * Tells apart why the item in the first person hand would stand lower than at rest, so that only a real change of item keeps its drop. The game lowers the item when another one comes into the hand, also when the server only rewrote the
 * data of the same item, as servers do while a tool is used. And it lowers it after a hit, rising as the attack cooldown refills, which is the "Cooldown dip". Only how high the item is drawn. Plain state, so it is unit tested.
 */
public final class ItemHeight {
	/** The height at rest. */
	public static final float REST = 1f;
	/** The game's motion goes on. */
	public static final float GAME = -1f;
	private static final float FULL = 1f;
	private static final int NO_SLOT = -1;

	private int slot = NO_SLOT;
	private boolean changing;

	/**
	 * Before the game's tick.
	 *
	 * @param selectedSlot the hotbar slot in use
	 * @param shownIsHeld  whether the item drawn is the one in the hand
	 * @param sameKind     whether both are the same kind of item
	 * @return whether the one in the hand is only a new state of the one drawn, and takes its place without a drop
	 */
	public boolean changedInPlace(int selectedSlot, boolean shownIsHeld, boolean sameKind) {
		if (slot != NO_SLOT && selectedSlot != slot && !shownIsHeld) {
			changing = true;
		}
		slot = selectedSlot;
		return !shownIsHeld && sameKind && !changing;
	}

	/**
	 * After the game's tick.
	 *
	 * @param shownIsHeld whether the item drawn is the one in the hand
	 * @param swapScale   the game's timer for the item's height, from 0 to 1
	 * @param handsBusy   whether the game keeps the hands down
	 * @param keepsDip    whether the dip after a hit stays
	 * @return the height for the look, or {@link #GAME}
	 */
	public float after(boolean shownIsHeld, float swapScale, boolean handsBusy, boolean keepsDip) {
		if (!shownIsHeld) {
			changing = true;
		} else if (swapScale >= FULL) {
			changing = false;
		}
		return changing || handsBusy || keepsDip ? GAME : REST;
	}

	/** @return whether another item is coming into the hand: its drop and rise stay the game's */
	public boolean changing() {
		return changing;
	}
}
