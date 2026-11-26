package dev.tim1337.lumio.features.music;

/**
 * What lies behind a line of the lyrics in the world, as the line's tones are chosen by it ({@link LyricTones}): how light its darkest and its
 * brightest part are, how busy world surfaces there are, and the contrast the line's size on the screen asks for. It is taken when the line starts
 * and followed a few times a second while the line stands ({@link #follow}): a small change is ignored, so nothing flickers on a border between two
 * tones, and a real one is blended in over {@link #BLEND_SECONDS}. The tones of both ends are worked out once and kept, so a frame only mixes them.
 * Plain logic, tested without the game.
 */
final class LyricBackdrop {
	/** A new background is blended in over this time. */
	static final float BLEND_SECONDS = 0.5f;
	/** What lies behind a line that was never measured: a dark sky. */
	static final float UNKNOWN_BEHIND = 0.03f;
	/** While a line stands, its background counts as changed from this ratio of contrast on, or from these differences. */
	static final float FOLLOW_RATIO = 1.15f;
	private static final float FOLLOW_BUSY = 0.08f;
	private static final float FOLLOW_CONTRAST = 0.2f;
	/** WCAG's flare, added to a luminance to compare two of them as the eye does. */
	private static final float FLARE = 0.05f;

	/** One background: its darkest and brightest part, how busy its surfaces are, and the contrast the words and the small words need. */
	private static final class Behind {
		float darkest = UNKNOWN_BEHIND;
		float brightest = UNKNOWN_BEHIND;
		float busy;
		float wordContrast = LyricTones.LARGE_CONTRAST;
		float smallContrast = LyricTones.LARGE_CONTRAST;

		void set(float darkest, float brightest, float busy, float wordContrast, float smallContrast) {
			this.darkest = darkest;
			this.brightest = brightest;
			this.busy = busy;
			this.wordContrast = wordContrast;
			this.smallContrast = smallContrast;
		}

		void set(Behind other) {
			set(other.darkest, other.brightest, other.busy, other.wordContrast, other.smallContrast);
		}

		void between(Behind a, Behind b, float t) {
			set(a.darkest + (b.darkest - a.darkest) * t, a.brightest + (b.brightest - a.brightest) * t, a.busy + (b.busy - a.busy) * t,
				a.wordContrast + (b.wordContrast - a.wordContrast) * t, a.smallContrast + (b.smallContrast - a.smallContrast) * t);
		}
	}

	private static boolean apart(float a, float b) {
		float ratio = (a + FLARE) / (b + FLARE);
		return ratio > FOLLOW_RATIO || ratio < 1f / FOLLOW_RATIO;
	}

	private boolean known;
	private final Behind from = new Behind();
	private final Behind to = new Behind();
	private float blend = 1f;

	/** The tones of both backgrounds, made for these colors, and whether they are made. */
	private final int[] cutoutFrom = new int[CutoutWords.Tone.values().length];
	private final int[] cutoutFromDimmed = new int[CutoutWords.Tone.values().length];
	private final int[] cutoutTo = new int[CutoutWords.Tone.values().length];
	private final int[] cutoutToDimmed = new int[CutoutWords.Tone.values().length];
	private long cutoutKey;
	private boolean cutoutFromMade;
	private boolean cutoutToMade;
	private final int[] classicFrom = new int[LyricTones.Classic.values().length];
	private final int[] classicFromDimmed = new int[LyricTones.Classic.values().length];
	private final int[] classicTo = new int[LyricTones.Classic.values().length];
	private final int[] classicToDimmed = new int[LyricTones.Classic.values().length];
	private long classicKey;
	private float classicTinted = Float.NaN;
	private boolean classicFromMade;
	private boolean classicToMade;

	/** Forgets what was behind, as for a new line. */
	void reset() {
		known = false;
		from.set(UNKNOWN_BEHIND, UNKNOWN_BEHIND, 0f, LyricTones.LARGE_CONTRAST, LyricTones.LARGE_CONTRAST);
		to.set(from);
		blend = 1f;
		forget();
	}

	private void forget() {
		cutoutFromMade = false;
		cutoutToMade = false;
		classicFromMade = false;
		classicToMade = false;
	}

	/** Takes what lies behind a line of large words. */
	void take(float darkest, float brightest, float busy, boolean animated) {
		take(darkest, brightest, busy, LyricTones.LARGE_CONTRAST, LyricTones.LARGE_CONTRAST, animated);
	}

	/**
	 * Takes what lies behind the line now.
	 *
	 * @param darkest       how light its darkest part is (relative luminance, 0 to 1)
	 * @param brightest     how light its brightest part is
	 * @param busy          how much world surfaces there vary (the sky never counts)
	 * @param wordContrast  the contrast its words need at their size on the screen
	 * @param smallContrast the same for its small words
	 * @param animated      whether it is blended in; else it is there at once
	 */
	void take(float darkest, float brightest, float busy, float wordContrast, float smallContrast, boolean animated) {
		if (!known || !animated) {
			known = true;
			to.set(darkest, brightest, busy, wordContrast, smallContrast);
			from.set(to);
			blend = 1f;
			forget();
			return;
		}
		// what is shown now is where the new one comes from, also halfway through a blend: no tone jumps
		float t = eased();
		boolean cutoutShown = cutoutFromMade && cutoutToMade;
		boolean classicShown = classicFromMade && classicToMade;
		if (cutoutShown) {
			mix(cutoutFrom, cutoutTo, cutoutFrom);
			mix(cutoutFromDimmed, cutoutToDimmed, cutoutFromDimmed);
		}
		if (classicShown) {
			mix(classicFrom, classicTo, classicFrom);
			mix(classicFromDimmed, classicToDimmed, classicFromDimmed);
		}
		from.between(from, to, t);
		cutoutFromMade = cutoutShown;
		classicFromMade = classicShown;
		to.set(darkest, brightest, busy, wordContrast, smallContrast);
		cutoutToMade = false;
		classicToMade = false;
		blend = 0f;
	}

