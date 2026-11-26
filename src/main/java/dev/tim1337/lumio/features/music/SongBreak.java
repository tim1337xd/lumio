package dev.tim1337.lumio.features.music;

/**
 * A clean break in the lyrics in the world: when the song changes, the player seeks, pauses for long or the lyrics are switched off, everything
 * of the old lyrics (lines, the Cut-out column, ad-libs) goes together and quickly, held where it stands (nothing scrolls, slides or finishes its
 * line), with a slight fade and shrink; after it nothing of it is left, and the next line is placed fresh. Plain numbers, so it is unit tested.
 */
final class SongBreak {
	/** How long the old lyrics take to go, in seconds. */
	static final float SECONDS = 0.3f;
	/** How much smaller they are at the end of it. */
	static final float SHRINK = 0.08f;
	/** A pause longer than this, in seconds, is a break. */
	static final float LONG_PAUSE_SECONDS = 5f;

	/** Seconds into the break, or less than 0 while none runs. */
	private float into = -1f;

	/** Starts a break, unless one runs. */
	void start() {
		if (into < 0f) {
			into = 0f;
		}
	}

	/** @return whether a break runs: the old lyrics are going and nothing new comes */
	boolean running() {
		return into >= 0f;
	}

	/**
	 * Moves the break on.
	 *
	 * @return whether it ended with this step: everything of the old lyrics is to be cleared now
	 */
	boolean step(float dt) {
		if (into < 0f) {
			return false;
		}
		into += dt;
		if (into >= SECONDS) {
			into = -1f;
			return true;
		}
		return false;
	}

	/** @return how much of their light the old lyrics keep now: 1 without a break */
	float light() {
		return into < 0f ? 1f : light(into);
	}

	/** @return how large the old lyrics are now: 1 without a break */
	float size() {
		return into < 0f ? 1f : 1f - SHRINK * eased(into);
	}

	/** @return the light the old lyrics keep this far into a break */
	static float light(float seconds) {
		return 1f - eased(seconds);
	}

	private static float eased(float seconds) {
		float t = Math.clamp(seconds / SECONDS, 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
