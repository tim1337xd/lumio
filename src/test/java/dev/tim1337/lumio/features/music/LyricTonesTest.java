package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The lyrics' tones read against what lies behind them by contrast alone (WCAG): large words and letters on a tile at least 3:1, small ones 4.5:1 (against
 * the tile for its letters), a tile 2:1 against what lies behind, a line already sung still 3:1; a shadow only where it is needed. Every tone keeps its
 * color's hue and never turns grey.
 */
class LyricTonesTest {
	/** Gold, blue, green, pink, a dark navy, and a nearly grey color. */
	private static final int[] COLORS = {0xE8B040, 0x4A7CF0, 0x45C870, 0xF06AB0, 0x1A2340, 0x8A8F94};
	private static final float DARK = 0.02f;
	private static final float MID = 0.3f;
	private static final float DUSK = 0.2f;
	private static final float BRIGHT = 0.7f;
	private static final float[] BACKGROUNDS = {DARK, MID, BRIGHT};
	/** Rounding to whole channels may cost a hair of contrast. */
	private static final float SLACK = 0.995f;
	/** A word's tone is clearly not grey: at least this saturated (HSV). */
	private static final float NOT_GREY = 0.12f;
	/** Its hue stays within this many degrees of its color's. */
	private static final float SAME_HUE = 16f;

	private static final int N = CutoutWords.Tone.values().length;

	private static float contrast(int a, float behind) {
		return LyricTones.contrast(LyricTones.luminance(a), behind);
	}

	private static float contrast(int a, int b) {
		return LyricTones.contrast(LyricTones.luminance(a), LyricTones.luminance(b));
	}

	private static int tone(int[] tones, CutoutWords.Tone tone) {
		return tones[tone.ordinal()];
	}

	private static float shadow(int[] tones) {
		return (tone(tones, CutoutWords.Tone.SHADOW) >>> 24) / 255f;
	}

	private static float hueApart(int a, int b) {
		float apart = Math.abs(LyricTones.hue(a) - LyricTones.hue(b)) % 360f;
		return Math.min(apart, 360f - apart);
	}

