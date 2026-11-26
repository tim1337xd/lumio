package dev.tim1337.lumio.features.visual.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps a size of the tab list calm while players join and leave: the room it reserves moves in whole steps, not with every single change. It
 * grows at once when what is there does not fit; it shrinks only after the amount has stayed below the next smaller step for a while, and then
 * one step at a time. The smallest room is the smallest step that fits, so a small lobby does not reserve a large list. Per kind of place (an
 * island, a lobby type) the largest room used in this session is remembered, and the list starts from it when it opens there again, so it does
 * not jump on opening. Used for the players (steps of rows) and for the height of the info side. Plain numbers, so it is unit tested.
 */
final class TabSteady {
	/** The shrink delay that never shrinks while the list is open. */
	static final long NEVER = -1L;
	private static final int MOST_PLACES = 64;
	/** Within this many steps below the remembered size, the list opens at that size; further below, at the size that fits. */
	static final int NEAR_STEPS = 2;

	private final int step;
	/** How many a full column holds, or 0: the room never grows past whole full columns, so players that fit one column are not spread over two. */
	private final int column;
	private final Map<String, Integer> largest = new HashMap<>();
	private String place = "";
	private int reserved;
	private long belowSince = NEVER;

	/** @param step the size of a step: a number of players, or of pixels */
	TabSteady(int step) {
		this(step, 0);
	}

	/** @param column how many a full column holds: the room is at most the whole columns the amount needs */
	TabSteady(int step, int column) {
		this.step = Math.max(1, step);
		this.column = Math.max(0, column);
	}

	/** @return the smallest whole number of steps that holds an amount, one step at least */
	int stepped(int amount) {
		int steps = Math.max(1, (Math.max(0, amount) + step - 1) / step) * step;
		if (column <= 0) {
			return steps;
		}
		return Math.min(steps, Math.max(1, (Math.max(0, amount) + column - 1) / column) * column);
	}

	/**
	 * The list opens at a place: it starts from the largest room used there in this session when what is there now is near it (so it
	 * does not jump on opening), else from the smallest step that fits (so a nearly empty place does not look empty).
	 *
	 * @param key    the kind of place (an island, a lobby type)
	 * @param amount what is there now
	 * @return the room reserved
	 */
	int open(String key, int amount) {
		place = key == null ? "" : key;
		int needed = stepped(amount);
		int remembered = largest.getOrDefault(place, 0);
		// the size it had there, unless far fewer are here now: a nearly empty place does not open with the room of a full one
		reserved = remembered > needed && remembered - needed <= NEAR_STEPS * step ? remembered : needed;
		belowSince = NEVER;
		remember();
		return reserved;
	}

	/**
	 * What is there changed, or time passed.
	 *
	 * @param amount      what is there now
	 * @param nowMillis   the time
	 * @param shrinkAfter how long the amount has to stay below the next smaller step before the room shrinks by one step; {@link #NEVER} for never
	 * @return the room reserved
	 */
	int update(int amount, long nowMillis, long shrinkAfter) {
		int needed = stepped(amount);
		if (needed > reserved) {
			// it does not fit: grown at once, to the step that holds it
			reserved = needed;
			belowSince = NEVER;
			remember();
		} else if (needed < reserved) {
			if (belowSince == NEVER) {
				belowSince = nowMillis;
			} else if (shrinkAfter != NEVER && nowMillis - belowSince >= shrinkAfter) {
				// one step at a time, and the wait starts again
				reserved -= step;
				belowSince = nowMillis;
				largest.put(place, reserved);
			}
		} else {
			belowSince = NEVER;
		}
		return reserved;
	}

	/** @return the room reserved now */
	int reserved() {
		return reserved;
	}

	private void remember() {
		if (largest.size() < MOST_PLACES || largest.containsKey(place)) {
			largest.merge(place, reserved, Math::max);
		}
	}
}
