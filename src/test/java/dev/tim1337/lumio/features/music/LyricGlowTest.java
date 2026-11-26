package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Glow choice of the lyrics in the world: none (Off), the sung word alone, or every word with the sung one stronger and sung lines weaker. */
class LyricGlowTest {
	@Test
	void sungWordGlowsOnlyTheWordBeingSung() {
		assertEquals(1f, LyricGlow.of(false, 1f, 0f));
		assertEquals(0f, LyricGlow.of(false, 0f, 0f));
		assertEquals(0f, LyricGlow.of(false, 0f, 1f));
	}

	@Test
	void wholeTextGlowsEveryWordTheSungOneStronger() {
		float rest = LyricGlow.of(true, 0f, 0f);
		assertTrue(rest > 0f, "a word not sung glows");
		assertTrue(LyricGlow.of(true, 1f, 0f) > rest, "the sung word glows more");
		float dimmed = LyricGlow.of(true, 0f, 1f);
		assertTrue(dimmed > 0f && dimmed < rest, "a line already sung keeps a weaker glow");
	}

	@Test
	void offDrawsNoGlowPassAndNoTilePass() {
		assertFalse(LyricGlow.drawn(MusicFeature.WorldGlow.OFF), "no glow pass with Glow Off");
		for (float sung : new float[] {0f, 0.5f, 1f}) {
			for (float dim : new float[] {0f, 1f}) {
				assertEquals(0f, LyricGlow.of(MusicFeature.WorldGlow.OFF, sung, dim));
			}
		}
		assertTrue(LyricGlow.drawn(MusicFeature.WorldGlow.SUNG_WORD) && LyricGlow.drawn(MusicFeature.WorldGlow.WHOLE_TEXT));
		assertEquals(1f, LyricGlow.of(MusicFeature.WorldGlow.SUNG_WORD, 1f, 0f));
		assertFalse(CutoutStamp.boxed(false, true), "no tile pass with the key word box off");
		assertTrue(CutoutStamp.boxed(true, true));
		assertFalse(CutoutStamp.boxed(true, false), "and never one for a word that is not the key word");
	}

	@Test
	void savedGlowChoicesKeepTheirNames() {
		assertEquals(MusicFeature.WorldGlow.SUNG_WORD, MusicFeature.WorldGlow.valueOf("SUNG_WORD"));
		assertEquals(MusicFeature.WorldGlow.WHOLE_TEXT, MusicFeature.WorldGlow.valueOf("WHOLE_TEXT"));
	}
}
