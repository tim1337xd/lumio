package dev.tim1337.lumio.features.music;

/**
 * The scroll of a lyric line wider than its room in the info bar. The sung word with its box is kept around the middle of the room while the line
 * scrolls underneath: at the start of a line the box walks from the first word to the middle, and near the end of a long line it moves on to the
 * right only once there is no more text to scroll. A line that fits does not scroll at all.
 * <p>
 * The scroll does not chase every word: it follows a focus point that looks a little ahead to where the singing will be shortly, so in fast parts
 * it moves evenly instead of in jumps. Both the focus and the scroll are critically damped (no overshoot) and stepped exactly for the elapsed
 * time, so the motion is the same at any frame rate and after a stall. It may lag slightly behind the singing; the box itself is never hidden.
 */
final class BarScroll {
	/** Where the box is kept, as a share of the room. */
	static final float CENTER = 0.5f;
	/** How much the focus leans towards where the singing will be shortly, from 0 (the box) to 1 (the point ahead). */
	static final float AHEAD_WEIGHT = 0.75f;
	/**
	 * How quickly the focus and the scroll settle, in radians per second. A critically damped follower trails a steady motion by 2 / rate seconds;
	 * the two in a row trail by 2 / 10 + 2 / 14, about 0.34 s. The focus' lean ahead (the info bar looks 0.45 s ahead, times {@link #AHEAD_WEIGHT})
	 * makes up for that, so in a steady part, also a fast one, the box stays at the middle while the motion stays smooth.
	 */
	static final float FOCUS_RATE = 10f;
	static final float SCROLL_RATE = 14f;
	/** The room kept between the box and a cut edge of the room, in GUI pixels. */
	static final float BOX_MARGIN = 6f;

	private float focus;
	private float focusVelocity;
	private float scroll;
	private float scrollVelocity;

	/** Starts a new line at its beginning, with the focus on its first word. */
	void reset(float boxCenter) {
		focus = boxCenter;
		focusVelocity = 0f;
		scroll = 0f;
		scrollVelocity = 0f;
	}

	float scroll() {
		return scroll;
	}

	/**
	 * Moves the scroll for one frame.
	 *
	 * @param boxCenter the middle of the box (or of the sung point without one), in line units
	 * @param ahead     where the singing will be shortly, in line units; never before the box
	 * @param boxLeft   the box's edges, kept inside the room whatever the smoothing does
	 * @param total     the width of the line (with what follows it), and {@code room} the width it is shown in
	 * @param animated  false: the scroll stands where it should, without motion
	 * @return the scroll, from 0 to {@code total - room}
	 */
	float update(float boxCenter, float ahead, float boxLeft, float boxRight, float total, float room, float dt, boolean animated) {
		float most = Math.max(0f, total - room);
		if (most <= 0f) {
			scroll = 0f;
			scrollVelocity = 0f;
			focus = boxCenter;
			focusVelocity = 0f;
			return 0f;
		}
		float wantedFocus = focusTarget(boxCenter, ahead);
		if (!animated) {
			focus = wantedFocus;
			focusVelocity = 0f;
			scroll = target(focus, total, room);
			scrollVelocity = 0f;
			return scroll;
		}
		float x = focus - wantedFocus;
		focus = wantedFocus + damped(x, focusVelocity, FOCUS_RATE, dt);
		focusVelocity = dampedVelocity(x, focusVelocity, FOCUS_RATE, dt);
		float wanted = target(focus, total, room);
		float s = scroll - wanted;
		scroll = wanted + damped(s, scrollVelocity, SCROLL_RATE, dt);
		scrollVelocity = dampedVelocity(s, scrollVelocity, SCROLL_RATE, dt);
		// whatever the smoothing state, the box stays inside the room; it only takes effect after a jump (a seek within the line)
		float keep = Math.min(BOX_MARGIN, room / 4f);
		float least = Math.max(0f, boxRight - room + keep);
		float highest = Math.min(most, Math.max(0f, boxLeft - keep));
		if (scroll < least || scroll > highest) {
			scroll = Math.max(least, Math.min(highest, scroll));
			scrollVelocity = 0f;
		}
		scroll = Math.max(0f, Math.min(most, scroll));
		return scroll;
	}

	/** @return the point the scroll keeps in the middle: the box, leaning towards where the singing goes next */
	static float focusTarget(float boxCenter, float ahead) {
		return boxCenter + Math.max(0f, ahead - boxCenter) * AHEAD_WEIGHT;
	}

	/** @return the scroll that puts the focus at the middle of the room, as far as the line allows */
	static float target(float focus, float total, float room) {
		return Math.max(0f, Math.min(Math.max(0f, total - room), focus - room * CENTER));
	}

	/** @return the offset from the target after {@code dt} of a critically damped motion, solved exactly so any step is stable */
	static float damped(float offset, float velocity, float rate, float dt) {
		return (offset + (velocity + rate * offset) * dt) * (float) Math.exp(-rate * dt);
	}

	/** @return its velocity after {@code dt} */
	static float dampedVelocity(float offset, float velocity, float rate, float dt) {
		return (velocity - rate * (velocity + rate * offset) * dt) * (float) Math.exp(-rate * dt);
	}
}
