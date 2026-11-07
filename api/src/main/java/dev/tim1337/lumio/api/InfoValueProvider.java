package dev.tim1337.lumio.api;

import java.util.List;

/**
 * Adds segments to the info bar and fills them with the values of an add-on's world, and can name the place for the Area segment. Its segments
 * are added to the bar's segment list once, while the game starts.
 */
public interface InfoValueProvider {
	/** @return the segments it adds and fills; asked once, while the game starts */
	List<InfoSegment> segments();

	/** @return the place for the Area segment ("Dwarven Mines"), or null to leave it to Lumio (the server or the world) */
	String area();

	/** Puts the current values of its segments; called a few times per second while the bar is shown, so it keeps its texts and only changes them */
	void read(InfoValues values);
}
