package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Molten sky's colors: a dark sky in it behind them, and the world's light turned towards it. */
class SkyMoltenTest {
	@Test
	void theSkyBehindIsDarkInTheLinesColorAndTheTopDarkerThanTheHorizon() {
		float[] horizon = SkyMolten.horizon(SkyMolten.GOLD_RGB);
		float[] zenith = SkyMolten.zenith(SkyMolten.GOLD_RGB);
		for (int i = 0; i < 3; i++) {
			assertTrue(horizon[i] <= SkyMolten.HORIZON_SHARE + 1e-6f, "dark");
			assertTrue(horizon[i] >= 0.4f * SkyMolten.HORIZON_SHARE, "of low saturation: no channel near black while another is lit");
			assertTrue(zenith[i] <= horizon[i], "darker at the top");
		}
		assertTrue(horizon[0] > horizon[2], "gold stays warm");
	}

	@Test
	void theWorldsLightTurnsTowardsTheColorAndStaysAsBright() {
		float[] daylight = {1f, 1f, 1f};
		assertArrayEquals(daylight, SkyMolten.tintedLight(daylight, SkyMolten.GOLD_RGB, 0f), 1e-6f, "no tint, no change");
		float[] tinted = SkyMolten.tintedLight(daylight, SkyMolten.GOLD_RGB, SkyMolten.WORLD_TINT);
		assertEquals(1f, Math.max(tinted[0], Math.max(tinted[1], tinted[2])), 1e-5f, "as bright as before");
		assertTrue(tinted[0] > tinted[2], "warmer");
		float[] night = {0.1f, 0.1f, 0.2f};
		float[] nightTinted = SkyMolten.tintedLight(night, 0x48B4FF, 1f);
		assertEquals(0.2f, Math.max(nightTinted[0], Math.max(nightTinted[1], nightTinted[2])), 1e-5f);
	}
}
