package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** An old shared config goes through the same upgrade as the settings file: an import takes only the keys of today's settings, so a value under an old key must be converted before that. */
class OldConfigImportTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@AfterEach
	void backToDefaults() {
		ModFeatures.HELD_ITEM_TRANSFORM.resetSettings();
	}

	private static Object swingRate() {
		for (var setting : ModFeatures.HELD_ITEM_TRANSFORM.settings()) {
			if (setting.key().equals("swing_rate")) {
				return setting.get();
			}
		}
		throw new IllegalStateException("no swing_rate");
	}

	private static boolean load(String config) {
		return FeatureManager.apply(config, ConfigPart.all(), feature -> true, false);
	}

	@Test
	void anImportedOldConfigKeepsItsSwingPace() {
		assertTrue(load("{\"held_item_transform\":{\"swing_speed\":0.57,\"swing_style\":\"SWEEP\"}}"));
		assertEquals((float) Math.log(0.57), (Float) swingRate(), 0.01f, "still slower than the game");
	}

	@Test
	void anOldConfigWithTheRemovedSettingsLoadsAndTheRestGoesIn() {
		assertTrue(load("{\"menus\":{\"world_panel\":false,\"song_card\":false},\"music\":{\"spotify_link\":null,\"spotify_dashboard\":\"x\"},\"held_item_transform\":{\"swing_rate\":0.25}}"));
		assertEquals(0.25f, (Float) swingRate(), 0.01f);
		for (var setting : ModFeatures.MENUS.settings()) {
			if (setting.key().equals("song_card")) {
				assertEquals(false, setting.get());
			}
		}
		ModFeatures.MENUS.resetSettings();
	}

	@Test
	void anImportedConfigOfTodayIsTakenAsItIs() {
		assertTrue(load("{\"_version\":" + ConfigUpgrade.VERSION + ",\"held_item_transform\":{\"swing_rate\":0.5}}"));
		assertEquals(0.5f, (Float) swingRate(), 0.01f);
	}
}
