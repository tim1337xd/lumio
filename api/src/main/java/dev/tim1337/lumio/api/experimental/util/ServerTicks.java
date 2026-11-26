package dev.tim1337.lumio.api.experimental.util;

import dev.tim1337.lumio.api.experimental.debug.DebugCategory;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Real server ticks, counted from the ping the server sends every tick (Hypixel does), never from the client's frames or ticks: for split
 * times and timers that must follow the server. The count only rises while connected to such a server.
 */
public final class ServerTicks {
	private static final List<Runnable> LISTENERS = new ArrayList<>();
	private static long count;
	/** The id of the last ping counted: the same ping twice is one tick. */
	private static int lastId;
	/** For the debug log: the steps between the ids of the pings, counted over a window, so the log shows whether the server sends one per tick. */
	private static final int ID_WINDOW = 400;
	private static int pings;
	private static int steppedByOne;
	private static int repeated;
	/** The world's game time the server last sent (about once a second), or -1 while none came yet: a cross-check for the tick count. */
	private static long serverGameTime = -1;

	private ServerTicks() {
	}

	/** @return how many server ticks were seen since the game started */
	public static long count() {
		return count;
	}

	/** Runs something on every server tick, on the game's thread; added once while an add-on starts. */
	public static void listen(Runnable listener) {
		LISTENERS.add(listener);
	}

	/** @return whether a ping is a new tick: not a ping without an id (0, which is no tick ping) and not the same id as the last one */
	static boolean newTick(int id, int lastId) {
		return id != 0 && id != lastId;
	}

	/**
	 * A tick ping of the server, by its id; called by Lumio on the game's thread. A ping with the same id as the last one is the same tick and is not
	 * counted again; a ping with id 0 is not a tick ping.
	 */
	public static void ping(int id) {
		if (id == 0) {
			return;
		}
		boolean same = !newTick(id, lastId);
		if (DebugHooks.ACTIVE) {
			pings++;
			steppedByOne += Math.abs(id - lastId) == 1 ? 1 : 0;
			repeated += same ? 1 : 0;
			if (pings >= ID_WINDOW) {
				DebugHooks.log(DebugCategory.GENERAL, "Server tick pings: {} in the last {}, {} one id after the last, {} repeated", pings, ID_WINDOW, steppedByOne, repeated);
				pings = 0;
				steppedByOne = 0;
				repeated = 0;
			}
		}
		if (same) {
			return;
		}
		lastId = id;
		tick();
	}

	/** @return the world's game time the server last sent, or -1 while none came yet (it comes about once a second) */
	public static long serverGameTime() {
		return serverGameTime;
	}

	/** The game time of the server's time update; called by Lumio on the game's thread. */
	public static void serverTime(long gameTime) {
		serverGameTime = gameTime;
	}

	/** One more server tick; called by Lumio on the game's thread. */
	public static void tick() {
		count++;
		for (int i = 0; i < LISTENERS.size(); i++) {
			Guard.run("server tick", LISTENERS.get(i));
		}
	}
}
