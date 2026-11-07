package dev.tim1337.lumio.features.misc.keybinds;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.api.Place;

import java.util.List;

/**
 * The places keybinds can be limited to, from the add-on that knows the world (such as the SkyBlock add-on). Without one, keybinds work
 * everywhere only, and the keys of places saved in keybinds are kept as they are, so nothing is lost when the add-on comes back.
 */
public final class KeybindPlaces {
	private KeybindPlaces() {
	}

	/** @return whether an add-on knows where the player is, so keybinds can be limited to its world and places */
	public static boolean available() {
		return Addons.location() != null;
	}

	/** @return the name of the add-on's world ("SkyBlock"), or "" without one */
	public static String worldName() {
		LocationProvider location = Addons.location();
		return location == null ? "" : location.worldName();
	}

	/** @return the places a keybind can be limited to, in order; empty without an add-on */
	public static List<Place> places() {
		LocationProvider location = Addons.location();
		return location == null ? List.of() : location.places();
	}

	/** @return the name of a place by its key, or the key itself for a place no add-on knows */
	public static String nameOf(String key) {
		for (Place place : places()) {
			if (place.key().equals(key)) {
				return place.name();
			}
		}
		return key;
	}

	/** @return whether the player is in the add-on's world now */
	public static boolean inWorld() {
		LocationProvider location = Addons.location();
		return location != null && location.inWorld();
	}

	/** @return the place name the player is at, as the server shows it, or "" */
	public static String placeName() {
		LocationProvider location = Addons.location();
		return location == null || !location.inWorld() ? "" : location.placeName();
	}
}
