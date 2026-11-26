package dev.tim1337.lumio.api.experimental.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background threads of the mods: daemons with a name, so they never keep the game from closing and are found in a thread dump. */
public final class Threads {
	private Threads() {
	}

	/** @return an executor that runs its tasks one at a time, on one daemon thread with this name */
	public static ExecutorService daemonExecutor(String name) {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		});
	}
}
