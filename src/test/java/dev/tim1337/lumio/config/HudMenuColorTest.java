package dev.tim1337.lumio.config;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.hud.HudPanelFeature;

import com.google.gson.JsonObject;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A fresh config has the menu color on every HUD element that offers it, and the base of the looks has no fixed one in its place. */
class HudMenuColorTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void everyHudElementStartsOnTheMenuColor() {
		List<String> fixed = new ArrayList<>();
		int panels = 0;
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof HudPanelFeature) {
				panels++;
				boolean has = false;
				for (var setting : feature.settings()) {
					if (setting instanceof ColorSetting color && color.isMenuByDefault()) {
						has = true;
						if (!color.usesMenu()) {
							fixed.add(feature.id());
						}
					}
				}
				if (!has) {
					fixed.add(feature.id() + " (has no such color)");
				}
			}
		}
		assertTrue(panels > 0, "the HUD panels were found");
		assertTrue(fixed.isEmpty(), "These don't start on the menu color: " + fixed);
	}

	@Test
	void aResetGoesBackToTheMenuColor() {
		ColorSetting color = new ColorSetting("test", net.minecraft.network.chat.Component.literal("test"), 0x123456).menuByDefault();
		assertTrue(color.usesMenu());
		assertTrue(color.isDefault());
		color.setUsesMenu(false);
		assertFalse(color.isDefault());
		color.reset();
		assertTrue(color.usesMenu());
	}

	@Test
	void aSavedFixedColorStaysFixed() {
		ColorSetting color = new ColorSetting("test", net.minecraft.network.chat.Component.literal("test"), 0x123456).menuByDefault();
		JsonObject saved = new JsonObject();
		saved.addProperty("color", "#654321");
		saved.addProperty("menu", false);
		color.load(saved);
		assertFalse(color.usesMenu());
	}
}
