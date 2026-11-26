package dev.tim1337.lumio.api.experimental.filter;

import java.util.Arrays;

/**
 * Entities an add-on asks not to be drawn right now, by id: a short list it sets a few times a second (the fake Livids of F5, say). Only what is
 * drawn changes: the entities stay in the world and nothing is sent. Unlike {@link VisualFilters} it follows the add-on's state at once and may
 * name players; the add-on keeps it short and clears it when it no longer applies.
 */
public final class HiddenEntities {
	private static final int[] NONE = new int[0];
	private static volatile int[] ids = NONE;

	private HiddenEntities() {
	}

	/** Sets the entities not drawn, replacing the last list. */
	public static void set(int[] entityIds) {
		int[] sorted = entityIds.clone();
		Arrays.sort(sorted);
		ids = sorted;
	}

	/** Draws every entity again. */
	public static void clear() {
		ids = NONE;
	}

	/** @return whether an entity is not to be drawn now; asked by Lumio for every entity in every frame, so it is a cheap lookup */
	public static boolean hidden(int entityId) {
		int[] now = ids;
		return now.length > 0 && Arrays.binarySearch(now, entityId) >= 0;
	}
}
