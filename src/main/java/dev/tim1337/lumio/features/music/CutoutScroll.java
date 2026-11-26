package dev.tim1337.lumio.features.music;

/**
 * How far the stack of a scrolling Cut-out column has moved up, at a time of the song. Every line has its place in the stack once (its top, from
 * the stack's top, in text units) and never moves in it; only the whole stack moves, so that each row stands at the bottom of the column's room
 * about as it starts.
 * <p>
 * The target is the straight path through those points (each row at the bottom as it starts, resting before the first and after the last); the
 * stack follows it smoothed by a bell {@link #HALF_WINDOW_MS} wide to each side. So the column starts easing a little before a row begins and
 * arrives as it is sung; rows of uneven length make one steady drift instead of a speed per row; a long pause lets it come to rest softly; and a big
 * step (two rows at once) eases in and out. As the bell is never negative and the target never runs back, the stack never runs backwards and never
 * overshoots. Its place, speed and acceleration are continuous, and as it is a function of the time alone (worked out exactly, not stepped per
 * frame) the path is the same at any frame rate. Plain logic, tested without the game.
 */
final class CutoutScroll {
	/** The most lines one stack keeps track of: the older ones have long left the room at the top. */
	static final int MOST = 64;
	/** Half the width of the smoothing bell, in milliseconds. */
	static final double HALF_WINDOW_MS = 700.0;

	private final double[] times = new double[MOST];
	private final double[] places = new double[MOST];
	private int count;

	/** Forgets every line, for a new stack. */
	void clear() {
		count = 0;
	}

	int count() {
		return count;
	}

	/**
	 * A row joins the stack: from its start on it stands at the bottom of the room.
	 *
	 * @param startMs when it starts, in the song's time; later than the row before
	 * @param bottom  where its bottom lies in the stack, in text units from the stack's top
	 * @param room    how tall the column's room is
	 */
	void add(long startMs, float bottom, float room) {
		addPlace(startMs, bottom - room);
	}

	/**
	 * A point of the path given by its place: from its start on the stack has moved up this far.
	 *
	 * @param startMs when it starts, in the song's time; later than the one before
	 * @param place   how far the stack has moved up, in text units
	 */
	void addPlace(long startMs, float place) {
		double start = count > 0 && startMs <= times[count - 1] ? times[count - 1] + 1.0 : startMs;
		if (count == MOST) {
			// the oldest point has no say in the motion any more
			System.arraycopy(times, 1, times, 0, MOST - 1);
			System.arraycopy(places, 1, places, 0, MOST - 1);
			count--;
		}
		times[count] = start;
		// never back: a short line after a tall one waits where the stack is
		places[count] = count > 0 ? Math.max(places[count - 1], place) : place;
		count++;
	}

	/** A scrolling column keeps at most this many sung lines above the line being sung, whatever room it has. */
	static final int KEPT_LINES = 3;

	/**
	 * The tops of the latest lines of a scrolling stack: once a line starts, the stack has moved up at least so far that the line {@link #KEPT_LINES}
	 * before it stands at the room's top, so every older line has left over it (fading in the band below the top as it goes).
	 */
	static final class Kept {
		private final float[] tops = new float[KEPT_LINES];
		private int count;

		void clear() {
			count = 0;
		}

		/**
		 * @param top where the top of the line starting now lies in the stack, in text units from the stack's top
		 * @return the least the stack has moved up once it has started: the top of the line {@link #KEPT_LINES} before it, or no bound for the first ones
		 */
		float add(float top) {
			float least = count >= KEPT_LINES ? tops[count % KEPT_LINES] : Float.NEGATIVE_INFINITY;
			tops[count % KEPT_LINES] = top;
			count++;
			return least;
		}
	}

	/** A page's step is made over this time before its line starts, plus the smoothing to each side. */
	static final long STEP_MS = 400L;

	/**
	 * The Pages flow moves the same stack, but once per sung line and only as far as it needs: the lines stand one under the other from the top of
	 * the room, and a line that no longer fits under them moves the stack up just enough as it starts. The oldest rows then leave over the top edge,
	 * and the line sung before stays as far as the room allows.
	 *
	 * @param bottom where the bottom of the line starting now lies in the stack, in text units from the stack's top
	 * @param room   how tall the column's room is
	 * @return how far the stack has moved up once that line has started (never back is kept by {@link #addPlace})
	 */
	static float pagePlace(float bottom, float room) {
		return Math.max(0f, bottom - room);
	}

