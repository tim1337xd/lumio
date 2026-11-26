package dev.tim1337.lumio.features.music;

/**
 * A smooth clock of the song for motion. The player's position arrives in steps: each poll moves the extrapolated position a little forward or
 * back, and the lead the lyrics run ahead by changes with the next line. Moving by them directly made the lyrics stop and go. This clock follows
 * the extrapolated position, but every step of it (and of the lead) is eased in from the moment it happened, with a curve whose speed and
 * acceleration start and end at zero, over at least {@link #EASE_SECONDS} and long enough that the clock's speed stays within
 * {@link #MOST_LEAN} of the song's. So the clock never jumps, never runs back, and its speed and acceleration have no step; and as it is a function
 * of the real time and the player's reports alone, it is the same at any frame rate. A pause stops it; a step beyond {@link #SEEK_MS} (a seek, a
 * skip, a new song) is a cut: the clock takes the new position at once, and says so. Plain logic, tested without the game.
 */
final class SongClock {
	/** A step this large is a seek: taken at once. */
	static final double SEEK_MS = 1_500.0;
	/** A step is eased in over at least this time, and so that the clock's speed leans at most this far from the song's. */
	static final double EASE_SECONDS = 0.5;
	static final double MOST_LEAN = 0.15;
	/** Steps smaller than this (rounding) go straight in. */
	private static final double LEAST_STEP_MS = 0.05;
	/** The steepest slope of the easing curve, for its whole step over its whole time 1. */
	private static final double STEEPEST = 1.875;
	private static final int MOST_STEPS = 16;

	private final double[] starts = new double[MOST_STEPS];
	private final double[] amounts = new double[MOST_STEPS];
	private final double[] lengths = new double[MOST_STEPS];
	private int steps;
	private boolean started;
	private boolean wasPlaying;
	private double lastPlayer;
	private double lastLead;
	private double lastNow;
	private double ms;

	/** Forgets the song: the next update takes the player's position as it is. */
	void reset() {
		started = false;
	}

	/**
	 * Moves the clock to a moment.
	 *
	 * @param playerMs      where the player's position is now, extrapolated from its last report
	 * @param reportSeconds when that report was made, in the same time as {@code nowSeconds}
	 * @param leadMs        how far ahead of the position the lyrics run
	 * @param playing       whether it plays
	 * @param nowSeconds    the real time now
	 * @return whether this was a cut: the first update, or a seek
	 */
	boolean update(double playerMs, double reportSeconds, double leadMs, boolean playing, double nowSeconds) {
		if (!started) {
			return cut(playerMs, leadMs, playing, nowSeconds);
		}
		double frame = Math.max(0.0, nowSeconds - lastNow);
		double expected = lastPlayer + (playing && wasPlaying ? frame * 1000.0 : 0.0);
		double step = playerMs - expected;
		double leadStep = leadMs - lastLead;
		if (Math.abs(step + leadStep) > SEEK_MS) {
			return cut(playerMs, leadMs, playing, nowSeconds);
		}
		if (!playing) {
			// paused: it stands, and so do the steps still being eased in, to go on where they were when it plays again
			for (int i = 0; i < steps; i++) {
				starts[i] += frame;
			}
		}
		// a poll is eased in from when the player was asked; a new lead from now
		add(step, Math.min(nowSeconds, Math.max(lastNow, reportSeconds)));
		add(leadStep, nowSeconds);
		lastPlayer = playerMs;
		lastLead = leadMs;
		lastNow = nowSeconds;
		wasPlaying = playing;
		if (playing) {
			ms = playerMs + leadMs - remaining(nowSeconds);
		}
		return false;
	}

	private boolean cut(double playerMs, double leadMs, boolean playing, double nowSeconds) {
		started = true;
		steps = 0;
		lastPlayer = playerMs;
		lastLead = leadMs;
		lastNow = nowSeconds;
		wasPlaying = playing;
		ms = playerMs + leadMs;
		return true;
	}

	/** Starts easing in a step. */
	private void add(double amount, double at) {
		if (Math.abs(amount) < LEAST_STEP_MS) {
			if (amount != 0.0 && steps > 0) {
				// rounding: carried by the newest step, which has the most of its way still to go
				amounts[steps - 1] += amount;
			}
			return;
		}
		if (steps == MOST_STEPS) {
			// the oldest is almost in: it is taken in now, a fraction of a millisecond
			System.arraycopy(starts, 1, starts, 0, MOST_STEPS - 1);
			System.arraycopy(amounts, 1, amounts, 0, MOST_STEPS - 1);
			System.arraycopy(lengths, 1, lengths, 0, MOST_STEPS - 1);
			steps--;
		}
		starts[steps] = at;
		amounts[steps] = amount;
		lengths[steps] = Math.max(EASE_SECONDS, Math.abs(amount) * STEEPEST / (MOST_LEAN * 1000.0));
		steps++;
	}

	/** @return how much of the steps is still to come in, in milliseconds; steps fully in are forgotten */
	private double remaining(double now) {
		double left = 0.0;
		int kept = 0;
		for (int i = 0; i < steps; i++) {
			double t = (now - starts[i]) / lengths[i];
			if (t >= 1.0) {
				continue;
			}
			left += amounts[i] * (1.0 - ease(Math.max(0.0, t)));
			starts[kept] = starts[i];
			amounts[kept] = amounts[i];
			lengths[kept] = lengths[i];
			kept++;
		}
		steps = kept;
		return left;
	}

	/** Smootherstep: from 0 to 1 with no speed and no acceleration at either end. */
	static double ease(double t) {
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}

	/** @return the clock, in milliseconds of the song */
	double ms() {
		return ms;
	}
}
