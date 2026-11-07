package dev.tim1337.lumio.features.music;

/**
 * The next lines the info bar shows after the sung one, wherever there is room: in the same row, each after a small quiet dot, the farther ahead the
 * dimmer, never lit or boxed. Only whole words are shown: a word that does not fit is left out, and with it the rest of its line; when the next one
 * starts it moves into place as the sung line in one motion (see {@link BarLyrics}). Plain numbers and text, so it is unit tested.
 */
final class NextLinePreview {
	/** The most lines shown ahead, however wide the segment. */
	static final int MOST_LINES = 8;
	/** How bright the first line ahead is, and how much of that each next one keeps. */
	static final float ALPHA = 0.42f;
	static final float FALLOFF = 0.68f;
	/** The gap before a line, with the dot in its middle, in GUI pixels; the dot's size, and how bright it is against its line. */
	static final float GAP = 14f;
	static final float DOT = 2f;
	static final float DOT_SHARE = 0.75f;

	private NextLinePreview() {
	}

	/** @return how bright a line ahead is: 0 for the next one, 1 for the one after, ... */
	static float alpha(int place) {
		float alpha = ALPHA;
		for (int i = 0; i < place; i++) {
			alpha *= FALLOFF;
		}
		return alpha;
	}

	/**
	 * @param text   the line
	 * @param glyphX where each character of the line starts, with one more entry for its end
	 * @param count  how many characters it has
	 * @param room   the room for it
	 * @return how many of its characters are shown: all of them when it fits, up to the end of the last word that fits, or 0 when not even the first word does
	 */
	static int fit(CharSequence text, float[] glyphX, int count, float room) {
		if (glyphX[count] <= room) {
			return count;
		}
		int fits = 0;
		for (int i = 1; i <= count; i++) {
			if (glyphX[i] > room) {
				break;
			}
			boolean wordEnds = i == count || Character.isWhitespace(text.charAt(i)) && !Character.isWhitespace(text.charAt(i - 1));
			if (wordEnds) {
				fits = i;
			}
		}
		return fits;
	}
}
