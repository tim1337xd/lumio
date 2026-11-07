package dev.tim1337.lumio.api.experimental.util;

import dev.tim1337.lumio.api.spi.Backend;

/** Verbose diagnostic logging (server data, lyric lookups, ...), written only while the "Debug log" switch on the Misc page is on. */
public final class Debug {
	private Debug() {
	}

	/** @return whether the Debug log switch is on */
	public static boolean on() {
		return Backend.get().debugOn();
	}

	public static void log(String message, Object... arguments) {
		if (on()) {
			Logs.info(message, arguments);
		}
	}
}
