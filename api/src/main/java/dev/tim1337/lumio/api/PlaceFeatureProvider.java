package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.feature.AddonPage;

import java.util.List;

/**
 * The features that fit where the player is in an add-on's world, for the Home page's "You are in" card: grouped by what the place is good for,
 * with what the player is doing right now first. Asked about once per second.
 */
public interface PlaceFeatureProvider {
	/** @return whether the player is in the add-on's world with its features on, so the card shows the place */
	boolean active();

	/** @return the name of the place ("Dwarven Mines"), or "" while it is not known */
	String placeName();

	/** @return the add-on's main page, for the card's heading */
	AddonPage page();

	/** @return a text that changes whenever the groups would change (the place, what the player is doing) */
	String key();

	/** @return the groups for the place now, in the order to show them */
	List<PlaceGroup> groups();
}