	/**
	 * A step of the Pages flow: the stack holds where it is until just before the line starts, then moves up to the place given as it starts;
	 * where it need not move, it simply holds. Unlike {@link #addPlace} it never drifts towards the next place over the time between.
	 */
	void addStep(long startMs, float place) {
		float held = count > 0 ? (float) places[count - 1] : 0f;
		if (place > held) {
			addPlace(startMs - STEP_MS, held);
		}
		addPlace(startMs, Math.max(held, place));
	}

	/** Moves every point by the same amount: the room changed (the column was placed anew), so the lines keep their places at the bottom. */
	void shift(float by) {
		for (int i = 0; i < count; i++) {
			places[i] += by;
		}
	}

	/** @return how far the stack has moved up at a time: the place of its top above the room's top, in text units */
	float at(double timeMs) {
		return (float) evaluate(timeMs, 0);
	}

	/** Like {@link #at}, in full precision, for measuring the motion. */
	double place(double timeMs) {
		return evaluate(timeMs, 0);
	}

	/** @return how fast the stack moves at a time, in text units per millisecond */
	float speed(double timeMs) {
		return (float) evaluate(timeMs, 1);
	}

	/** @return how fast its speed changes at a time, in text units per millisecond squared */
	double acceleration(double timeMs) {
		return evaluate(timeMs, 2);
	}

	/**
	 * The smoothed path or one of its derivatives. The straight target is its first place plus a ramp at every point, as much as the slope changes
	 * there; smoothing a ramp by the bell gives a smooth ramp, so only the points within the bell's reach differ from the straight target.
	 *
	 * @param order 0 the place, 1 the speed, 2 the acceleration
	 */
	private double evaluate(double t, int order) {
		if (count == 0) {
			return 0.0;
		}
		double straight = order == 0 ? straight(t) : order == 1 ? straightSlope(t) : 0.0;
		double h = HALF_WINDOW_MS;
		for (int i = 0; i < count; i++) {
			double x = t - times[i];
			if (x <= -h || x >= h) {
				continue;
			}
			double change = slopeAfter(i) - slopeAfter(i - 1);
			if (change == 0.0) {
				continue;
			}
			double s = x / h;
			straight += change * switch (order) {
				case 0 -> h * smoothRamp(s) - Math.max(0.0, x);
				case 1 -> share(s) - (x > 0.0 ? 1.0 : 0.0);
				default -> bell(s) / h;
			};
		}
		return straight;
	}

	/** @return the slope of the straight target after point i (0 before the first and after the last), in text units per millisecond */
	private double slopeAfter(int i) {
		if (i < 0 || i >= count - 1) {
			return 0.0;
		}
		return (places[i + 1] - places[i]) / (times[i + 1] - times[i]);
	}

	/** @return the straight target at a time */
	private double straight(double t) {
		if (t <= times[0]) {
			return places[0];
		}
		if (t >= times[count - 1]) {
			return places[count - 1];
		}
		int i = segment(t);
		return places[i] + (t - times[i]) * slopeAfter(i);
	}

	private double straightSlope(double t) {
		if (t <= times[0] || t >= times[count - 1]) {
			return 0.0;
		}
		return slopeAfter(segment(t));
	}

	/** The bell: (15/16)(1 - s²)² on -1..1, its area 1; it and its slope are 0 at its ends. */
	static double bell(double s) {
		double u = 1.0 - s * s;
		return 15.0 / 16.0 * u * u;
	}

	/** @return the share of the bell's area left of s */
	static double share(double s) {
		return 15.0 / 16.0 * (s - 2.0 * s * s * s / 3.0 + s * s * s * s * s / 5.0) + 0.5;
	}

	/** @return the area under {@link #share} from -1 to s: a ramp max(0, s) smoothed by the bell, 0 at -1 and 1 at 1 */
	static double smoothRamp(double s) {
		double s2 = s * s;
		return 15.0 / 16.0 * (s2 / 2.0 - s2 * s2 / 6.0 + s2 * s2 * s2 / 30.0) + s / 2.0 + 5.0 / 32.0;
	}

	/** @return the segment a time lies in: between point i and i + 1 */
	private int segment(double timeMs) {
		int low = 0;
		int high = count - 2;
		while (low < high) {
			int middle = (low + high + 1) >>> 1;
			if (times[middle] <= timeMs) {
				low = middle;
			} else {
				high = middle - 1;
			}
		}
		return low;
	}
}
