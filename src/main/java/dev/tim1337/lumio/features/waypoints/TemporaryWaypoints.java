package dev.tim1337.lumio.features.waypoints;

import java.util.ArrayList;
import java.util.List;

/**
 * The temporary waypoints made from coordinates in chat: never in a group of the player's or a saved list, each labeled with who sent it, gone
 * after a few minutes (fading at the end), when reached if wanted, or on a world change; a few at most, the oldest going first. Saving one moves
 * it into a group. They are drawn through a group of their own that is never stored, listed or saved. An add-on's own ones
 * ({@link dev.tim1337.lumio.api.experimental.waypoint.AddonWaypoints}) look the same, but the add-on moves and removes them: no time limit, never
 * pushed out by the chat's, never removed when reached; only a world change lets them go here too.
 */
final class TemporaryWaypoints {
	/** A temporary waypoint: its point (named "Corpse · from Kiwi"), who sent it, and when; owner is the add-on's key of its own ones, else null. */
	record Temp(Waypoint point, String name, String sender, long createdAt, String owner) {
		boolean owned() {
			return owner != null;
		}
	}

	/** Their color: unlike the groups' first colors, so they read as passing. */
	static final int RGB = 0xFF9F0A;
	/** How near counts as reached, in blocks, and the last share of their time in which they show softer before they go. */
	static final double REACHED_BLOCKS = 3.0;
	static final float FADE_SHARE = 0.15f;
	private static final String GROUP_ID = "temporary";

	private final List<Temp> temps = new ArrayList<>();
	/** The group they are drawn through: never in the store. */
	private final WaypointGroup group = new WaypointGroup(GROUP_ID, "Temporary", RGB);

	TemporaryWaypoints() {
		group.show(WaypointGroup.Show.ALL);
	}

	/** Adds one, the oldest going first when there are already as many as allowed. @return the new one */
	Temp add(int x, int y, int z, String name, String sender, long now, int most) {
		// the oldest of the chat's go first; an add-on's own never count
		while (fromChat() >= Math.max(1, most)) {
			removeAt(oldestFromChat());
		}
		String label = sender == null || sender.isEmpty() ? name : name + " · from " + sender;
		Temp temp = new Temp(new Waypoint(x, y, z, label), name, sender == null ? "" : sender, now, null);
		temps.add(temp);
		group.add(temp.point());
		return temp;
	}

	/**
	 * Shows an add-on's own waypoint, or moves, renames or recolors the one it has under this key.
	 *
	 * @param rgb   its color, or 0 for the temporary waypoints' own
	 * @param label whether its label shows
	 */
	void showOwned(String key, String name, int x, int y, int z, int rgb, boolean label, long now) {
		int index = indexOwned(key);
		if (index >= 0) {
			Waypoint point = temps.get(index).point();
			if (point.x() == x && point.y() == y && point.z() == z && point.name().equals(name) && point.rgb() == (rgb & 0xFFFFFF)
				&& point.noLabel() == !label) {
				return;
			}
			removeAt(index);
		}
		Waypoint point = new Waypoint(x, y, z, name);
		point.rgb(rgb);
		point.noLabel(!label);
		Temp temp = new Temp(point, name, "", now, key);
		temps.add(temp);
		group.add(temp.point());
	}

	/** Takes an add-on's own waypoint away, if it shows. */
	void removeOwned(String key) {
		int index = indexOwned(key);
		if (index >= 0) {
			removeAt(index);
		}
	}

	private int indexOwned(String key) {
		for (int i = 0; i < temps.size(); i++) {
			if (key.equals(temps.get(i).owner())) {
				return i;
			}
		}
		return -1;
	}

	private int fromChat() {
		int count = 0;
		for (Temp temp : temps) {
			count += temp.owned() ? 0 : 1;
		}
		return count;
	}

	private int oldestFromChat() {
		for (int i = 0; i < temps.size(); i++) {
			if (!temps.get(i).owned()) {
				return i;
			}
		}
		return -1;
	}

	/** Lets go of those whose time is over, and (if wanted) those the player reached. @return whether any went */
	boolean tick(long now, long lastsMillis, boolean removeReached, double px, double py, double pz) {
		boolean changed = false;
		for (int i = temps.size() - 1; i >= 0; i--) {
			Temp temp = temps.get(i);
			if (temp.owned()) {
				continue;
			}
			boolean expired = now - temp.createdAt() >= lastsMillis;
			boolean reached = removeReached && temp.point().distanceSq(px, py, pz) <= REACHED_BLOCKS * REACHED_BLOCKS;
			if (expired || reached) {
				removeAt(i);
				changed = true;
			}
		}
		return changed;
	}

	/** @return whether a temporary waypoint is in the last part of its time, shown softer */
	static boolean fading(Temp temp, long now, long lastsMillis) {
		return !temp.owned() && now - temp.createdAt() >= lastsMillis * (1f - FADE_SHARE);
	}

	/** Takes one out, to be saved into a group. @return it, or null when it is gone already */
	Temp take(Temp temp) {
		int index = temps.indexOf(temp);
		if (index < 0) {
			return null;
		}
		removeAt(index);
		return temp;
	}

	private void removeAt(int index) {
		temps.remove(index);
		group.remove(index);
	}

	List<Temp> list() {
		return temps;
	}

	/** @return the group they are drawn through (never stored or listed) */
	WaypointGroup group() {
		return group;
	}

	/** A world change: all of them go. */
	void clear() {
		while (!temps.isEmpty()) {
			removeAt(temps.size() - 1);
		}
	}
}
