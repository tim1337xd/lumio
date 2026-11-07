package dev.tim1337.lumio.features.music;

/**
 * When an adlib shows: slightly before its first word until a moment after its last, and always for at least {@link #LEAST_SHOWN_MS} from when
 * it first showed, however short its timing (a quick "hey", a zero or negative length, a start and end within one frame). Plain numbers, so it is
 * unit tested.
 */
final class AdlibTimes {
	/** An adlib shows this long before its start and stays this long after its end. */
	static final long LEAD_MS = 100L;
	static final long HOLD_MS = 300L;
	/** Every adlib shows at least this long. */
	static final long LEAST_SHOWN_MS = 900L;
	/** An adlib without an end of its own: at least this long, and this long per letter. */
	private static final long SHORTEST_MS = 700L;
	private static final long MS_PER_LETTER = 70L;
	/** No next adlib. */
	static final long NO_NEXT = -1L;
	/** Not shown yet. */
	static final long NOT_SHOWN = Long.MIN_VALUE;
	/** It fades in this fast and out at the usual pace, in seconds. */
	static final float IN_SECONDS = 0.08f;
	static final float OUT_SECONDS = 0.35f;
	/** How fast it goes when there is no room left for what comes after it. */
	static final float FORCED_OUT_SECONDS = 0.12f;

	private AdlibTimes() {
	}

	/**
	 * @param nextStartMs when the next adlib starts, or {@link #NO_NEXT}
	 * @param letters     how long its text is
	 * @return when an adlib ends: at its own end; without one, where the next adlib starts or after a time by its length, whichever comes first; never at its line's end
	 */
	static long ownEnd(long startMs, long endMs, long nextStartMs, int letters) {
		if (endMs > startMs) {
			return endMs;
		}
		long byLength = startMs + Math.max(SHORTEST_MS, letters * MS_PER_LETTER);
		return nextStartMs > startMs ? Math.min(nextStartMs, byLength) : byLength;
	}

	/** @return whether an adlib is within its own time now: from just before its start until a moment after its end (a bad end counts as its start) */
	static boolean due(long startMs, long endMs, long nowMs) {
		long end = Math.max(startMs, endMs);
		return nowMs >= startMs - LEAD_MS && nowMs <= end + HOLD_MS;
	}

	/**
	 * @return whether an adlib shows now: within its own time, or less than {@link #LEAST_SHOWN_MS} since it first showed (and not before that,
	 *         after a seek back)
	 * @param firstShownMs when it first showed, or {@link #NOT_SHOWN}
	 */
	static boolean shown(long startMs, long endMs, long firstShownMs, long nowMs) {
		if (due(startMs, endMs, nowMs)) {
			return true;
		}
		return firstShownMs != NOT_SHOWN && nowMs >= firstShownMs && nowMs - firstShownMs < LEAST_SHOWN_MS;
	}
}
