package dev.tim1337.lumio.features.visual.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * The fixed places of the tab list's info cards. A card of a known kind has its place from the add-on that reads it (Area, Profile, Pet, ...): the
 * same always. A card without one (a section the add-on does not know) gets the next place after all the known ones the first time it is seen,
 * and keeps it for the stay in that world, however often the list opens and whichever cards come and go meanwhile. Plain data, so it is unit
 * tested.
 */
final class TabCardSlots {
	/** Where the cards without a place of their own start: after every place an add-on gives. */
	static final int UNKNOWN_FROM = 1000;
	private static final int MOST_UNKNOWN = 64;

	private final Map<String, Integer> unknown = new HashMap<>();

	/**
	 * @param title the card's title
	 * @param given the place the add-on gave the card, or -1 for none
	 * @return the card's place
	 */
	int slot(String title, int given) {
		if (given >= 0 && given < UNKNOWN_FROM) {
			return given;
		}
		Integer kept = unknown.get(title);
		if (kept != null) {
			return kept;
		}
		// a strange list cannot count without end: past the most, the last place is shared
		int next = UNKNOWN_FROM + Math.min(unknown.size(), MOST_UNKNOWN - 1);
		if (unknown.size() < MOST_UNKNOWN) {
			unknown.put(title, next);
		}
		return next;
	}

	/** Another world: the cards without a place of their own are counted anew. */
	void reset() {
		unknown.clear();
	}
}
