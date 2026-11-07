package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.ModFeatures;

import com.google.gson.JsonElement;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * No setting gets lost: every saved key of the list taken before the settings were tidied up is still there, or is named here with where its value went. The list itself never changes;
 * {@code ./gradlew test -PupdateSettingKeys} only writes it when it does not exist yet.
 */
class SettingKeysTest {
	private static final Path BASELINE = Path.of("src/test/resources/settings-keys.txt");
	/** What left, on purpose, and where its value went. */
	private static final Map<String, String> GONE = new TreeMap<>(Map.ofEntries(Map.entry("ambience.distance", "removed: left over from before the rework"), Map.entry("ambience.ember_amount", "removed: left over from before the rework"), Map.entry("ambience.ember_color", "removed: left over from before the rework"), Map.entry("ambience.fog", "removed: left over from before the rework"), Map.entry("ambience.indoors", "removed: left over from before the rework"), Map.entry("ambience.snow_color", "removed: left over from before the rework"), Map.entry("ambience.wind", "removed: left over from before the rework"), Map.entry("ambience.wind_streak_amount", "removed: left over from before the rework"), Map.entry("ambience.wind_streaks", "removed: left over from before the rework"), Map.entry("ambience.wind_where", "removed: left over from before the rework"), Map.entry("arrow_trails.star_color", "removed: left over from before the rework"), Map.entry("arrow_trails.star_count", "removed: left over from before the rework"), Map.entry("held_item_outline.body_light", "removed: left over from before the rework"), Map.entry("held_item_outline.glow_smoothness", "removed: left over from before the rework"), Map.entry("held_item_outline.show_in", "removed: left over from before the rework"), Map.entry("held_item_transform.affects", "removed: left over from before the rework"), Map.entry("held_item_transform.cooldown_dip", "removed: left over from before the rework"), Map.entry("held_item_transform.hand_size", "removed: left over from before the rework"), Map.entry("held_item_transform.hand_x", "removed: left over from before the rework"), Map.entry("held_item_transform.hand_y", "removed: left over from before the rework"), Map.entry("held_item_transform.hand_z", "removed: left over from before the rework"), Map.entry("held_item_transform.z", "removed: left over from before the rework"), Map.entry("sky.also_show", "removed: left over from before the rework"), Map.entry("sky.animation_speed", "removed: left over from before the rework"), Map.entry("sky.color", "removed: left over from before the rework"), Map.entry("sky.cycle_source", "removed: left over from before the rework"), Map.entry("sky.lightning", "removed: left over from before the rework"), Map.entry("sky.lightning_all", "removed: left over from before the rework"), Map.entry("sky.lightning_bolts", "removed: left over from before the rework"), Map.entry("sky.lightning_branches", "removed: left over from before the rework"), Map.entry("sky.lightning_brightness", "removed: left over from before the rework"), Map.entry("sky.lightning_color", "removed: left over from before the rework"), Map.entry("sky.mode", "removed: left over from before the rework"), Map.entry("sky.nether_sky", "removed: left over from before the rework"), Map.entry("sky.sync_music", "removed: left over from before the rework"), Map.entry("sky.sync_strength", "removed: left over from before the rework"), Map.entry("sky.palette", "moved: the aurora's own colors"), Map.entry("sky.storm_color", "moved: the clouds' own colors"), Map.entry("held_item_outline.flame_speed", "removed: the flames use the shared color, strength and width"), Map.entry("held_item_outline.flame_turbulence", "removed: the flames use the shared color, strength and width"), Map.entry("held_item_outline.embers", "removed: the flames use the shared color, strength and width"), Map.entry("held_item_outline.wrap_flames", "removed: the flames use the shared color, strength and width"), Map.entry("held_item_outline.flame_layers", "removed: the flames use the shared color, strength and width"), Map.entry("held_item_outline.advanced_flames", "removed: the flames use the shared color, strength and width"), Map.entry("held_item_outline.flame_origin", "removed: the flames use the shared color, strength and width"), Map.entry("held_item_outline.flame_height", "removed: the flames use the shared color, strength and width"), Map.entry("sky.theme", "moved: the styles, and the painted sky under Advanced"), Map.entry("sky.volumetric_clouds", "removed: all clouds are volumetric"), Map.entry("ambience.petal_color", "removed with the petals"), Map.entry("held_item_outline.preview", "removed: the page shows the look live in the world"), Map.entry("arrow_trails.shots", "moved: its switches are rows of their own"), Map.entry("arrow_trails.fire_color", "the page has one color"), Map.entry("arrow_trails.lightning_color", "the page has one color"), Map.entry("arrow_trails.electric_color", "the page has one color"), Map.entry("swing_trail.enabled", "moved: arrow_trails.on_swings"), Map.entry("swing_trail.look", "moved: arrow_trails.swing_style"), Map.entry("swing_trail.color", "moved: arrow_trails.swing_color"), Map.entry("swing_trail.ghosts", "moved: arrow_trails.swing_count"), Map.entry("swing_trail.width", "moved: arrow_trails.swing_width"), Map.entry("swing_trail.after", "moved: arrow_trails.fade_time"), Map.entry("held_item_transform.hand_preview", "a preview box, which held no value: the changes show on the real item"), Map.entry("menus.world_panel", "nothing read it: removed by name"), Map.entry(		"menu.menu_side_x", "the side menu is gone: the window has one place and one size"), Map.entry("menu.menu_side_y", "the side menu is gone: the window has one place and one size"), Map.entry("menu.menu_side_scale", "the side menu is gone: the window has one place and one size")));

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	/** @return every key the settings file can hold, as feature.key, with the sub-keys of settings saved as a group */
	static TreeSet<String> keysNow() {
		TreeSet<String> keys = new TreeSet<>();
		for (Feature feature : FeatureManager.features()) {
			for (Map.Entry<String, JsonElement> entry : feature.save().entrySet()) {
				String key = feature.id() + "." + entry.getKey();
				keys.add(key);
			}
		}
		return keys;
	}

	@Test
	void everyKeyOfTheListIsStillThereOrNamedAsGone() throws IOException {
		TreeSet<String> now = keysNow();
		if (Boolean.getBoolean("lumio.updateSettingKeys") && !Files.exists(BASELINE)) {
			Files.createDirectories(BASELINE.getParent());
			Files.write(BASELINE, now, StandardCharsets.UTF_8);
		}
		List<String> lost = new ArrayList<>();
		for (String key : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
			if (!now.contains(key) && !GONE.containsKey(key)) {
				lost.add(key);
			}
		}
		assertEquals(List.of(), lost, "settings lost without a word");
	}

	@Test
	void whatIsNamedAsGoneIsReallyGone() {
		TreeSet<String> now = keysNow();
		for (String key : GONE.keySet()) {
			assertEquals(false, now.contains(key), key);
		}
	}
}
