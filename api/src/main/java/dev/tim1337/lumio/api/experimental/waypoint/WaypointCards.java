package dev.tim1337.lumio.api.experimental.waypoint;

import java.util.function.Consumer;

/**
 * The place card of the waypoints (a name, a color, where it is) for an add-on's own points, such as points kept relative to a dungeon room: it
 * looks and works as for the player's own waypoints, and gives the result back instead of keeping it in a group. The main mod sets what opens it.
 */
public final class WaypointCards {
	/** What the card gave back: the name typed and the color chosen, as 0xRRGGBB. */
	public record Placed(String name, int rgb) {
	}

	/** Opens the card; set by the main mod. */
	public interface Opener {
		void open(int x, int y, int z, String title, String name, Consumer<Placed> done);
	}

	/** Takes the place key's tap for a block before the player's own waypoints do, such as a point of the dungeon room the player is in. */
	public interface Claim {
		/** @return whether the add-on took the tap (it then opens a card of its own); asked on the game's thread */
		boolean place(int x, int y, int z);
	}

	private static Opener opener;
	private static final java.util.List<Claim> CLAIMS = new java.util.ArrayList<>();

	private WaypointCards() {
	}

	/** Adds a claim on the place key; done once while an add-on starts. */
	public static void claim(Claim claim) {
		CLAIMS.add(claim);
	}

	/** @return whether an add-on took the place key's tap for this block; asked by the main mod */
	public static boolean claimed(int x, int y, int z) {
		for (Claim claim : CLAIMS) {
			if (claim.place(x, y, z)) {
				return true;
			}
		}
		return false;
	}

	/** Sets what opens the card; called by the main mod when it starts. */
	public static void opener(Opener value) {
		opener = value;
	}

	/**
	 * Opens the place card for a block; nothing happens without the main mod's waypoints.
	 *
	 * @param title the card's heading, such as "NEW ROOM WAYPOINT"
	 * @param name  the name filled in
	 * @param done  called with the result when the player keeps it; not called when they go back
	 */
	public static void open(int x, int y, int z, String title, String name, Consumer<Placed> done) {
		if (opener != null) {
			opener.open(x, y, z, title, name, done);
		}
	}

	/** @return the key that places or edits a point at the block looked at, for an add-on to list on its own page; null before it is registered */
	public static net.minecraft.client.KeyMapping placeKey() {
		return dev.tim1337.lumio.api.spi.Backend.get().waypointPlaceKey();
	}
}
