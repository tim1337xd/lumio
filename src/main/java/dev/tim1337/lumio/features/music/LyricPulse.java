package dev.tim1337.lumio.features.music;

/**
 * The pulse of the word being sung, a part of the Cut-out preset only (Classic shows the sung word by its fill and glow, with no change of size):
 * at the word's start it swells quickly, then eases back over the word's own time, never shorter than a blink and never longer than a long note.
 * Plain logic, tested without the game.
 */
final class LyricPulse {
	/** How strong the pulse is: how much the word grows at its peak, and how much it brightens. */
	static final float GROW = 0.12f;
	static final float LIGHT = 0.75f;

	/** The swell takes this long; the way back as long as the word, within these bounds. */
	static final long ATTACK_MS = 70L;
	static final long LEAST_MS = 240L;
	static final long MOST_MS = 800L;

	private LyricPulse() {
	}

	/**
	 * @param sinceMs  the time since the word started
	 * @param lengthMs how long it is sung
	 * @return the pulse, from 0 to 1: 0 before the start, 1 at the peak right after it, back at 0 when the word's time is over
	 */
	static float at(long sinceMs, long lengthMs) {
		if (sinceMs < 0L) {
			return 0f;
		}
		if (sinceMs < ATTACK_MS) {
			float t = sinceMs / (float) ATTACK_MS;
			return t * t * (3f - 2f * t);
		}
		long back = Math.clamp(lengthMs, LEAST_MS, MOST_MS) - ATTACK_MS;
		float t = (sinceMs - ATTACK_MS) / (float) back;
		if (t >= 1f) {
			return 0f;
		}
		return (1f - t) * (1f - t);
	}
}
