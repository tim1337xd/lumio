package dev.tim1337.lumio.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mood pad's mapping, presets against Custom, and text's contrast with Intensity at its most. */
class ColoredMoodTest {
	private static final float CLOSE = 0.0001f;
	private static final int[][] COLORS = {{0xFF2E63, 0x2F6BFF, 0xA23BFF}, {0xFFE000, 0x00FF88, 0xFFFFFF}, {0xFF5A2E, 0xFFB02E, 0xA81E3C}, {0x2FE6A8, 0x2F6BFF, 0xA23BFF}, {0xFFFFFF}};
	private static final float GAME_WORLD = 0.08f;
	private static final float LAYER = 0.24f;

	@Test
	void theFlowPresetIsTheThemesOwnLook() {
		ColoredMood.Preset flow = ColoredMood.Preset.FLOW;
		assertEquals(1f, ColoredMood.rate(flow.speed()), CLOSE);
		assertEquals(3.4f, ColoredMood.fall(flow.edges()), CLOSE);
		assertEquals(0f, ColoredMood.sharp(flow.edges()), CLOSE);
		assertEquals(1f, ColoredMood.radiusFactor(flow.fieldSize()), CLOSE);
		assertEquals(0x2F6BFF, ColoredMood.intense(0x2F6BFF, flow.intensity()));
	}

	@Test
	void everyPresetIsFoundByItsValues() {
		for (ColoredMood.Preset preset : ColoredMood.Preset.values()) {
			assertSame(preset, ColoredMood.presetOf(preset.speed(), preset.edges(), preset.movement(), preset.fieldSize(), preset.intensity()));
		}
	}

	@Test
	void anyChangeMakesItCustom() {
		ColoredMood.Preset flow = ColoredMood.Preset.FLOW;
		assertNull(ColoredMood.presetOf(flow.speed() + 0.05f, flow.edges(), flow.movement(), flow.fieldSize(), flow.intensity()));
		assertNull(ColoredMood.presetOf(flow.speed(), flow.edges() + 0.05f, flow.movement(), flow.fieldSize(), flow.intensity()));
		assertNull(ColoredMood.presetOf(flow.speed(), flow.edges(), ColoredMood.Movement.STILL, flow.fieldSize(), flow.intensity()));
		assertNull(ColoredMood.presetOf(flow.speed(), flow.edges(), flow.movement(), flow.fieldSize() + 0.05f, flow.intensity()));
		assertNull(ColoredMood.presetOf(flow.speed(), flow.edges(), flow.movement(), flow.fieldSize(), flow.intensity() + 0.05f));
	}

	@Test
	void thePadMapsItsPlaceToItsValue() {
		assertEquals(0f, ColoredMood.padValue(0f, 100f), CLOSE);
		assertEquals(1f, ColoredMood.padValue(100f, 100f), CLOSE);
		assertEquals(0.62f, ColoredMood.padValue(62f, 100f), CLOSE);
		assertEquals(0f, ColoredMood.padValue(-20f, 100f), CLOSE);
		assertEquals(1f, ColoredMood.padValue(140f, 100f), CLOSE);
		assertEquals(0f, ColoredMood.padValue(10f, 0f), CLOSE);
	}

	@Test
	void thePadGoesFromCalmToLivelyAndSoftToDefined() {
		assertTrue(ColoredMood.rate(0f) < ColoredMood.rate(1f));
		assertTrue(ColoredMood.fall(0f) < ColoredMood.fall(0.5f));
		assertEquals(1f, ColoredMood.sharp(1f), CLOSE);
		assertEquals(0f, ColoredMood.sharp(0f), CLOSE);
	}

	@Test
	void largeFieldsAreAtTheLeftOfTheSlider() {
		assertTrue(ColoredMood.radiusFactor(0f) > ColoredMood.radiusFactor(1f));
	}

	@Test
	void textKeepsItsContrastWithIntensityAtItsMostAndTheMusicAtItsLoudest() {
		for (int[] colors : COLORS) {
			int[] fields = ColoredPalette.fields(colors);
			for (int i = 0; i < fields.length; i++) {
				fields[i] = ColoredMood.brighter(ColoredMood.intense(fields[i], 1f), ColoredMood.MOST_BREATH);
			}
			float layer = ColoredPalette.layerOver(fields, LAYER, GAME_WORLD);
			for (int field : fields) {
				int seen = ColoredPalette.mix(ColoredPalette.mix(field, 0xFFFFFF, GAME_WORLD), ColoredPalette.LAYER_RGB, layer);
				double contrast = 1.05 / (ColoredPalette.luminance(seen) + 0.05);
				assertTrue(contrast >= 4.5, "contrast " + contrast);
			}
		}
	}

	@Test
	void lessIntensityIsDuller() {
		int vivid = ColoredMood.intense(0xFF2E63, 1f);
		int dull = ColoredMood.intense(0xFF2E63, 0f);
		assertTrue((vivid >> 8 & 0xFF) < (dull >> 8 & 0xFF));
	}
}
