package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.ColoredMood;
import dev.tim1337.lumio.ui.MacTheme;

import com.google.gson.JsonObject;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one page of the menu settings: what its combined rows do with the saved values, and what old configs turn into. Runs without the game's window. */
public class MenuPageTest {
	private static final float CLOSE = 0.001f;
	private static MenuFeature menu;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
		menu = ModFeatures.MENU;
	}

	@BeforeEach
	void defaults() {
		menu.settings().forEach(Setting::reset);
	}

	@SuppressWarnings("unchecked")
	private static <T> Setting<T> setting(String key) {
		for (Setting<?> setting : menu.settings()) {
			if (setting.key().equals(key)) {
				return (Setting<T>) setting;
			}
		}
		throw new AssertionError("no setting " + key);
	}

	@Test
	void aNewConfigStartsDarkAndASavedThemeStays() {
		MenuFeature fresh = new MenuFeature();
		assertEquals(MacTheme.DARK, fresh.theme());
		assertEquals(MenuFeature.Accent.BLUE, fresh.accent());
		assertTrue(fresh.glowEnabled());
		fresh.load(new JsonObject());
		assertEquals(MacTheme.DARK, fresh.theme());
		JsonObject saved = new JsonObject();
		saved.addProperty("theme", "GLASS");
		fresh.load(saved);
		assertEquals(MacTheme.GLASS, fresh.theme());
	}

	@Test
	void transparencyAtItsDefaultLeavesTheGlassAsItWas() {
		assertEquals(0.52f, MenuPageTest.<Float>setting("transparency").get(), CLOSE);
		assertEquals(0.55f, GlassMix.blurFor(0.52f), CLOSE);
		assertEquals(0.55f, menu.glassBlur(), CLOSE);
		assertEquals(0.48f, menu.glassOpacity(), CLOSE);
	}

	@Test
	void transparencyMovesTintAndBlurTogether() {
		Setting<Float> transparency = setting("transparency");
		transparency.set(1f);
		assertEquals(0f, menu.glassOpacity(), CLOSE);
		assertEquals(0f, menu.glassBlur(), CLOSE);
		transparency.set(0f);
		assertEquals(1f, menu.glassOpacity(), CLOSE);
		assertEquals(1f, menu.glassBlur(), CLOSE);
		transparency.set(0.52f);
		assertEquals(0.48f, menu.glassOpacity(), CLOSE);
		assertEquals(0.55f, menu.glassBlur(), CLOSE);
	}

	@Test
	void aBlurSetByItselfKeepsItsDistance() {
		assertEquals(0.65f, GlassMix.blurAfter(0.65f, 0.52f, 0.52f), CLOSE);
		assertEquals(GlassMix.blurFor(0.3f) + 0.1f, GlassMix.blurAfter(0.65f, 0.52f, 0.3f), CLOSE);
		assertEquals(1f, GlassMix.blurAfter(0.65f, 0.52f, 0f), CLOSE);
	}

	@Test
	void glowStrengthZeroIsOffAndKeepsTheStrength() {
		Setting<Float> amount = setting("glow_amount");
		amount.set(0.8f);
		assertTrue(menu.glowEnabled());
		assertEquals(0.8f, menu.glowStrength(), CLOSE);
		amount.set(0f);
		assertFalse(menu.glowEnabled());
		assertEquals(0f, amount.get(), CLOSE);
		assertEquals(0.8f, menu.glowStrength(), CLOSE);
		amount.set(0.3f);
		assertTrue(menu.glowEnabled());
		assertEquals(0.3f, menu.glowStrength(), CLOSE);
	}

	@Test
	void reducedMeansNoOvershootAndOffMeansNoMovement() {
		Setting<MenuFeature.Motion> motion = setting("motion");
		assertEquals(MenuFeature.Motion.FULL, motion.get());
		assertEquals(0.5f, menu.bounce(), CLOSE);
		motion.set(MenuFeature.Motion.REDUCED);
		assertTrue(menu.animations());
		assertEquals(0f, menu.bounce(), CLOSE);
		motion.set(MenuFeature.Motion.OFF);
		assertFalse(menu.animations());
		motion.set(MenuFeature.Motion.FULL);
		assertTrue(menu.animations());
		assertEquals(0.5f, menu.bounce(), CLOSE);
	}

	@Test
	void anOldReduceTransparencyBecomesNearlySolidGlass() {
		JsonObject config = new JsonObject();
		config.addProperty("reduce_transparency", true);
		menu.load(config);
		assertEquals(0.9f, menu.glassOpacity(), CLOSE);
		assertFalse(menu.save().has("reduce_transparency"));
	}

	@Test
	void savedValuesOfRemovedSettingsAreIgnoredAndOthersKept() {
		JsonObject config = new JsonObject();
		config.addProperty("glass_readable", false);
		config.addProperty("glow_menu", false);
		config.addProperty("glow_title", false);
		config.addProperty("reduce_transparency", false);
		config.addProperty("glow_strength", 0.4f);
		config.addProperty("colored_background", false);
		menu.load(config);
		assertTrue(menu.glowOn());
		assertEquals(0.4f, menu.glowStrength(), CLOSE);
		assertEquals(0.48f, menu.glassOpacity(), CLOSE);
		assertFalse(menu.backgroundGlow());
		JsonObject saved = menu.save();
		for (String gone : List.of("glass_readable", "glow_menu", "glow_title", "glow_hud", "glow_buttons", "reduce_transparency")) {
			assertFalse(saved.has(gone), gone);
		}
		assertTrue(saved.has("colored_background"));
	}

	@Test
	void theBackgroundGlowGivesWayToTheColoredTheme() {
		assertTrue(menu.backgroundGlow());
		menu.setTheme(MacTheme.COLORED);
		assertFalse(menu.backgroundGlow());
		assertTrue(setting("colored_background").isDisabled());
		menu.setTheme(MacTheme.DARK);
		assertTrue(menu.backgroundGlow());
		assertFalse(setting("colored_background").isDisabled());
	}

	@Test
	void theColoredRowsReplaceTheAccentRow() {
		menu.setTheme(MacTheme.DARK);
		assertTrue(setting("accent").isVisible());
		assertFalse(setting("colored_row").isVisible());
		assertEquals("Only for glass", setting("transparency").disabledNote());
		menu.setTheme(MacTheme.COLORED);
		assertFalse(setting("accent").isVisible());
		assertTrue(setting("colored_row").isVisible());
		assertTrue(setting("colored_customize").isVisible());
		assertEquals("Accent follows color 1", setting("colored_row").description());
		assertEquals("In Colored: Customize › Intensity", setting("transparency").disabledNote());
		assertEquals("Flow · Drift · Calm", setting("colored_customize").description());
	}

	@Test
	void theColoredRowsAlsoShowWhileOnlyTheHudIsColored() {
		menu.setTheme(MacTheme.DARK);
		setting("hud_theme").set(cast(MenuFeature.HudTheme.COLORED));
		assertTrue(setting("accent").isVisible());
		assertTrue(setting("colored_row").isVisible());
		assertNull(setting("colored_row").description());
	}

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object value) {
		return (T) value;
	}

	@Test
	void theSummaryNamesPresetMovementAndMood() {
		assertEquals("Flow · Drift · Calm", ColoredMood.summary(ColoredMood.Preset.FLOW, ColoredMood.Movement.DRIFT, 0.5f));
		assertEquals("Custom · Swirl · Steady", ColoredMood.summary(null, ColoredMood.Movement.SWIRL, 0.6f));
		assertEquals("Vivid · Waves · Lively", ColoredMood.summary(ColoredMood.Preset.VIVID, ColoredMood.Movement.WAVES, 0.9f));
	}

	@Test
	void theSheetAndTheAboutPageHoldWhatLeftThePage() {
		assertNotNull(menu.viewOf(setting("glass_blur")));
		assertTrue(menu.viewOf(setting("glass_blur")).isSheet());
		assertNotNull(menu.viewOf(setting("colored_background")));
		assertNotNull(menu.viewOf(setting("ui_sound_volume")));
		assertFalse(menu.viewOf(setting("reset_all")).isSheet());
		assertNull(menu.viewOf(setting("ui_font")));
	}

	@Test
	void everyRowOfThePageBuildsItsControl() {
		for (MacTheme theme : MacTheme.values()) {
			menu.setTheme(theme);
			SettingsPagesTest.buildsWithRows(menu);
			SettingsPagesTest.buildsWithRows(menu.viewOf(setting("glass_blur")));
			SettingsPagesTest.buildsWithRows(menu.viewOf(setting("reset_all")));
		}
	}

	@Test
	void theWindowSizeKeepsToItsRangeAndStepsWhereTheScreenSetsNoLimit() {
		var size = new dev.tim1337.lumio.config.setting.FloatSetting("size", net.minecraft.network.chat.Component.literal("Size"), 1f, 0.7f, 1.3f, 2).percent().step(0.05f);
		assertEquals(0.7f, size.rounded(0.5f), CLOSE);
		assertEquals(1.3f, size.rounded(1.6f), CLOSE);
		assertEquals(0.85f, size.rounded(0.86f), CLOSE);
		size.upperLimit(() -> 1.05);
		assertEquals(1.05f, size.max(), CLOSE);
		assertEquals(1.05f, size.rounded(1.3f), CLOSE);
		assertEquals(1f, size.fraction(1.05f), CLOSE);
	}

	@Test
	void aDoubleClickPutsTheSliderBackOnlyWhereItIsAsked() {
		var plain = new dev.tim1337.lumio.config.setting.FloatSetting("plain", net.minecraft.network.chat.Component.literal("Plain"), 1f, 0.7f, 1.3f, 2).percent().step(0.05f);
		assertFalse(plain.resetsOnDoubleClick());
		var resets = new dev.tim1337.lumio.config.setting.FloatSetting("resets", net.minecraft.network.chat.Component.literal("Resets"), 1f, 0.7f, 1.3f, 2).percent().step(0.05f).doubleClickResets();
		assertTrue(resets.resetsOnDoubleClick());
	}

	@Test
	void theGlowColorsRowAddsAndRemovesWithinItsSlots() {
		var row = (dev.tim1337.lumio.config.setting.GlowColorsSetting) (Setting<?>) setting("glow_colors");
		assertEquals(3, row.count());
		assertFalse(row.canAdd());
		row.remove(0);
		assertEquals(2, row.count());
		row.remove(0);
		assertEquals(1, row.count());
		assertFalse(row.canRemove());
		row.remove(0);
		assertEquals(1, row.count());
		row.add();
		assertEquals(2, row.count());
	}
}
