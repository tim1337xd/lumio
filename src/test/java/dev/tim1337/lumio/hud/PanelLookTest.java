package dev.tim1337.lumio.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.PanelTheme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A panel and the global HUD theme: what a new config shows, what an old one keeps, and that a change in the editor keeps what is shown. */
class PanelLookTest {
	private HudPanelFeature panel;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@BeforeEach
	void aFreshConfig() {
		panel = ModFeatures.ARMOR;
		ModFeatures.MENU.resetSettings();
		panel.resetSettings();
		PanelLook.settleAll();
	}

	@AfterEach
	void backToAFreshConfig() {
		aFreshConfig();
	}

	private static Setting<?> setting(Feature feature, String key) {
		for (Setting<?> setting : feature.settings()) {
			if (setting.key().equals(key)) {
				return setting;
			}
		}
		throw new IllegalArgumentException(key);
	}

	private boolean flag(String key) {
		return ((BooleanSetting) setting(panel, key)).get();
	}

	private float number(String key) {
		return ((FloatSetting) setting(panel, key)).get();
	}

	private Object mode() {
		return setting(panel, "look_mode").get();
	}

	private void loadOld(String json) {
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		panel.load(object);
		PanelLook.settleAll();
	}

	@Test
	void aNewConfigFollowsTheThemeWithItsGlow() {
		assertEquals(PanelLook.Mode.THEME, mode());
		assertTrue(flag("glow"), "the theme's glow");
		assertTrue(flag("glow_soft"));
	}

	@Test
	void aChangeOfTheThemeReachesThePanel() {
		((FloatSetting) setting(ModFeatures.MENU, "hud_size")).set(1.5f);
		PanelLook.settleAll();
		assertEquals(1.5f, number("scale"), 0.001f);
		assertEquals(PanelLook.Mode.THEME, mode());
	}

	@Test
	void aChangeOnThePanelMakesItCustomAndKeepsTheRest() {
		((FloatSetting) setting(panel, "scale")).set(1.4f);
		assertEquals(PanelLook.Mode.CUSTOM, mode());
		assertTrue(flag("glow"), "what it showed stays");
		((FloatSetting) setting(ModFeatures.MENU, "hud_size")).set(0.7f);
		PanelLook.settleAll();
		assertEquals(1.4f, number("scale"), 0.001f, "a custom panel keeps its own");
	}

	@Test
	void backToTheThemeClearsThePanelsValues() {
		((FloatSetting) setting(panel, "scale")).set(1.4f);
		((FloatSetting) setting(panel, "see_through")).set(0.3f);
		@SuppressWarnings("unchecked")
		EnumSetting<PanelLook.Mode> choice = (EnumSetting<PanelLook.Mode>) setting(panel, "look_mode");
		choice.set(PanelLook.Mode.THEME);
		assertEquals(1f, number("scale"), 0.001f);
		assertEquals(0f, number("see_through"), 0.001f);
	}

	@Test
	void anOldPanelLikeTheThemeShowsThemeDefault() {
		ModFeatures.MENU.load(JsonParser.parseString("{\"hud_glow\":false}").getAsJsonObject());
		loadOld("{\"glow\":false,\"scale\":1.0,\"see_through\":0.0,\"background\":true,\"panel_theme\":\"HUD\",\"panel_depth\":\"MENU\"}");
		assertEquals(PanelLook.Mode.THEME, mode());
		assertFalse(flag("glow"));
	}

	@Test
	void anOldPanelThatDiffersIsCustomAndKeepsEveryValue() {
		ModFeatures.MENU.load(JsonParser.parseString("{\"hud_glow\":false}").getAsJsonObject());
		loadOld("{\"glow\":true,\"scale\":1.3,\"see_through\":0.25,\"background\":false,\"panel_theme\":\"DARK\",\"panel_depth\":\"OFF\",\"accent\":{\"color\":\"#FF0000\",\"menu\":false}}");
		assertEquals(PanelLook.Mode.CUSTOM, mode());
		assertTrue(flag("glow"));
		assertFalse(flag("glow_soft"), "the strong glow it had");
		assertEquals(1.3f, number("scale"), 0.001f);
		assertEquals(0.25f, number("see_through"), 0.001f);
		assertFalse(flag("background"));
		assertEquals(PanelTheme.DARK, setting(panel, "panel_theme").get());
		assertEquals(0xFF0000, ((ColorSetting) setting(panel, "accent")).get());
	}

	@Test
	void oneValueOffIsEnoughForCustom() {
		ModFeatures.MENU.load(JsonParser.parseString("{\"hud_glow\":false}").getAsJsonObject());
		loadOld("{\"glow\":false,\"see_through\":0.05}");
		assertEquals(PanelLook.Mode.CUSTOM, mode());
	}

	@Test
	void aSavedChoiceIsNotDecidedAgain() {
		loadOld("{\"look_mode\":\"CUSTOM\",\"glow\":true,\"glow_soft\":true}");
		assertEquals(PanelLook.Mode.CUSTOM, mode());
	}

	@Test
	void themeValuesDoNotCountAsChanges() {
		assertEquals(0, panel.changedSettings());
		assertTrue(FeatureManager.features().contains(panel));
	}
}
