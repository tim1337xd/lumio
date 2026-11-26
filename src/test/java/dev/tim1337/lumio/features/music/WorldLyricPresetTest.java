package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The presets of the lyrics in the world. */
class WorldLyricPresetTest {
	@Test
	void theLookOfBeforeIsTheFirstChoice() {
		assertEquals(WorldLyricPreset.CLASSIC, WorldLyricPreset.values()[0], "the look of before is the first choice, and the default");
		assertEquals(2, WorldLyricPreset.values().length, "Classic and Cut-out");
		assertEquals("Cut-out", WorldLyricPreset.CUTOUT.label());
	}
}
