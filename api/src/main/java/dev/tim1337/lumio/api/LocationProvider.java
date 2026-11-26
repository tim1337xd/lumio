package dev.tim1337.lumio.api;

import java.util.List;

/**
 * Where the player is, in a world an add-on knows (such as SkyBlock): used by the keybinds' "Where it works", the Home page's "You are in" card
 * and everything else that shows the current place. Read often, so the answers come from values the add-on keeps up to date, not from a scan.
 */
public interface LocationProvider {
	/** @return the name of the world it knows, for "Where it works" ("SkyBlock") */
	String worldName();

	/** @return whether the player is in that world now */
	boolean inWorld();

	/** @return the name of the place the player is at, as the server shows it ("Dwarven Mines"), or "" */
	String placeName();

	/** @return the name of the smaller area within the place, as the server's scoreboard shows it ("Your Island"), or "" */
	default String zoneName() {
		return "";
	}

	/**
	 * @return the key of the place a block of that world is at, by its coordinates alone, or "" when they do not tell (most places share their
	 *         coordinates). Waypoints use it to guess where an imported list belongs. Since 1.12.0.
	 */
	default String placeAt(int x, int y, int z) {
		return "";
	}

	/**
	 * @return an id of the player's current profile in that world (a SkyBlock profile), or "" while it is not known; things the player keeps per profile
	 *         (such as waypoints) are kept by it. Since 1.11.0.
	 */
	default String profileId() {
		return "";
	}

	/** @return whether a text is the name of an area of that world, for telling a location line by its name */
	default boolean knownArea(String name) {
		return false;
	}

	/** @return the places a keybind can be limited to, in the order to list them */
	List<Place> places();

	/**
	 * @return whether the current place has a property: "mines" (under ground), "garden", "slayer" (a slayer quest is on). For the ambience's Auto, also what fits the place now:
	 * "ambience:snow", "ambience:fireflies" (at night), "ambience:mist", "ambience:no_leaves", "ambience:autumn", and "mood:ash", "mood:sparks", "mood:dust" and "mood:webs".
	 * It is asked about once a second. (The "sky:" properties of the Sky page's Island skies, 1.9.0 to 1.29.0, are no longer asked: Island skies
	 * were removed in 1.30.0.)
	 */
	boolean has(String property);
}
