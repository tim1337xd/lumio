package dev.tim1337.lumio.features.music;

/**
 * How a Cut-out column goes on from line to line: in pages, whose lines stand under each other until the room is full and then move up as far
 * as the next line needs, or as one stack that scrolls up row by row. Auto scrolls while the player stands still (no movement for
 * {@link #STILL_MS}) or the cinematic camera films, and uses pages while the player moves. The choice changes
 * only when a line starts ({@link #atLineStart}), never in the middle of one, so a switch is one soft cross-fade between two lines. Plain logic,
 * tested without the game.
 */
final class CutoutFlow {
	/** The flow setting. */
	enum Mode {
		PAGES("Pages"),
		SCROLL("Scroll"),
		AUTO("Auto");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** No movement for this long is standing still. */
	static final long STILL_MS = 2_000L;

	private long movedAt = Long.MIN_VALUE / 2;
	private Mode shown = Mode.PAGES;

	/** The player moved at this time (real time, in milliseconds). */
	void moved(long nowMs) {
		movedAt = nowMs;
	}

	/** @return whether the player moved within the last {@link #STILL_MS} */
	boolean moving(long nowMs) {
		return nowMs - movedAt < STILL_MS;
	}

	/** @return the flow the lines go on in now: what the last line start chose */
	Mode shown() {
		return shown;
	}

	/**
	 * A line starts: the flow is chosen for it and the lines after it, until the next line start.
	 *
	 * @param setting the flow chosen in the settings
	 * @param nowMs   the real time, in milliseconds
	 * @param filmed  whether the cinematic camera films
	 * @return the flow, Pages or Scroll
	 */
	Mode atLineStart(Mode setting, long nowMs, boolean filmed) {
		shown = setting != Mode.AUTO ? setting : filmed || nowMs - movedAt >= STILL_MS ? Mode.SCROLL : Mode.PAGES;
		return shown;
	}
}
