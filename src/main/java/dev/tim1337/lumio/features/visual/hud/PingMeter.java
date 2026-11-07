package dev.tim1337.lumio.features.visual.hud;

/**
 * Measures the ping like the game's F3 network chart: the game's own ping request carries a timestamp, the server echoes it at once, and the
 * time until the answer is the round trip. One request about every four seconds, never more than one waiting for its answer; one without an
 * answer after ten seconds is given up. The ping shown is the average of the last ten answers. Everything starts over on another server or
 * world. The answer arrives on the network thread, the requests go out from the main thread, so the state is guarded.
 */
final class PingMeter {
	/** How often a request goes out, and how long one waits for its answer before it is given up, in milliseconds. */
	static final long INTERVAL_MILLIS = 4000L;
	static final long TIMEOUT_MILLIS = 10_000L;
	/** How many answers the average covers. */
	static final int SAMPLES = 10;
	/** No request answered yet. */
	static final int NONE = -1;
	private static final long NOT_WAITING = -1L;

	private final long[] times = new long[SAMPLES];
	private int count;
	private int next;
	/** The timestamp of the request waiting for its answer, or {@link #NOT_WAITING}; when the last one went out. */
	private long waitingFor = NOT_WAITING;
	private long lastSent = Long.MIN_VALUE / 2;
	/** What the measuring belongs to: the connection and the world. */
	private Object connection;
	private Object world;
	private volatile int average = NONE;

	/**
	 * @return whether a request should go out now, from this connection and world; if so it counts as sent, with {@code now} as its timestamp.
	 *         Another connection or world starts the measuring over.
	 */
	synchronized boolean due(Object connectionNow, Object worldNow, long now) {
		if (connectionNow != connection || worldNow != world) {
			reset();
			connection = connectionNow;
			world = worldNow;
		}
		if (waitingFor != NOT_WAITING && now - waitingFor > TIMEOUT_MILLIS) {
			// no answer: given up, a later answer to it is not counted
			waitingFor = NOT_WAITING;
		}
		if (waitingFor != NOT_WAITING || now - lastSent < INTERVAL_MILLIS) {
			return false;
		}
		waitingFor = now;
		lastSent = now;
		return true;
	}

	/** An answer arrived: counted only when it answers the request waiting, the one this meter sent. */
	synchronized void answered(long timestamp, long now) {
		if (waitingFor == NOT_WAITING || timestamp != waitingFor) {
			return;
		}
		waitingFor = NOT_WAITING;
		long time = now - timestamp;
		if (time < 0 || time > TIMEOUT_MILLIS) {
			return;
		}
		times[next] = time;
		next = (next + 1) % SAMPLES;
		count = Math.min(SAMPLES, count + 1);
		long sum = 0;
		for (int i = 0; i < count; i++) {
			sum += times[i];
		}
		average = (int) Math.round((double) sum / count);
	}

	/** @return the average of the last answers in milliseconds, or {@link #NONE} before the first */
	int average() {
		return average;
	}

	/** Forgets everything, for another server or world, or when the measuring stops. */
	synchronized void reset() {
		count = 0;
		next = 0;
		waitingFor = NOT_WAITING;
		lastSent = Long.MIN_VALUE / 2;
		average = NONE;
		connection = null;
		world = null;
	}
}
