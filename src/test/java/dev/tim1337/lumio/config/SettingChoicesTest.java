package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.Setting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Settings look alike on every page: in a choice with an "Auto" option, Auto is the last one, on the right (rule of 2026-11-24). */
class SettingChoicesTest {
	private static final String AUTO = "AUTO";

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void aSavedAutoSkyStyleLoadsAsClouds() {
		com.google.gson.JsonObject saved = ModFeatures.SKY.save();
		saved.addProperty("style", AUTO);
		ModFeatures.SKY.load(saved);
		assertEquals("CLOUDS", ModFeatures.SKY.save().get("style").getAsString(), "the Auto style is gone (2026-11-24): the natural sky");
	}

	@Test
	void autoIsAlwaysTheLastOption() {
		List<String> wrong = new ArrayList<>();
		for (Feature feature : FeatureManager.features()) {
			for (Setting<?> setting : feature.settings()) {
				if (setting instanceof EnumSetting<?> choice) {
					Enum<?>[] options = choice.get().getDeclaringClass().getEnumConstants();
					for (int i = 0; i < options.length - 1; i++) {
						if (options[i].name().equals(AUTO)) {
							wrong.add(feature.id() + "." + setting.key());
						}
					}
				}
			}
		}
		assertEquals(List.of(), wrong, "Auto is not the last option");
	}
}
