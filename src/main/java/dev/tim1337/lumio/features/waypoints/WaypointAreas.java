package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.api.Place;
import dev.tim1337.lumio.features.misc.keybinds.CustomIslands;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The areas a group shows in. With an add-on that knows the places of its world (SkyBlock), they are its places, and "Everywhere"; without one, this
 * server, this world or everywhere. Where the player is now is worked out once a second, so asking per frame costs nothing.
 */
public final class WaypointAreas {
	private static final long CHECK_MILLIS = 1000L;
	/** The prefix of an alias that holds the server's own id of a place, which other mods' lists name it by. */
	private static final String ID_ALIAS = "id:";

	/** One choice of the "+ Add area" list. */
	public record Area(String key, String name) {
	}

	private static long checkedAt;
	private static String placeKey = "";
	private static boolean inAddonWorld;
	private static boolean multiplayer;

	private WaypointAreas() {
	}

	/** Works out where the player is, at most once a second. */
	static void refresh(Minecraft client, long now) {
		if (now - checkedAt < CHECK_MILLIS) {
			return;
		}
		checkedAt = now;
		LocationProvider location = Addons.location();
		inAddonWorld = location != null && location.inWorld();
		Place place = inAddonWorld ? CustomIslands.builtIn(location.placeName()) : null;
		placeKey = place == null ? "" : place.key();
		multiplayer = client.getSingleplayerServer() == null;
	}

	/** @return whether a group shows where the player is now */
	public static boolean here(WaypointGroup group) {
		for (String area : group.areas()) {
			if (area.equals(WaypointGroup.EVERYWHERE) || area.equals(WaypointGroup.THIS_SERVER) && multiplayer || area.equals(WaypointGroup.THIS_WORLD)
				&& !multiplayer || inAddonWorld && area.equals(placeKey)) {
				return true;
			}
		}
		return false;
	}

	/** @return the areas a new group gets: the place the player is at, or this server or world */
	public static List<String> forNewGroup() {
		List<String> areas = new ArrayList<>();
		if (inAddonWorld && !placeKey.isEmpty()) {
			areas.add(placeKey);
		} else {
			areas.add(multiplayer ? WaypointGroup.THIS_SERVER : WaypointGroup.THIS_WORLD);
		}
		return areas;
	}

	/** @return the areas that can be added, in order: the add-on's places when there is one, then the general ones */
	public static List<Area> choices() {
		List<Area> choices = new ArrayList<>();
		LocationProvider location = Addons.location();
		if (location != null) {
			for (Place place : location.places()) {
				choices.add(new Area(place.key(), place.name()));
			}
		}
		choices.add(new Area(WaypointGroup.THIS_SERVER, nameOf(WaypointGroup.THIS_SERVER)));
		choices.add(new Area(WaypointGroup.THIS_WORLD, nameOf(WaypointGroup.THIS_WORLD)));
		choices.add(new Area(WaypointGroup.EVERYWHERE, nameOf(WaypointGroup.EVERYWHERE)));
		return choices;
	}

	/** @return the name of an area, as its chip shows it */
	public static String nameOf(String key) {
		return switch (key) {
			case WaypointGroup.EVERYWHERE -> "Everywhere";
			case WaypointGroup.THIS_SERVER -> "This server";
			case WaypointGroup.THIS_WORLD -> "This world";
			default -> {
				LocationProvider location = Addons.location();
				if (location != null) {
					for (Place place : location.places()) {
						if (place.key().equals(key)) {
							yield place.name();
						}
					}
				}
				yield key;
			}
		};
	}

	/** @return the areas of a group as one short text: "Glacite Mineshafts", "Hub, Gold Mine", or "nowhere" */
	public static String summary(WaypointGroup group) {
		if (group.areas().isEmpty()) {
			return "nowhere";
		}
		String first = nameOf(group.areas().getFirst());
		return group.areas().size() == 1 ? first : first + " +" + (group.areas().size() - 1);
	}

	/** @return the key of the place another mod's list names by its island ("crystal_hollows", "mining_3"), or null */
	static String placeOfIsland(String island) {
		LocationProvider location = Addons.location();
		if (location == null || island.isEmpty()) {
			return null;
		}
		for (Place place : location.places()) {
			if (place.key().equals(island) || place.aliases().contains(ID_ALIAS + island)) {
				return place.key();
			}
		}
		return null;
	}

	/** @return the island id another mod's list names a place by: the server's id from its aliases, or its key; null for the general areas */
	static String islandOf(String area) {
		LocationProvider location = Addons.location();
		if (location == null) {
			return null;
		}
		for (Place place : location.places()) {
			if (place.key().equals(area)) {
				for (String alias : place.aliases()) {
					if (alias.startsWith(ID_ALIAS)) {
						return alias.substring(ID_ALIAS.length());
					}
				}
				return place.key();
			}
		}
		return null;
	}

	/** @return the place a block is at by its coordinates alone, from the add-on, or "" */
	static String placeAt(int x, int y, int z) {
		LocationProvider location = Addons.location();
		if (location == null) {
			return "";
		}
		try {
			String place = location.placeAt(x, y, z);
			return place == null ? "" : place;
		} catch (RuntimeException e) {
			return "";
		}
	}

	/** @return whether a group is kept with those of every server and world: it shows everywhere and no add-on world is in play */
	static boolean everywhere(WaypointGroup group) {
		return group.areas().contains(WaypointGroup.EVERYWHERE) && Addons.location() == null;
	}
}
