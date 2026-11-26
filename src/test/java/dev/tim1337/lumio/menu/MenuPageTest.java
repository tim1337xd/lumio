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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
		assertTrue(fresh.glowOn());
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
		assertTrue(menu.glowOn());
		assertEquals(0.8f, menu.glowStrength(), CLOSE);
		amount.set(0f);
		assertFalse(menu.glowOn());
		assertEquals(0f, amount.get(), CLOSE);
		assertEquals(0.8f, menu.glowStrength(), CLOSE);
		amount.set(0.3f);
		assertTrue(menu.glowOn());
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
	void everySettingHasItsPlaceInATabAndNothingIsInASecondWindow() {
		assertNull(menu.viewOf(setting("glass_blur")));
		assertNull(menu.viewOf(setting("reset_all")));
		assertEquals("Look · Theme", menu.sectionOf(setting("theme")));
		assertEquals("Glass · Glass", menu.sectionOf(setting("transparency")));
		assertEquals("Glass · Glass", menu.sectionOf(setting("glass_tint")));
		assertEquals("Glass · Real glass", menu.sectionOf(setting("glass_refraction")));
		assertEquals("Glass · Edges and color", menu.sectionOf(setting("glass_effect")));
		assertEquals("Glow · Glow", menu.sectionOf(setting("glow")));
		assertEquals("Glow · Color", menu.sectionOf(setting("glow_color_from")));
		assertEquals("Glow · Shape and motion", menu.sectionOf(setting("glow_blend")));
		assertEquals("Glow · Background", menu.sectionOf(setting("colored_background")));
		assertEquals("HUD · HUD theme", menu.sectionOf(setting("hud_glow_soft")));
		assertEquals("HUD · HUD theme", menu.sectionOf(setting("hud_theme")));
		assertEquals("Window and sound · Sound", menu.sectionOf(setting("ui_sound_volume")));
		assertEquals("About · Help", menu.sectionOf(setting("disable_all")));
		assertTrue(menu.alwaysTabbed());
		// nothing folds on this page any more
		for (var shown : menu.shownSettings()) {
			assertFalse(menu.isCollapsible(menu.sectionOf(shown)), shown.key());
		}
		assertFalse(menu.shownSettings().contains(setting("glow_row")), "the setup's glow row is not on the page");
		// the window's rows are the cursor's and borderless features' own settings, shown here
		assertTrue(menu.shownSettings().contains(dev.tim1337.lumio.ModFeatures.BORDERLESS.settingOf("start_borderless")));
		java.util.Set<String> tabs = new java.util.LinkedHashSet<>();
		for (var shown : menu.shownSettings()) {
			String section = menu.sectionOf(shown);
			tabs.add(section.substring(0, section.indexOf(" · ")));
		}
		assertEquals(java.util.List.of("Look", "Glass", "Glow", "HUD", "Window and sound", "About"), java.util.List.copyOf(tabs));
	}

	@Test
	void theGlowTakesItsColorFromTheChosenSource() {
		var from = setting("glow_color_from");
		var count = setting("glow_count");
		menu.setTheme(MacTheme.DARK);
		count.set(2f);
		from.set(MenuFeature.GlowSource.OWN);
		assertEquals(2, menu.glowPalette().length, "own: the glow colors");
		from.set(MenuFeature.GlowSource.ACCENT);
		assertArrayEquals(new int[]{MenuFeature.accentRgb() & 0xFFFFFF}, menu.glowPalette(), "accent: the accent alone");
		from.set(MenuFeature.GlowSource.THEME);
		assertEquals(MenuFeature.GlowSource.OWN, menu.glowSource(), "theme colors without a Colored theme glow in the own colors");
		assertFalse(menu.themeGlow());
		menu.setTheme(MacTheme.COLORED);
		assertTrue(menu.themeGlow(), "in Colored the panes glow in the theme's colors");
		assertArrayEquals(menu.coloredColors(), menu.glowPalette());
		from.set(MenuFeature.GlowSource.OWN);
		assertFalse(menu.themeGlow(), "own colors also in Colored");
		menu.setTheme(MacTheme.DARK);
		from.reset();
		count.reset();
	}

	@Test
	void anOldConfigKeepsItsGlow() {
		var from = setting("glow_color_from");
		com.google.gson.JsonObject colored = menu.save();
		colored.remove("glow_color_from");
		colored.addProperty("theme", "COLORED");
		menu.load(colored);
		assertEquals(MenuFeature.GlowSource.THEME, from.get(), "Colored kept its theme colors");
		com.google.gson.JsonObject dark = menu.save();
		dark.remove("glow_color_from");
		dark.addProperty("theme", "DARK");
		dark.addProperty("hud_theme", "MENU");
		menu.load(dark);
		assertEquals(MenuFeature.GlowSource.OWN, from.get(), "the other themes kept the glow colors");
		com.google.gson.JsonObject chosen = menu.save();
		chosen.addProperty("glow_color_from", "ACCENT");
		menu.load(chosen);
		assertEquals(MenuFeature.GlowSource.ACCENT, from.get(), "a choice made stays");
		menu.setTheme(MacTheme.DARK);
		from.reset();
	}

	@Test
	void theTabResetTakesBackOnlyItsTab() {
		var sounds = setting("ui_sounds");
		var font = setting("ui_font");
		sounds.set(false);
		Object other = ((Enum<?>) font.get()).getDeclaringClass().getEnumConstants()[1];
		font.set(other);
		menu.resetSettings(each -> menu.sectionOf(each) != null && menu.sectionOf(each).startsWith("Window and sound"));
		assertEquals(true, sounds.get(), "the sound is back");
		assertEquals(other, font.get(), "the Look tab stays");
		font.reset();
	}

	@Test
	void everyRowOfThePageBuildsItsControl() {
		for (MacTheme theme : MacTheme.values()) {
			menu.setTheme(theme);
			SettingsPagesTest.buildsWithRows(menu);
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
