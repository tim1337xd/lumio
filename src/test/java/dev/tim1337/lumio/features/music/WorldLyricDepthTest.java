package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The lyrics lie over all of the world, except what stands within a few blocks of the camera; before a near wall they are drawn nearer still. */
class WorldLyricDepthTest {
	@Test
	void aFarLineIsDrawnAtTheCoveringDistance() {
		for (double distance : new double[] {8.0, 40.0, 200.0}) {
			assertEquals(WorldLyricDepth.COVER_BLOCKS, distance * WorldLyricDepth.share(distance, WorldLyricDepth.COVER_BLOCKS), 1e-4);
		}
	}

	@Test
	void aNearLineStaysWhereItIs() {
		assertEquals(1f, WorldLyricDepth.share(3.0, WorldLyricDepth.COVER_BLOCKS));
	}

	@Test
	void beforeANearWallABlockIsDrawnBeforeThatWall() {
		// a wall two blocks away, the block three blocks away: drawn at 1.4 blocks, so it lies over the wall
		assertEquals(1.4, 3.0 * WorldLyricDepth.share(3.0, 1.4), 1e-6);
	}
}
