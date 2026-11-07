package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.config.setting.ColoredColorsSetting;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;

/** The fields of the Colored theme, the contrast rule, and loading its colors from a config. */
class ColoredPaletteTest {
	private static final float MENU_LAYER = 0.30f;
	private static final float GAME_LAYER = 0.40f;
	private static final float GAME_WORLD = 0.15f;

	/** @return the worst contrast of white text over the fields, under the layer */
	private static double worstContrast(int[] colors, float layer, float world) {
		double worst = 21.0;
		for (int field : ColoredPalette.fields(colors)) {
			int over = ColoredPalette.mix(ColoredPalette.mix(field, 0xFFFFFF, world), ColoredPalette.LAYER_RGB, layer);
			worst = Math.min(worst, ColoredPalette.whiteContrast(over));
		}
		return worst;
	}

	@Test
	void whiteKeepsItsContrastWithAnyColors() {
		int[][] all = {{0xFF2E63, 0x2F6BFF, 0xA23BFF}, {0xFFFFFF}, {0xFFFF00, 0x00FFFF}, {0xFFFF00, 0xFFFFFF, 0x00FF00}, {0x101018}, {0xFF0000}, {0xE0E0E0, 0x30D158}};
		for (int[] colors : all) {
			float menu = ColoredPalette.layer(colors, MENU_LAYER, 0f);
			assertTrue(worstContrast(colors, menu, 0f) >= 4.5, "menu " + java.util.Arrays.toString(colors));
			float game = ColoredPalette.layer(colors, GAME_LAYER, GAME_WORLD);
			assertTrue(worstContrast(colors, game, GAME_WORLD) >= 4.5, "game " + java.util.Arrays.toString(colors));
		}
	}

	@Test
	void darkColorsKeepTheWantedLayer() {
		assertEquals(MENU_LAYER, ColoredPalette.layer(new int[]{0x202030}, MENU_LAYER, 0f));
	}

	@Test
	void lightColorsDarkenTheLayerNotTheColors() {
		int[] colors = {0xFFFFFF};
		assertTrue(ColoredPalette.layer(colors, MENU_LAYER, 0f) > MENU_LAYER);
		assertEquals(0xFFFFFF, colors[0]);
		assertEquals(0xFFFFFF, ColoredPalette.fields(colors)[0]);
	}

	@Test
	void everyCountHasFiveFields() {
		assertEquals(ColoredPalette.FIELDS, ColoredPalette.fields(new int[]{0xFF2E63}).length);
		assertEquals(ColoredPalette.FIELDS, ColoredPalette.fields(new int[]{0xFF2E63, 0x2F6BFF}).length);
		assertEquals(ColoredPalette.FIELDS, ColoredPalette.fields(new int[]{0xFF2E63, 0x2F6BFF, 0xA23BFF}).length);
	}

	@Test
	void eachColorHasItsOwnArea() {
		int[] two = ColoredPalette.fields(new int[]{0xFF2E63, 0x2F6BFF});
		assertEquals(0xFF2E63, two[0]);
		assertEquals(0x2F6BFF, two[1]);
		assertEquals(ColoredPalette.mix(0xFF2E63, 0x2F6BFF, 0.5f), two[2]);
		int[] three = ColoredPalette.fields(new int[]{0xFF2E63, 0x2F6BFF, 0xA23BFF});
		assertEquals(0xFF2E63, three[0]);
		assertEquals(0x2F6BFF, three[1]);
		assertEquals(0xA23BFF, three[2]);
	}

	@Test
	void theBaseIsDarkAndFromTheColors() {
		int base = ColoredPalette.base(new int[]{0xFF2E63, 0x2F6BFF});
		assertTrue(ColoredPalette.luminance(base) < ColoredPalette.luminance(0xFF2E63));
		assertTrue(ColoredPalette.luminance(base) > 0.0);
	}

	@Test
	void theAccentIsTheFirstColorUnlessItCannotCarryWhiteText() {
		assertEquals(0x2F6BFF, ColoredPalette.accent(0x2F6BFF));
		int fromWhite = ColoredPalette.accent(0xFFFFFF);
		assertTrue(ColoredPalette.whiteContrast(fromWhite) >= 3.0);
		assertTrue(ColoredPalette.whiteContrast(ColoredPalette.accent(0xFFFF40)) >= 3.0);
	}

