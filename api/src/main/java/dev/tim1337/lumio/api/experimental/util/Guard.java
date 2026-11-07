package dev.tim1337.lumio.api.experimental.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs code that reads outside data (a chat message, an add-on's callback) so a failure can never interrupt the game: the error is logged once
 * per name, so a message repeated by a player cannot flood the log, and the code is skipped for that call.
 */
public final class Guard {
	private static final Set<String> FAILED = new HashSet<>();

	private Guard() {
	}

	/** Runs the action; an error in it is logged once for the name and swallowed. */
	public static void run(String name, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			if (FAILED.add(name)) {
				Logs.warn("The {} failed and is skipped where it fails again", name, e);
			}
		}
	}
}