	/**
	 * Follows what lies behind a standing line: takes it only if it differs clearly from what the line is going to. As each change has to be that
	 * large from the last one taken, a value wavering around a border between two tones never makes them flip back and forth.
	 *
	 * @return whether it was taken
	 */
	boolean follow(float darkest, float brightest, float busy, float wordContrast, float smallContrast, boolean animated) {
		if (known && !apart(darkest, to.darkest) && !apart(brightest, to.brightest) && Math.abs(busy - to.busy) < FOLLOW_BUSY
			&& Math.abs(wordContrast - to.wordContrast) < FOLLOW_CONTRAST && Math.abs(smallContrast - to.smallContrast) < FOLLOW_CONTRAST) {
			return false;
		}
		take(darkest, brightest, busy, wordContrast, smallContrast, animated);
		return true;
	}

	/** Takes another line's background as it is, blend and tones and all: the lines of one column stand before the same. */
	void copy(LyricBackdrop other) {
		known = other.known;
		from.set(other.from);
		to.set(other.to);
		blend = other.blend;
		cutoutKey = other.cutoutKey;
		cutoutFromMade = other.cutoutFromMade;
		cutoutToMade = other.cutoutToMade;
		classicKey = other.classicKey;
		classicTinted = other.classicTinted;
		classicFromMade = other.classicFromMade;
		classicToMade = other.classicToMade;
		copy(other.cutoutFrom, cutoutFrom);
		copy(other.cutoutFromDimmed, cutoutFromDimmed);
		copy(other.cutoutTo, cutoutTo);
		copy(other.cutoutToDimmed, cutoutToDimmed);
		copy(other.classicFrom, classicFrom);
		copy(other.classicFromDimmed, classicFromDimmed);
		copy(other.classicTo, classicTo);
		copy(other.classicToDimmed, classicToDimmed);
	}

	private static void copy(int[] source, int[] target) {
		System.arraycopy(source, 0, target, 0, target.length);
	}

	/** Moves the blend on by a frame's time. */
	void step(float seconds) {
		blend = Math.min(1f, blend + seconds / BLEND_SECONDS);
	}

	/** @return whether the background was measured */
	boolean known() {
		return known;
	}

	/** @return how light the darkest part of what lies behind the line is now, between the old and the new */
	float darkest() {
		return from.darkest + (to.darkest - from.darkest) * eased();
	}

	private float eased() {
		return blend * blend * (3f - 2f * blend);
	}

	/** Writes the tones of a Cut-out line ({@link CutoutWords#tones}), and those of it dimmed, as the background is blended now. */
	void cutout(int first, int second, int[] out, int[] dimmed) {
		long key = (long) first << 32 | second & 0xFFFFFFFFL;
		if (key != cutoutKey) {
			cutoutKey = key;
			cutoutFromMade = false;
			cutoutToMade = false;
		}
		if (!cutoutToMade) {
			CutoutWords.tones(first, second, to.darkest, to.brightest, to.busy, to.wordContrast, to.smallContrast, cutoutTo, cutoutToDimmed);
			cutoutToMade = true;
		}
		if (!cutoutFromMade) {
			CutoutWords.tones(first, second, from.darkest, from.brightest, from.busy, from.wordContrast, from.smallContrast, cutoutFrom, cutoutFromDimmed);
			cutoutFromMade = true;
		}
		mix(cutoutFrom, cutoutTo, out);
		mix(cutoutFromDimmed, cutoutToDimmed, dimmed);
	}

	/** Writes the tones of a Classic line ({@link LyricTones#classic}), and those of it dimmed, as the background is blended now. */
	void classic(int text, int color, float tinted, int[] out, int[] dimmed) {
		long key = (long) text << 32 | color & 0xFFFFFFFFL;
		if (key != classicKey || tinted != classicTinted) {
			classicKey = key;
			classicTinted = tinted;
			classicFromMade = false;
			classicToMade = false;
		}
		if (!classicToMade) {
			LyricTones.classic(text, color, tinted, to.darkest, to.brightest, to.busy, to.wordContrast, classicTo, classicToDimmed);
			classicToMade = true;
		}
		if (!classicFromMade) {
			LyricTones.classic(text, color, tinted, from.darkest, from.brightest, from.busy, from.wordContrast, classicFrom, classicFromDimmed);
			classicFromMade = true;
		}
		mix(classicFrom, classicTo, out);
		mix(classicFromDimmed, classicToDimmed, dimmed);
	}

	private void mix(int[] older, int[] newer, int[] out) {
		float t = eased();
		for (int i = 0; i < out.length; i++) {
			out[i] = t >= 1f ? newer[i] : LyricTones.mix(older[i], newer[i], t);
		}
	}
}
