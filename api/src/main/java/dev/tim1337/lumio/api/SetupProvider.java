package dev.tim1337.lumio.api;

import java.util.List;

/**
 * An add-on's part of the first setup: its world as an answer to "Where do you play?", its general features, and the things the player can
 * do there. Without one, the setup does not ask where the player plays.
 */
public interface SetupProvider {
	/** @return the world, with its general features (SkyBlock's panels) */
	SetupChoice world();

	/** @return the world's name on the "Where do you play?" card ("Hypixel SkyBlock") */
	String worldTitle();

	/** @return what the player can pick under "What you do there", in the order of the grid */
	List<SetupChoice> activities();

	/** @return the welcome card's line about the add-on ("Trackers and helpers for what you do.") */
	String welcomeLine();

	/** @return whether its features are switched on as a whole (its master switch) */
	boolean worldOn();

	/** Switches its features on or off as a whole. */
	void setWorldOn(boolean on);
}