	@Test
	void freeWordsReadAgainstWhatLiesBehind() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			for (int step = 0; step <= 20; step++) {
				float behind = step / 20f;
				CutoutWords.tones(color, 0xFF000000 | COLORS[(step + 1) % COLORS.length], behind, 0f, tones, dimmed);
				// with a shadow under them, the words are read against what lies behind darkened by it
				float shaded = LyricTones.shaded(behind, shadow(tones));
				String name = Integer.toHexString(color) + " before " + behind;
				for (CutoutWords.Tone free : new CutoutWords.Tone[] {CutoutWords.Tone.WORD, CutoutWords.Tone.SMALL, CutoutWords.Tone.SYMBOL, CutoutWords.Tone.SUNG}) {
					assertTrue(contrast(tone(tones, free), shaded) >= LyricTones.LARGE_CONTRAST * SLACK,
						free + " of " + name + ": " + contrast(tone(tones, free), shaded));
				}
				// where the word alone does not reach it, the shadow is there
				boolean alone = contrast(tone(tones, CutoutWords.Tone.WORD), behind) >= LyricTones.LARGE_CONTRAST * SLACK;
				assertTrue(alone || shadow(tones) > 0f, "a shadow where the word needs one: " + name);
			}
		}
	}

	@Test
	void theShadowComesOnlyWhereItIsNeeded() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			CutoutWords.tones(color, color, DARK, 0f, tones, dimmed);
			assertEquals(0f, shadow(tones), "none before the night: " + Integer.toHexString(color));
			CutoutWords.tones(color, color, BRIGHT, 0f, tones, dimmed);
			assertEquals(0f, shadow(tones), "none before the bright day: " + Integer.toHexString(color));
		}
		// gold before a dim middle background (dusk): neither its tint nor a deep gold that still has color reads well enough, so the tint gets a shadow
		CutoutWords.tones(0xE8B040, 0xE8B040, DUSK, 0f, tones, dimmed);
		assertTrue(shadow(tones) > 0f, "a shadow for gold at dusk");
		assertTrue(LyricTones.light(0xFFE8B040, DUSK), "on a tint");
		assertTrue(shadow(tones) <= LyricTones.SHADOW_MOST);
	}

	@Test
	void tilesStandOutAndTheirLettersReadOnThem() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			for (int step = 0; step <= 100; step++) {
				float behind = step / 100f;
				CutoutWords.tones(color, color, behind, 0f, tones, dimmed);
				String name = Integer.toHexString(color) + " before " + behind;
				for (CutoutWords.Tone[] tile : new CutoutWords.Tone[][] {{CutoutWords.Tone.DARK_TILE, CutoutWords.Tone.ON_DARK},
					{CutoutWords.Tone.PALE_TILE, CutoutWords.Tone.ON_PALE}}) {
					int face = tone(tones, tile[0]);
					int letters = tone(tones, tile[1]);
					assertTrue(contrast(face, behind) >= LyricTones.TILE_CONTRAST * SLACK, tile[0] + " against what lies behind, " + name + ": " + contrast(face, behind));
					assertTrue(contrast(letters, face) >= LyricTones.LARGE_CONTRAST * SLACK, tile[1] + " on it, " + name + ": " + contrast(letters, face));
					// the letters on both kinds of tile are white or nearly so
					assertTrue(LyricTones.luminance(letters) > 0.75f, "light letters on " + tile[0] + ", " + name);
				}
			}
		}
	}

	@Test
	void aLineAlreadySungDimsButStillReads() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			for (float behind : BACKGROUNDS) {
				CutoutWords.tones(color, color, behind, 0f, tones, dimmed);
				float shaded = LyricTones.shaded(behind, shadow(tones));
				String name = Integer.toHexString(color) + " before " + behind;
				for (CutoutWords.Tone free : new CutoutWords.Tone[] {CutoutWords.Tone.WORD, CutoutWords.Tone.SMALL, CutoutWords.Tone.SYMBOL}) {
					float full = contrast(tone(tones, free), shaded);
					float dim = contrast(tone(dimmed, free), shaded);
					assertTrue(dim >= LyricTones.SUNG_CONTRAST * SLACK, free + " dimmed, " + name + ": " + dim);
					assertTrue(dim <= full + 0.05f, free + " dims toward what lies behind, " + name);
					// dimmed by its brightness only: the same hue
					assertTrue(hueApart(tone(dimmed, free), tone(tones, free)) <= SAME_HUE, free + " keeps its hue dimmed, " + name);
				}
				for (CutoutWords.Tone[] tile : new CutoutWords.Tone[][] {{CutoutWords.Tone.DARK_TILE, CutoutWords.Tone.ON_DARK},
					{CutoutWords.Tone.PALE_TILE, CutoutWords.Tone.ON_PALE}}) {
					assertTrue(contrast(tone(dimmed, tile[1]), tone(dimmed, tile[0])) >= LyricTones.SUNG_CONTRAST * SLACK, "letters on a dimmed " + tile[0] + ", " + name);
				}
			}
		}
	}

	@Test
	void theTonesKeepTheirColorAndNeverTurnGrey() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			for (float behind : BACKGROUNDS) {
				CutoutWords.tones(color, color, behind, 0f, tones, dimmed);
				String name = Integer.toHexString(color) + " before " + behind;
				for (CutoutWords.Tone free : new CutoutWords.Tone[] {CutoutWords.Tone.WORD, CutoutWords.Tone.SMALL, CutoutWords.Tone.DARK_TILE, CutoutWords.Tone.PALE_TILE}) {
					int each = tone(tones, free);
					assertTrue(LyricTones.saturation(each) >= NOT_GREY, free + " is no grey, " + name + ": " + Integer.toHexString(each));
					assertTrue(hueApart(each, 0xFF000000 | color) <= SAME_HUE, free + " keeps the hue, " + name + ": " + Integer.toHexString(each));
					assertTrue(LyricTones.saturation(tone(dimmed, free)) >= NOT_GREY, free + " dimmed is no grey, " + name);
				}
			}
		}
	}

	@Test
	void lightWordsBeforeTheDarkDeepOnesBeforeTheBright() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			CutoutWords.tones(color, color, DARK, 0f, tones, dimmed);
			int night = tone(tones, CutoutWords.Tone.WORD);
			assertTrue(LyricTones.luminance(night) > 0.4f, "a light tint by night: " + Integer.toHexString(night));
			assertTrue(night != LyricTones.WHITE, "never pure white");
			assertEquals(LyricTones.WHITE, tone(tones, CutoutWords.Tone.SUNG), "the word being sung is white by night");
			assertTrue((tone(tones, CutoutWords.Tone.GLOW) >>> 24) > 0, "and glows");
			CutoutWords.tones(color, color, BRIGHT, 0f, tones, dimmed);
			int day = tone(tones, CutoutWords.Tone.WORD);
			assertTrue(LyricTones.luminance(day) <= LyricTones.DEEP_MOST_LUMINANCE + 0.01f, "a deep tone before the bright: " + Integer.toHexString(day));
			assertTrue(LyricTones.saturation(day) >= LyricTones.DEEP_LEAST_SATURATION - 0.05f, "and saturated: " + Integer.toHexString(day));
			assertEquals(0, tone(tones, CutoutWords.Tone.GLOW) >>> 24, "no light glow to wash it out");
		}
	}

	@Test
	void aBusyWorldBehindGetsAShadow() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		CutoutWords.tones(0x4A7CF0, 0x4A7CF0, DARK, 0f, tones, dimmed);
		assertEquals(0f, shadow(tones));
		CutoutWords.tones(0x4A7CF0, 0x4A7CF0, DARK, 0.3f, tones, dimmed);
		assertTrue(shadow(tones) > 0f, "busy surfaces behind light words");
	}

	@Test
	void theClassicLineReadsToo() {
		int[] tones = new int[LyricTones.Classic.values().length];
		int[] dimmed = new int[LyricTones.Classic.values().length];
		for (int color : COLORS) {
			for (int text : new int[] {0xFFFFFF, 0xF0E0C0, color}) {
				for (float behind : BACKGROUNDS) {
					LyricTones.classic(text, color, 0.55f, behind, 0f, tones, dimmed);
					float shadow = (tones[LyricTones.Classic.SHADOW.ordinal()] >>> 24) / 255f;
					float shaded = LyricTones.shaded(behind, shadow);
					String name = Integer.toHexString(color) + "/" + Integer.toHexString(text) + " before " + behind;
					int sung = tones[LyricTones.Classic.SUNG.ordinal()];
					assertTrue(contrast(sung, shaded) >= LyricTones.LARGE_CONTRAST * SLACK, "the sung part of " + name + ": " + contrast(sung, shaded));
					for (LyricTones.Classic part : new LyricTones.Classic[] {LyricTones.Classic.REST, LyricTones.Classic.TINTED}) {
						assertTrue(contrast(tones[part.ordinal()], shaded) >= LyricTones.SUNG_CONTRAST * SLACK, part + " of " + name);
						assertTrue(contrast(dimmed[part.ordinal()], shaded) >= LyricTones.SUNG_CONTRAST * SLACK, part + " dimmed, of " + name);
					}
					assertTrue(contrast(dimmed[LyricTones.Classic.SUNG.ordinal()], shaded) >= LyricTones.SUNG_CONTRAST * SLACK, "an older line of " + name);
				}
				// before the bright, the text is the deep line color, and no light glow washes it out
				LyricTones.classic(text, color, 0.55f, BRIGHT, 0f, tones, dimmed);
				int sung = tones[LyricTones.Classic.SUNG.ordinal()];
				assertTrue(LyricTones.luminance(sung) <= LyricTones.DEEP_MOST_LUMINANCE + 0.01f && LyricTones.saturation(sung) >= NOT_GREY, "deep before the bright: " + Integer.toHexString(sung));
				assertTrue(hueApart(sung, 0xFF000000 | color) <= SAME_HUE, "in the line's hue");
				assertEquals(0, tones[LyricTones.Classic.GLOW.ordinal()] >>> 24);
			}
		}
	}

	@Test
	void fittingKeepsTheHue() {
		for (int color : COLORS) {
			int vivid = LyricTones.hsv(LyricTones.hue(0xFF000000 | color), 0.8f, 1f);
			for (float target : new float[] {0.01f, 0.05f, 0.2f, 0.5f, 0.8f}) {
				int fitted = LyricTones.fit(vivid, target);
				assertEquals(target, LyricTones.luminance(fitted), 0.02f, "fitted to its luminance: " + Integer.toHexString(color));
				assertTrue(hueApart(fitted, vivid) <= SAME_HUE, "same hue: " + Integer.toHexString(fitted));
			}
		}
		assertFalse(LyricTones.light(0xE8B040, BRIGHT), "deep words before the bright");
		assertTrue(LyricTones.light(0xE8B040, DARK), "light words before the dark");
	}

	@Test
	void aMixedBackgroundIsReadAgainstAllOfIt() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		// dark leaves below, the bright day sky above: deep words would be lost on the leaves, so they are light, with a shadow for the sky
		for (int color : COLORS) {
			float darkest = 0.05f;
			float brightest = 0.45f;
			CutoutWords.tones(color, color, darkest, brightest, 0f, LyricTones.LARGE_CONTRAST, LyricTones.LARGE_CONTRAST, tones, dimmed);
			String name = Integer.toHexString(color);
			int word = tone(tones, CutoutWords.Tone.WORD);
			assertTrue(LyricTones.luminance(word) > 0.4f, "light words before leaves and sky: " + name);
			float shadow = shadow(tones);
			assertTrue(shadow > 0f, "with a shadow: " + name);
			assertTrue(contrast(word, LyricTones.shaded(darkest, shadow)) >= LyricTones.LARGE_CONTRAST * SLACK, "against the leaves: " + name);
		}
		// the bright sky above the bright sand: deep words, read against the darker of the two
		CutoutWords.tones(0x4A7CF0, 0x4A7CF0, 0.45f, 0.7f, 0f, LyricTones.LARGE_CONTRAST, LyricTones.LARGE_CONTRAST, tones, dimmed);
		assertTrue(contrast(tone(tones, CutoutWords.Tone.WORD), 0.45f) >= LyricTones.LARGE_CONTRAST * SLACK);
		assertTrue(contrast(tone(tones, CutoutWords.Tone.WORD), 0.7f) >= LyricTones.LARGE_CONTRAST * SLACK);
	}

	@Test
	void theContrastFollowsTheSizeOnTheScreen() {
		assertEquals(LyricTones.SMALL_CONTRAST, LyricTones.contrastFor(12f), 1e-6f);
		assertEquals(LyricTones.SMALL_CONTRAST, LyricTones.contrastFor(LyricTones.SMALL_BELOW_PIXELS), 1e-6f);
		assertEquals(LyricTones.LARGE_CONTRAST, LyricTones.contrastFor(LyricTones.LARGE_FROM_PIXELS), 1e-6f);
		assertEquals(LyricTones.LARGE_CONTRAST, LyricTones.contrastFor(80f), 1e-6f);
		float between = LyricTones.contrastFor(22f);
		assertTrue(between > LyricTones.LARGE_CONTRAST && between < LyricTones.SMALL_CONTRAST, "no jump between the two");
	}

	@Test
	void smallWordsAndTheirLettersReadAtTheHigherContrast() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			for (int step = 0; step <= 20; step++) {
				float behind = step / 20f;
				CutoutWords.tones(color, color, behind, behind, 0f, LyricTones.SMALL_CONTRAST, LyricTones.SMALL_CONTRAST, tones, dimmed);
				String name = Integer.toHexString(color) + " before " + behind;
				float shaded = LyricTones.shaded(behind, shadow(tones));
				assertTrue(contrast(tone(tones, CutoutWords.Tone.SMALL), shaded) >= LyricTones.SMALL_CONTRAST * SLACK, "small word, " + name);
				assertTrue(contrast(tone(tones, CutoutWords.Tone.ON_DARK), tone(tones, CutoutWords.Tone.DARK_TILE)) >= LyricTones.SMALL_CONTRAST * SLACK, "letters, " + name);
			}
		}
	}

	@Test
	void goldBeforeTheBrightSkyIsARichOrangeGold() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		CutoutWords.tones(0xE8B040, 0xE8B040, 0.38f, 0f, tones, dimmed);
		int word = tone(tones, CutoutWords.Tone.WORD);
		assertTrue(LyricTones.saturation(word) >= 0.9f, "saturated: " + Integer.toHexString(word));
		assertTrue(LyricTones.hue(word) < 33f, "turned toward orange, no olive: " + LyricTones.hue(word));
		assertTrue((word >> 16 & 0xFF) >= 130, "as light as 3:1 allows: " + Integer.toHexString(word));
		assertTrue(LyricTones.luminance(tone(tones, CutoutWords.Tone.SUNG)) < LyricTones.luminance(word), "the sung word is deeper");
		assertTrue(LyricTones.saturation(tone(dimmed, CutoutWords.Tone.WORD)) < LyricTones.saturation(word), "a line already sung recedes though it cannot get lighter");
	}

	@Test
	void freeWordsByNightAreACleanTintAndTilesGlowUnderWhiteLetters() {
		int[] tones = new int[N];
		int[] dimmed = new int[N];
		for (int color : COLORS) {
			CutoutWords.tones(color, color, DARK, 0f, tones, dimmed);
			String name = Integer.toHexString(color);
			assertTrue(LyricTones.saturation(tone(tones, CutoutWords.Tone.WORD)) >= 0.3f, "not greyish: " + name);
			assertEquals(LyricTones.WHITE, tone(tones, CutoutWords.Tone.ON_DARK), "white letters: " + name);
			assertTrue(LyricTones.luminance(tone(tones, CutoutWords.Tone.PALE_TILE)) > 0.2f, "a tile that glows: " + name);
		}
	}
}
