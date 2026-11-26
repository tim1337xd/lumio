package dev.tim1337.lumio.features.music;

/**
 * How strongly a word of the lyrics in the world glows, the same for Cut-out and Classic: with "Off" no glow at all, with "Sung word" only the word being sung (or a whole
 * line lit at once), with "Whole text" every word shown, the sung one a little stronger, and a line already sung keeps a weaker glow as it dims.
 * Plain numbers, so it is unit tested.
 */
final class LyricGlow {
	/** With Whole text, a word not being sung glows this much of the sung word's glow. */
	static final float REST_SHARE = 0.55f;
	/** How much of that a line keeps once it is sung and fully dimmed. */
	static final float DIMMED_KEEPS = 0.5f;

	private LyricGlow() {
	}

	/**
	 * @param whole whether the player chose Whole text
	 * @param sung  how far the word is the one being sung, 0 to 1
	 * @param dim   how far its line is dimmed as one already sung, 0 to 1
	 * @return its glow as a share of the sung word's
	 */
	static float of(boolean whole, float sung, float dim) {
		float rest = whole ? REST_SHARE * (1f - (1f - DIMMED_KEEPS) * Math.clamp(dim, 0f, 1f)) : 0f;
		return Math.max(Math.clamp(sung, 0f, 1f), rest);
	}

	/** @return whether there is a glow pass at all: none with Glow Off, where the text is drawn crisp (its thin shadow stays where needed) */
	static boolean drawn(MusicFeature.WorldGlow mode) {
		return mode != MusicFeature.WorldGlow.OFF;
	}

	/** @return the glow of a word for the chosen Glow, as {@link #of(boolean, float, float)}; 0 with Off */
	static float of(MusicFeature.WorldGlow mode, float sung, float dim) {
		return drawn(mode) ? of(mode == MusicFeature.WorldGlow.WHOLE_TEXT, sung, dim) : 0f;
	}
}
