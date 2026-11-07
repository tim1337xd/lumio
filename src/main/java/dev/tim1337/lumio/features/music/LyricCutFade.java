package dev.tim1337.lumio.features.music;

/**
 * How visible the lyrics and the song card are around a cut of the cinematic camera: they fade out in the last moment before a cut that was foreseen, are gone at the cut itself, and fade in at their new place in the new shot. Plain numbers, so it
 * is unit tested.
 */
public final class LyricCutFade {
	/** The fade before a foreseen cut, and the fade in after any cut. */
	public static final long OUT_MS = 150L;
	public static final long IN_MS = 200L;
	/** No cut foreseen, or none so far. */
	public static final long NONE = -1L;

	private LyricCutFade() {
	}

	/**
	 * @param untilCutMs how long until a foreseen cut, or {@link #NONE}: a hard cut comes without one
	 * @param sinceCutMs how long since the last cut, or {@link #NONE}
	 * @return how visible the lyrics are, from 0 to 1
	 */
	public static float alpha(long untilCutMs, long sinceCutMs) {
		float out = untilCutMs < 0L ? 1f : Math.min(1f, (float) untilCutMs / OUT_MS);
		float in = sinceCutMs < 0L ? 1f : Math.min(1f, (float) sinceCutMs / IN_MS);
		return Math.min(out, in);
	}

	/** @return whether a line that starts now starts with the cut's fade in, and not with a fade of its own on top of it */
	public static boolean inCutFade(long sinceCutMs) {
		return sinceCutMs >= 0L && sinceCutMs < IN_MS;
	}
}