	private static ColoredColorsSetting setting() {
		return new ColoredColorsSetting("colors", Component.literal("Colors"));
	}

	@Test
	void aFreshConfigHasTheThreeDefaults() {
		ColoredColorsSetting setting = setting();
		assertArrayEquals(ColoredPalette.DEFAULT_COLORS, setting.colors());
		assertTrue(setting.isDefault());
	}

	@Test
	void loadsOneTwoAndThreeColors() {
		for (int[] colors : new int[][]{{0x112233}, {0x112233, 0x445566}, {0x112233, 0x445566, 0x778899}}) {
			ColoredColorsSetting saved = setting();
			saved.setColors(colors);
			ColoredColorsSetting loaded = setting();
			loaded.load(JsonParser.parseString(saved.save().toString()));
			assertArrayEquals(colors, loaded.colors());
			assertFalse(loaded.isDefault());
		}
	}

	@Test
	void badValuesKeepWhatIsThere() {
		for (String json : new String[]{"[]", "{}", "5", "[\"nope\"]", "[1,2]"}) {
			ColoredColorsSetting setting = setting();
			setting.load(JsonParser.parseString(json));
			assertArrayEquals(ColoredPalette.DEFAULT_COLORS, setting.colors(), json);
		}
	}

	@Test
	void moreThanThreeAreCut() {
		ColoredColorsSetting setting = setting();
		setting.load(JsonParser.parseString("[\"#111111\",\"#222222\",\"#333333\",\"#444444\"]"));
		assertArrayEquals(new int[]{0x111111, 0x222222, 0x333333}, setting.colors());
	}

	@Test
	void addingAndRemovingKeepsAtLeastOneAndAtMostThree() {
		ColoredColorsSetting setting = setting();
		assertFalse(setting.canAdd());
		setting.remove(1);
		assertArrayEquals(new int[]{0xFF2E63, 0xA23BFF}, setting.colors());
		setting.remove(0);
		assertArrayEquals(new int[]{0xA23BFF}, setting.colors());
		assertFalse(setting.canRemove());
		setting.remove(0);
		assertEquals(1, setting.colors().length);
		assertTrue(setting.canAdd());
		setting.add();
		assertEquals(2, setting.colors().length);
	}

	@Test
	void resetBringsTheDefaultsBack() {
		ColoredColorsSetting setting = setting();
		setting.setColors(new int[]{0x112233});
		setting.reset();
		assertArrayEquals(ColoredPalette.DEFAULT_COLORS, setting.colors());
	}

	@Test
	void aBlendPassesNoMud() {
		// red to green mixed by their numbers goes through a dark olive; as the eye sees it stays bright
		int red = 0xFF0000;
		int green = 0x00FF00;
		assertEquals(red, ColoredPalette.mixPerceptual(red, green, 0f));
		assertEquals(green, ColoredPalette.mixPerceptual(red, green, 1f));
		double middle = ColoredPalette.luminance(ColoredPalette.mixPerceptual(red, green, 0.5f));
		assertTrue(middle > ColoredPalette.luminance(ColoredPalette.mix(red, green, 0.5f)));
	}

	@Test
	void fieldsGoToTheNearestNewColor() {
		int[] shown = {0xFF0000, 0x0000FF, 0x00FF00};
		// the same colors in another order: nothing has to move
		assertEquals(java.util.List.of(0xFF0000, 0x0000FF, 0x00FF00), java.util.Arrays.stream(ColoredPalette.matched(shown, new int[]{0x00FF00, 0xFF0000, 0x0000FF})).boxed().toList());
	}

	@Test
	void neighborsAlwaysDiffer() {
		for (int[] colors : new int[][]{{0xFF2E63}, {0xFF2E63, 0x2F6BFF}, {0xFF2E63, 0x2F6BFF, 0xA23BFF}}) {
			int[] fields = ColoredPalette.fields(colors);
			for (int[] row : new int[][]{{0, 1, 2, 3}, {4, 5, 6}, {7, 8, 9, 10}}) {
				for (int i = 1; i < row.length; i++) {
					assertTrue(fields[row[i - 1]] != fields[row[i]], "fields " + row[i - 1] + " and " + row[i] + " of " + colors.length + " colors");
				}
			}
		}
	}
}
