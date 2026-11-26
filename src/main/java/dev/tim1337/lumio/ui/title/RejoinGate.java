package dev.tim1337.lumio.ui.title;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Keeps the main menu's Rejoin to one connection per click: closed from the click until the connect screen is gone, and for a minute after
 * Mojang's login server answered that it is busy. Also counts what a click led to, for the debug log. Plain logic, tested without the game.
 */
public final class RejoinGate {
	/** How long Rejoin waits after Mojang's login server said it is busy. */
	public static final long BUSY_WAIT_MS = 60_000L;
	/** The line under the game's own text on the screen of such a failed login. */
	public static final String BUSY_LINE = "[Lumio] Mojang's login server is busy. Wait about a minute.";
	/** The gate of the running game. */
	public static final RejoinGate SHARED = new RejoinGate();

	/** How the game words a login refused with HTTP 429, in every language: the status comes from the login library, untranslated. */
	private static final Pattern BUSY_REASON = Pattern.compile("Status:\\s*429\\b");
	private static final long MS_PER_SECOND = 1000L;

	private boolean connecting;
	private long busyUntilMs;
	private final AtomicInteger connectScreens = new AtomicInteger();
	private final AtomicInteger sessionJoins = new AtomicInteger();

	/** @return whether a disconnect reason is Mojang's login server refusing with HTTP 429 */
	public static boolean busyReason(String reason) {
		return reason != null && BUSY_REASON.matcher(reason).find();
	}

	/** @return whether Rejoin can be pressed now */
	public boolean ready(long nowMs) {
		return !connecting && nowMs >= busyUntilMs;
	}

	/**
	 * Runs a click: connects only if the gate is open, and closes it first.
	 *
	 * @return whether it connected
	 */
	public boolean click(long nowMs, Runnable connect) {
		if (!ready(nowMs)) {
			return false;
		}
		connecting = true;
		connectScreens.set(0);
		sessionJoins.set(0);
		try {
			connect.run();
		} catch (RuntimeException e) {
			connecting = false;
			throw e;
		}
		return true;
	}

	/** The connect screen is gone: Rejoin opens again, unless it has to wait. */
	public void connectEnded() {
		connecting = false;
	}

	/** Mojang's login server said it is busy: Rejoin waits. */
	public void loginBusy(long nowMs) {
		connecting = false;
		busyUntilMs = nowMs + BUSY_WAIT_MS;
	}

	/** @return the whole seconds Rejoin still waits for the login server, 0 when it does not */
	public int secondsLeft(long nowMs) {
		long left = busyUntilMs - nowMs;
		return left <= 0L ? 0 : (int) ((left + MS_PER_SECOND - 1L) / MS_PER_SECOND);
	}

	/** @return how many connect screens were opened since the last click, this one counted */
	public int countConnectScreen() {
		return connectScreens.incrementAndGet();
	}

	/** @return how many session joins were made since the last click, this one counted */
	public int countSessionJoin() {
		return sessionJoins.incrementAndGet();
	}
}
