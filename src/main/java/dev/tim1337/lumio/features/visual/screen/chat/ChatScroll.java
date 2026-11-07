package dev.tim1337.lumio.features.visual.screen.chat;

/**
 * Where the open chat is scrolled to, in lines from the newest, with the part of a line that makes it smooth. A wheel step moves the target and the position eases there; steps coming fast add up, the
 * ease going on from where the position is. It never goes past the top or the bottom. Plain numbers, so it is unit tested.
 */
public final class ChatScroll {
	/** How far one step of a wheel goes. A high-resolution wheel or a trackpad sends parts of a step, which go as far as their part. */
	public static final float LINES_PER_STEP = 3f;
	public static final long EASE_NANOS = 120_000_000L;
	/** Adding up parts leaves a little behind, which must not count as a part of a line. */
	private static final float WHOLE_MARGIN = 0.001f;

	private float position;
	private float from;
	private float target;
	private long startedAt;
	private boolean easing;

	/** @return how many lines a wheel moves, up being positive: a step's worth, or with Shift a page's */
	public static float lines(double wheel, boolean page, int perPage) {
		return (float) wheel * (page ? pageLines(perPage) : LINES_PER_STEP);
	}

	/** @return how many lines a page moves, keeping one line of the page before, as the game's Page Up does */
	public static int pageLines(int perPage) {
		return Math.max(1, perPage - 1);
	}

	private static float eased(float share) {
		float left = 1f - share;
		return 1f - left * left * left;
	}

	private static float clamp(float value, float most) {
		return Math.max(0f, Math.min(value, most));
	}

	/**
	 * Goes further, easing.
	 *
	 * @param most the highest place: where the oldest line reaches the top
	 */
	public void add(float lines, float most, long nanos) {
		position = step(nanos, most);
		from = position;
		target = clamp(target + lines, most);
		startedAt = nanos;
		easing = target != position;
	}

	/** Goes there at once, as the scroll bar and a jump to a search match do. */
	public void set(float place) {
		position = place;
		from = place;
		target = place;
		easing = false;
	}

	/** Lines came in below while the player is scrolled up: everything moves with them, so the view stays. On the way to the newest it still goes there. */
	public void linesAdded(float lines) {
		position += lines;
		from += lines;
		if (target > 0f) {
			target += lines;
		}
	}

	/** @return the place at this time, for the frame */
	public float step(long nanos, float most) {
		if (easing) {
			float share = (nanos - startedAt) / (float) EASE_NANOS;
			if (share >= 1f) {
				easing = false;
				position = target;
			} else {
				position = from + (target - from) * eased(Math.max(0f, share));
			}
		}
		// fewer lines than before, or a lower limit: nothing stays beyond the end
		if (position > most || target > most) {
			position = clamp(position, most);
			from = clamp(from, most);
			target = clamp(target, most);
		}
		return position;
	}

	/** @return whether it is away from the newest, or on its way there or from there */
	public boolean awayFromNewest() {
		return position > 0f || target > 0f;
	}

	/** @return the last place worked out */
	public float position() {
		return position;
	}

	/** @return the lowest whole line in view: what the game counts in */
	public int line() {
		return (int) (position + WHOLE_MARGIN);
	}

	/** @return how much of the next line is in view, from 0 to below 1; nothing when it is as good as whole */
	public float part() {
		float part = position - line();
		return part <= WHOLE_MARGIN ? 0f : part;
	}

	/** @return how far up from the track's bottom the thumb lies */
	public static float thumbFromBottom(float place, float most, int track, int thumb) {
		return most <= 0f ? 0f : (track - thumb) * clamp(place, most) / most;
	}

	/**
	 * @param fromBottom where the mouse is, up from the track's bottom
	 * @param grab       where on the thumb it holds, up from the thumb's bottom
	 * @return the place that puts the thumb under the mouse
	 */
	public static float placeAt(float fromBottom, float grab, float most, int track, int thumb) {
		int room = track - thumb;
		return room <= 0 ? 0f : clamp((fromBottom - grab) / room * most, most);
	}
}
