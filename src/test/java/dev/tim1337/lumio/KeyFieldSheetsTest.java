package dev.tim1337.lumio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.PageOutlines;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.config.setting.Setting;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Every key of the Keybinds page's overview is a key field in the sheet of exactly one feature, on the sheet itself and not a level deeper
 * under Advanced, and no key field of a feature is missing from the overview. Both are the same setting over the game's key, so a change in
 * one shows in the other.
 */
class KeyFieldSheetsTest {
	@BeforeAll
	static void start() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
		PageOutlines.apply(Lumio.MOD_ID);
		// The keys, handed to their features the way registering them does (Feature.addKey). Registering itself needs the running game, so the
		// keys here are made for the test, under the names of the mod's own.
		give(ModFeatures.CAMERA, "full_bright");
		for (String name : List.of("cinematic_toggle", "cinematic_leave", "cinematic_next", "cinematic_hold", "cinematic_lyrics")) {
			give(ModFeatures.CINEMATIC, name);
		}
		for (String name : List.of("music_play", "music_next", "music_previous")) {
			give(ModFeatures.MUSIC, name);
		}
		for (String name : List.of("waypoint_place", "waypoint_next", "waypoint_previous", "waypoint_map")) {
			give(ModFeatures.WAYPOINTS, name);
		}
	}

	private static void give(Feature feature, String name) {
		try {
			Method add = Feature.class.getDeclaredMethod("addKey", KeyMapping.class, KeySetting.Where.class);
			add.setAccessible(true);
			add.invoke(feature, new KeyMapping("key." + Lumio.MOD_ID + "." + name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
				KeyMapping.Category.MISC), KeySetting.Where.WORLD);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** @return the features (not their sub-pages) whose settings hold a key */
	private static List<Feature> holders(KeySetting key) {
		List<Feature> holders = new ArrayList<>();
		for (Feature feature : FeatureManager.features()) {
			if (!(feature instanceof SettingsView) && feature.settings().contains(key)) {
				holders.add(feature);
			}
		}
		return holders;
	}

	@Test
	void everyKeyOfTheOverviewIsAFieldOfExactlyOneFeaturesSheet() throws ReflectiveOperationException {
		assertTrue(KeySetting.all().size() >= 10, "the mod's keys are registered: " + KeySetting.all().size());
		for (KeySetting key : KeySetting.all()) {
			List<Feature> holders = holders(key);
			assertEquals(1, holders.size(), key.key() + " is a field of " + holders.size() + " features");
			Feature feature = holders.getFirst();
			assertFalse(key.owner().isEmpty(), key.key() + " names its feature for the overview's link");
			if (feature == ModFeatures.MUSIC) {
				// the music page is an overview whose rows open sub-pages: its keys are the Keys row's page
				Method keys = feature.getClass().getDeclaredMethod("keysView");
				keys.setAccessible(true);
				assertTrue(((Feature) keys.invoke(feature)).settings().contains(key), key.key() + " is on the music page's Keys page");
				continue;
			}
			assertTrue(feature.shownSettings().contains(key), key.key() + " is a field on the sheet of " + feature.id() + " itself");
			if (feature.outline() != null) {
				assertFalse(feature.pageAdvanced().settings().contains(key), key.key() + " is not also under Advanced");
			}
		}
	}

	@Test
	void noKeyFieldOfAFeatureIsMissingFromTheOverview() {
		int fields = 0;
		for (Feature feature : FeatureManager.features()) {
			if (feature instanceof SettingsView) {
				continue;
			}
			for (Setting<?> setting : feature.settings()) {
				if (setting instanceof KeySetting key) {
					fields++;
					assertTrue(KeySetting.all().contains(key), key.key() + " of " + feature.id() + " is not in the overview's list");
					assertTrue(key.isVisible(), key.key() + " is hidden, which leaves it out of the overview");
					assertNotNull(key.mapping());
				}
			}
		}
		assertEquals(KeySetting.all().size(), fields, "the overview lists exactly the features' key fields");
	}

	@Test
	void noKeyIsStartedWithADefaultKey() {
		for (KeySetting key : KeySetting.all()) {
			assertTrue(key.mapping().isUnbound(), key.key() + " has a key by default");
		}
	}
}
