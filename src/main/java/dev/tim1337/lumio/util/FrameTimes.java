package dev.tim1337.lumio.util;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.Logs;

import java.util.Arrays;

/**
 * A frame time log for one animation (the startup transition, a loading screen): it keeps the time of every frame, and when the animation ends
 * writes the number of frames, the median, the 99th percentile and the worst frame to the log. Frames can carry what happened in them (the
 * phase, and work done in that frame); then every slow frame is listed too, with its time from a marked moment. Off in normal play: only while
 * the debug log is on, or when the game was started with {@code -Dlumio.frametimes=true} (the game tests). Recording costs no
 * allocations unless a frame notes its work.
 */
public final class FrameTimes {
	private static final int CAPACITY = 4096;
	/** A frame slower than this is listed on its own. */
	private static final long SLOW_NANOS = 25_000_000L;
	private static final int MAX_LISTED = 40;
	private static final boolean FORCED = Boolean.getBoolean("lumio.frametimes");

	private final String name;
	private final long[] frames = new long[CAPACITY];
	private final String[] labels = new String[CAPACITY];
	private int count;
	private long last;
	private String noted;
	/** The frame the listed times count from, or -1. */
	private int zero = -1;

	public FrameTimes(String name) {
		this.name = name;
	}

	public static boolean enabled() {
		return FORCED || Debug.on();
	}

	/** Records one frame of the animation. */
	public void frame() {
		frame(null);
	}

	/** Records one frame of the animation in a phase (a fixed text), with the work noted for it. */
	public void frame(String phase) {
		if (!enabled()) {
			return;
		}
		long now = System.nanoTime();
		if (last != 0L && count < CAPACITY) {
			labels[count] = noted == null ? phase : phase + ": " + noted;
			frames[count++] = now - last;
		}
		noted = null;
		last = now;
	}

	/** Notes work done in the current frame, so a slow frame says what it did. */
	public void note(String work) {
		if (enabled()) {
			noted = noted == null ? work : noted + ", " + work;
		}
	}

	/** Marks the moment the listed frame times count from (the end of loading). */
	public void markZero() {
		if (enabled()) {
			zero = count;
		}
	}

	/** Ends the animation: writes its frame times to the log and starts over. */
	public void finish() {
		if (count > 0 && enabled()) {
			long[] sorted = Arrays.copyOf(frames, count);
			Arrays.sort(sorted);
			Logs.info("Frame times of {}: {} frames, median {} ms, 99% {} ms, worst {} ms", name, count, millis(sorted[count / 2]),
				millis(sorted[Math.min(count - 1, (int) (count * 0.99))]), millis(sorted[count - 1]));
			listSlow();
		}
		count = 0;
		last = 0L;
		noted = null;
		zero = -1;
	}

	private void listSlow() {
		if (labels[0] == null && zero < 0) {
			return;
		}
		// the time of every frame from the marked one, so the list shows when it happened
		long[] at = new long[count];
		long sum = 0L;
		for (int i = 0; i < count; i++) {
			sum += frames[i];
			at[i] = sum;
		}
		long origin = zero > 0 ? at[zero - 1] : 0L;
		int listed = 0;
		for (int i = 0; i < count && listed < MAX_LISTED; i++) {
			if (frames[i] > SLOW_NANOS) {
				Logs.info("  slow frame of {} at {} s: {} ms ({})", name, seconds(at[i] - origin),
					millis(frames[i]), labels[i]);
				listed++;
			}
		}
	}

	private static String seconds(long nanos) {
		return String.valueOf(Math.round(nanos / 10_000_000.0) / 100.0);
	}

	private static String millis(long nanos) {
		return String.valueOf(Math.round(nanos / 10_000.0) / 100.0);
	}
}
