package dev.tim1337.lumio.api.experimental.waypoint;

/**
 * Temporary waypoints an add-on keeps up itself, such as the Hotspot Radar's guess: drawn exactly as the temporary waypoints from chat (the same
 * beam, color and label with the distance) and listed with them on the Waypoints page, where Save copies one into a group; never stored on
 * their own. The add-on moves and removes its own (by a key of its choosing); Lumio only lets them go on a world change. Lumio's own time limit, the most temporary waypoints and "remove when reached" are for the
 * chat's waypoints and do not apply. The main mod sets what shows them; without it nothing happens.
 */
public final class AddonWaypoints {
	/** Shows the waypoints; set by the main mod. */
	public interface Shower {
		void show(String key, String name, int x, int y, int z);

		/** Shows one in a color of its own, with or without its label; without this, in the temporary waypoints' color with its label. */
		default void show(String key, String name, int x, int y, int z, int rgb, boolean label) {
			show(key, name, x, y, z);
		}

		void remove(String key);
	}

	private static Shower shower;

	private AddonWaypoints() {
	}

	/** Sets what shows them; called by the main mod when it starts. */
	public static void shower(Shower value) {
		shower = value;
	}

	/**
	 * Shows an add-on's waypoint, or moves and renames the one it shows under the same key. Called on the game's thread.
	 *
	 * @param key  the add-on's own key for it, such as "lumio-skyblock:hotspot"
	 * @param name the label, such as "Hotspot"
	 */
	public static void show(String key, String name, int x, int y, int z) {
		if (shower != null) {
			shower.show(key, name, x, y, z);
		}
	}

	/**
	 * As {@link #show(String, String, int, int, int)}, in a color of the add-on's choosing and with or without its label.
	 *
	 * @param rgb   the color (0xRRGGBB), or 0 for the temporary waypoints' own
	 * @param label whether the label (the name and the distance) shows
	 */
	public static void show(String key, String name, int x, int y, int z, int rgb, boolean label) {
		if (shower != null) {
			shower.show(key, name, x, y, z, rgb, label);
		}
	}

	/** Takes an add-on's waypoint away; nothing happens when it does not show. Called on the game's thread. */
	public static void remove(String key) {
		if (shower != null) {
			shower.remove(key);
		}
	}
}
