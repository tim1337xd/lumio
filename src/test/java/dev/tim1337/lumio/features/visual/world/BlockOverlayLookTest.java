package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.visual.world.BlockOverlayLook.Colors;

import org.junit.jupiter.api.Test;

/** What moves on the block overlay. */
class BlockOverlayLookTest {
	@Test
	void colorsGoOnAroundTheCorners() {
		float steps = 720f;
		for (int i = 1; i <= steps; i++) {
			double angle = i / steps * 2.0 * Math.PI;
			double before = (i - 1) / steps * 2.0 * Math.PI;
			float place = BlockOverlayLook.place(0.5f + (float) Math.sin(angle), 0f, 0.5f + (float) Math.cos(angle));
			float last = BlockOverlayLook.place(0.5f + (float) Math.sin(before), 0f, 0.5f + (float) Math.cos(before));
			float apart = Math.abs(place - last);
			assertTrue(Math.min(apart, 1f - apart) < 0.01f, "no jump at step " + i);
		}
	}

	@Test
	void rainbowFlowsAndACustomColorStaysItself() {
		assertNotEquals(BlockOverlayLook.color(Colors.RAINBOW, 0, 0f, 0f, 1f), BlockOverlayLook.color(Colors.RAINBOW, 0, 0f, 1f, 1f));
		assertNotEquals(BlockOverlayLook.color(Colors.RAINBOW, 0, 0f, 0f, 1f), BlockOverlayLook.color(Colors.RAINBOW, 0, 0.3f, 0f, 1f));
		assertEquals(0xFFFFFF, BlockOverlayLook.color(Colors.CUSTOM, 0xFFFFFF, 0.3f, 2f, 1f), "white stays white");
		int shade = BlockOverlayLook.color(Colors.ACCENT, 0x0A84FF, 0f, 0f, 1f);
		assertTrue((shade & 255) >= 0xFF - 1, "a shade of it, only toward white");
	}

	@Test
	void aPulseGrowsAndFadesToNothing() {
		assertEquals(1f, BlockOverlayLook.pulseAlpha(0L));
		assertEquals(0f, BlockOverlayLook.pulseAlpha(BlockOverlayLook.PULSE_MILLIS));
		assertEquals(0f, BlockOverlayLook.pulseGrow(0L));
		assertTrue(BlockOverlayLook.pulseGrow(BlockOverlayLook.PULSE_MILLIS / 2) > 0f);
		assertTrue(BlockOverlayLook.pulseAlpha(30L) > BlockOverlayLook.pulseAlpha(200L));
	}

	@Test
	void breakingTakesWhicheverSaysMore() {
		assertEquals(0.5f, BlockOverlayLook.breaking(0.5f, -1));
		assertEquals(0.5f, BlockOverlayLook.breaking(0f, 4), "the server's stage, as on servers with their own mining");
		assertEquals(0f, BlockOverlayLook.breaking(0f, -1));
		assertEquals(0f, BlockOverlayLook.breaking(0f, 10), "a stage out of range is none");
		assertEquals(1f, BlockOverlayLook.breaking(2f, 9));
	}

	@Test
	void fadesTakeTheirTimeBothWays() {
		float in = BlockOverlayLook.fade(0f, true, BlockOverlayLook.FADE_SECONDS / 2f);
		assertEquals(0.5f, in, 0.001f);
		assertEquals(1f, BlockOverlayLook.fade(in, true, BlockOverlayLook.FADE_SECONDS));
		assertEquals(0f, BlockOverlayLook.fade(1f, false, BlockOverlayLook.FADE_SECONDS));
	}

	@Test
	void theShaderColorsTheEffectItselfAndNeverGoesWhite() {
		for (float seconds = 0f; seconds < 10f; seconds += 0.1f) {
			int rgb = BlockOverlayLook.shaderColor(BlockOverlayLook.Colors.CUSTOM, 0x2060C0, 0.3f, seconds, 1f);
			assertTrue((rgb >> 16 & 0xFF) <= 0x20 && (rgb & 0xFF) <= 0xC0, "at " + seconds);
		}
	}

	@Test
	void aPulseIsAVeryShortFlash() {
		assertEquals(100L, BlockOverlayLook.PULSE_MILLIS);
	}
}
