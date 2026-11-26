package dev.tim1337.lumio.features.waypoints;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells when the player joined a new lobby, for the routes that restart at 1 there. A lobby is known by its server's id, which Hypixel shows on
 * the scoreboard after the date ("11/15/26 m12AB"): another id is another lobby, the same id again (a rejoin) is not. Where no id can be read,
 * any change of the world counts. The id comes a moment after the world, so a world change waits {@link #WAIT_MS} for it. Plain logic, tested
 * without the game.
 */
final class LobbyWatch {
	/** After a world change the server's id is waited for this long; without one by then, the world change is a new lobby. */
	static final long WAIT_MS = 4_000L;
	/** A date and the server's id after it, as the scoreboard's top line shows them. */
	private static final Pattern SERVER_LINE = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+([A-Za-z]{0,6}\\d{1,4}[A-Za-z]{0,4})\\b");

	private String lastId;
	private Object world;
	private long worldSince = -1L;

	/** @return the server's id in a scoreboard line ("m12AB"), or null when the line has none */
	static String serverId(String line) {
		Matcher matcher = SERVER_LINE.matcher(line);
		return matcher.find() ? matcher.group(1) : null;
	}

	/**
	 * Takes one look.
	 *
	 * @param world the world the player is in, or null while in none; only compared by identity
	 * @param id    the server's id read now, or null when none can be read
	 * @return whether a new lobby began now
	 */
	boolean update(Object world, String id, long nowMs) {
		if (world == null) {
			this.world = null;
			worldSince = -1L;
			return false;
		}
		if (world != this.world) {
			this.world = world;
			worldSince = nowMs;
		}
		if (id != null) {
			boolean other = !id.equals(lastId);
			lastId = id;
			worldSince = -1L;
			return other;
		}
		if (worldSince >= 0L && nowMs - worldSince >= WAIT_MS) {
			// no id to tell by: the world change is the new lobby; the next id seen is compared with none
			worldSince = -1L;
			lastId = null;
			return true;
		}
		return false;
	}
}
