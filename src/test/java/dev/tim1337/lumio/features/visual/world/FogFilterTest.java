package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The numbers of the Visual Filter's fog switches. */
class FogFilterTest {
	private static final float RENDER_DISTANCE = 192f;
	private static final float OVERWORLD_END = 1024f;
	private static final float NETHER_END = 96f;

	@Test
	void theNethersFogAndABossFightsAreThickTheOverworldsHazeIsNot() {
		assertTrue(FogFilter.thick(NETHER_END, RENDER_DISTANCE, false));
		assertTrue(FogFilter.thick(OVERWORLD_END, RENDER_DISTANCE, true));
		assertFalse(FogFilter.thick(OVERWORLD_END, RENDER_DISTANCE, false));
	}

	@Test
	void theEdgeFadesWhereTheGameFadesItOrOverTheLastBlocks() {
		assertEquals(RENDER_DISTANCE - 19.2f, FogFilter.edgeStart(RENDER_DISTANCE, false), 0.001f);
		assertEquals(RENDER_DISTANCE - FogFilter.CLEAR_EDGE_BLOCKS, FogFilter.edgeStart(RENDER_DISTANCE, true), 0.001f);
		// a very short render distance: never a wider fade than the game's own
		assertEquals(32f - 4f, FogFilter.edgeStart(32f, true), 0.001f);
		assertTrue(FogFilter.edgeStart(RENDER_DISTANCE, true) < RENDER_DISTANCE, "the land is never cut off");
	}

	@Test
	void rainStaysInTheFog() {
		assertEquals(0f, FogFilter.weather(OVERWORLD_END, OVERWORLD_END));
		assertEquals(0.5f, FogFilter.weather(OVERWORLD_END, OVERWORLD_END - 128f), 0.001f);
		assertEquals(1f, FogFilter.weather(OVERWORLD_END, OVERWORLD_END - 256f));
		// no rain: the fog is gone up to the edge; full rain: the game's own value
		assertEquals(RENDER_DISTANCE, FogFilter.cleared(OVERWORLD_END, RENDER_DISTANCE, 0f));
		assertEquals(768f, FogFilter.cleared(768f, RENDER_DISTANCE, 1f));
	}
}
