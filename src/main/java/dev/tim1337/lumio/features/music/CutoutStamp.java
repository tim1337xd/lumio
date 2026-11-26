package dev.tim1337.lumio.features.music;

/**
 * The tile of a Cut-out line: it belongs to the line's key word only ({@link CutoutTiles}), never to the word being sung, which shows by its
 * brightness and pulse alone. When the key word is sung its tile stamps in: a little larger and see-through at first, settling in
 * {@link #STAMP_MILLIS} with an ease-out and no bounce. Then it stays for as long as its line is shown, dimming with the line once that is
 * sung, and leaves only with it. Line-synced songs do the same at the key word's estimated time. Plain logic, tested without the game.
 */
final class CutoutStamp {
	/** How long the tile takes to settle once its word is sung, in milliseconds. */
	static final long STAMP_MILLIS = 180L;
	/** How much larger the tile starts. */
	static final float STAMP_GROW = 0.12f;
	/** How strong the tile starts. */
	static final float STAMP_FROM = 0.5f;

	private CutoutStamp() {
	}

	/**
	 * @param now     the time of the lyrics, in milliseconds
	 * @param startMs when the key word is sung
	 * @return how far the tile has stamped in: 0 before its word is sung (no tile yet), then easing out to 1, where it stays
	 */
	static float progress(long now, long startMs) {
		if (now < startMs) {
			return 0f;
		}
		float t = Math.min(1f, (now - startMs) / (float) STAMP_MILLIS);
		float rest = 1f - t;
		return 1f - rest * rest * rest;
	}

	/** @return whether a word has a tile pass at all: only the key word, and only with the key word box on */
	static boolean boxed(boolean keyBox, boolean keyWord) {
		return keyBox && keyWord;
	}

	/** @return whether the tile shows at all at this time: from its word's start on */
	static boolean placed(long now, long startMs) {
		return now >= startMs;
	}

	/** @return how large the tile is drawn: a little larger as it stamps in, then its own size */
	static float scale(float progress) {
		return 1f + STAMP_GROW * (1f - progress);
	}

	/** @return how strong the tile is drawn as it stamps in: a little see-through at first, then whole */
	static float strength(long now, long startMs) {
		return placed(now, startMs) ? STAMP_FROM + (1f - STAMP_FROM) * progress(now, startMs) : 0f;
	}

	/** The letters take the tile's tone once it is this far there, fully a little later, so they never stand light on light. */
	private static final float LETTERS_FROM = 0.3f;
	private static final float LETTERS_TO = 0.8f;

	/** @return how far a word's letters take the tile's tone, for how much of the tile it has */
	static float letters(float amount) {
		return smooth((amount - LETTERS_FROM) / (LETTERS_TO - LETTERS_FROM));
	}

	/** The tile comes with its word's way in over this part of it only, so it is never long half there while the word fades in. */
	private static final float WORD_FROM = 0.15f;
	private static final float WORD_TO = 0.85f;

	/**
	 * @param tile how strong the tile is ({@link #strength}, 0 for a word without one)
	 * @param word how far the word itself is there on its way in and out (0 to 1)
	 * @return how strong the tile is drawn: never stronger than its word, and gone as the word goes with its line
	 */
	static float tile(float tile, float word) {
		return Math.min(tile, smooth((word - WORD_FROM) / (WORD_TO - WORD_FROM)));
	}

	private static float smooth(float t) {
		float x = Math.clamp(t, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
