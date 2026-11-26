package dev.tim1337.lumio.api.experimental.util;

/**
 * The ping Lumio measures with the game's own ping request, the value its info bar shows (the latency in the player list is a fixed number on
 * many servers, Hypixel among them). While the info bar shows the ping it is the average of the last answers; otherwise nothing is measured
 * until an add-on asks for one measurement ({@link #ask}), which sends one request, never more often than the info bar would.
 */
public final class ServerPing {
	/** No measurement yet, or none young enough. */
	public static final int NONE = -1;

	private static volatile int millis = NONE;
	private static volatile long measuredAt;
	private static volatile Runnable asker = () -> {
	};

	private ServerPing() {
	}

	/**
	 * @param now       the time now, as {@code System.currentTimeMillis()}
	 * @param mostAgeMs how old the measurement may be, in milliseconds
	 * @return the ping in milliseconds, or {@link #NONE} when none that young is known
	 */
	public static int millis(long now, long mostAgeMs) {
		int known = millis;
		return known != NONE && now - measuredAt <= mostAgeMs ? known : NONE;
	}

	/** Asks Lumio for one measurement when none is running; the answer shows in {@link #millis} a moment later. On the game's thread. */
	public static void ask() {
		asker.run();
	}

	/** A measurement, from Lumio, on any thread. */
	public static void measured(int pingMillis, long now) {
		millis = pingMillis;
		measuredAt = now;
	}

	/** Forgets the measurement: another server or world. From Lumio. */
	public static void forget() {
		millis = NONE;
	}

	/** Sets what sends one ping request when asked; from Lumio, once at its start. */
	public static void asker(Runnable sendsOne) {
		asker = sendsOne;
	}
}
