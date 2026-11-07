package dev.tim1337.lumio.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.PanelTheme;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Every HUD panel draws in its own look when it has one, whatever the menu's theme is: its glow and material never come from the menu. */
class PanelOwnLookTest {
	private static final MacTheme[] MENU_THEMES = {MacTheme.GLASS, MacTheme.DARK, MacTheme.LIGHT, MacTheme.COLORED};

	@BeforeAll
	static void start() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	private static List<HudPanelFeature> panels() {
		List<HudPanelFeature> panels = new ArrayList<>();
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof HudPanelFeature panel) {
				panels.add(panel);
			}
		}
		return panels;
	}

	@SuppressWarnings("unchecked")
	private static <T> T setting(Feature feature, String key) {
		for (Setting<?> setting : feature.settings()) {
			if (setting.key().equals(key)) {
				return (T) setting;
			}
		}
		throw new IllegalStateException(feature.id() + " has no " + key);
	}

	private static void custom(HudPanelFeature panel, PanelTheme theme, boolean glow) {
		EnumSetting<PanelLook.Mode> mode = setting(panel, "look_mode");
		mode.set(PanelLook.Mode.CUSTOM);
		EnumSetting<PanelTheme> own = setting(panel, "panel_theme");
		own.set(theme);
		BooleanSetting glowSwitch = setting(panel, "glow");
		glowSwitch.set(glow);
	}

	@Test
	void thereArePanels() {
		assertFalse(panels().isEmpty());
	}

	@Test
	void customGlassWithoutGlowUnderEveryMenuTheme() {
		for (HudPanelFeature panel : panels()) {
			custom(panel, PanelTheme.GLASS, false);
			for (MacTheme menu : MENU_THEMES) {
				ModFeatures.MENU.setTheme(menu);
				panel.enterLook();
				try {
					assertEquals(MacTheme.GLASS, HudStyle.themeNow(), panel.id() + " under " + menu);
					assertEquals(0f, HudStyle.surfaceGlowNow(), panel.id() + " has a glow under " + menu);
				} finally {
					HudPanelFeature.leaveLook();
				}
			}
		}
	}

	@Test
	void itsOwnGlowWhenSwitchedOn() {
		for (HudPanelFeature panel : panels()) {
			custom(panel, PanelTheme.DARK, true);
			panel.enterLook();
			try {
				assertEquals(MacTheme.DARK, HudStyle.themeNow(), panel.id());
				assertTrue(HudStyle.surfaceGlowNow() > 0f, panel.id());
			} finally {
				HudPanelFeature.leaveLook();
			}
		}
	}

	@Test
	void theThemeDefaultFollowsTheHudTheme() {
		for (HudPanelFeature panel : panels()) {
			custom(panel, PanelTheme.HUD, false);
			for (MacTheme menu : MENU_THEMES) {
				ModFeatures.MENU.setTheme(menu);
				panel.enterLook();
				try {
					assertEquals(ModFeatures.MENU.hudTheme(), HudStyle.themeNow(), panel.id() + " under " + menu);
				} finally {
					HudPanelFeature.leaveLook();
				}
			}
		}
	}

	@Test
	void outsideOfAPanelNothingChanges() {
		HudPanelFeature.leaveLook();
		assertEquals(1f, HudStyle.surfaceGlowNow(), "other surfaces keep their glow");
	}
}
