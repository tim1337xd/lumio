package dev.tim1337.lumio.features.music;

/**
 * Where a highlight box is (see {@link HighlightBox}): behind the sung word, gliding from word to word. Both edges move alike on a critically damped
 * motion, so the box keeps its shape while it travels and arrives without overshoot or jitter, also for very short or fast words; without word
 * timing it is softer, so it flows evenly along the line. Positions stay fractional and are only rounded where drawn. On a new row it grows in
 * from the word's start; the box of the row before fades out quickly. In text units of the line; with animations off it moves straight from word
 * to word.
 */
final class LyricBox {
	/** How fast the box of the row before fades out, per second. */
	private static final float OLD_ROW_FADE = 8f;
	/** How quickly the edges follow, as the motion's angular frequency per second: from word to word, and gliding along a line without timing. */
	static final float WORD_RATE = 26f;
	static final float GLIDE_RATE = 10f;

	private float left;
	private float right;
	private float leftSpeed;
	private float rightSpeed;
	private int row = -1;
	/** The box of the row before, fading out: its row, edges and how visible it still is. */
	int oldRow = -1;
	float oldLeft;
	float oldRight;
	float oldAlpha;

	/** Starts again for a new line: the box grows in from its first word. */
	void reset() {
		row = -1;
		oldRow = -1;
		oldAlpha = 0f;
	}

	/** Moves the box towards a word of a row, in text units. */
	void follow(int wordRow, float from, float to, float dt, boolean animated) {
		follow(wordRow, from, to, dt, animated, false);
	}

	/**
	 * Moves the box towards a part of a row, in text units.
	 *
	 * @param glide whether it glides softly along the line (a line without word timing) instead of moving from word to word
	 */
	void follow(int wordRow, float from, float to, float dt, boolean animated, boolean glide) {
		if (!animated) {
			row = wordRow;
			left = from;
			right = to;
			leftSpeed = 0f;
			rightSpeed = 0f;
			oldAlpha = 0f;
			return;
		}
		if (wordRow != row) {
			if (row >= 0) {
				oldRow = row;
				oldLeft = left;
				oldRight = right;
				oldAlpha = 1f;
			}
			row = wordRow;
			left = from;
			right = from;
			leftSpeed = 0f;
			rightSpeed = 0f;
		}
		float rate = glide ? GLIDE_RATE : WORD_RATE;
		float decay = (float) Math.exp(-rate * dt);
		// the exact critically damped step, so the box never overshoots whatever the frame time
		float leftOffset = left - from;
		float leftCarry = leftSpeed + rate * leftOffset;
		left = from + (leftOffset + leftCarry * dt) * decay;
		leftSpeed = (leftSpeed - rate * leftCarry * dt) * decay;
		float rightOffset = right - to;
		float rightCarry = rightSpeed + rate * rightOffset;
		right = to + (rightOffset + rightCarry * dt) * decay;
		rightSpeed = (rightSpeed - rate * rightCarry * dt) * decay;
		oldAlpha = Math.max(0f, oldAlpha - dt * OLD_ROW_FADE);
	}

	int row() {
		return row;
	}

	float left() {
		return left;
	}

	float right() {
		return Math.max(left, right);
	}
}
