package dev.tim1337.lumio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every key the mod registers is a key field in its feature's own settings: the class that makes a key hands it to {@code addKey} or
 * {@code addMenuKey} (itself, or through the feature named here). Such a field is a {@code KeySetting}, and every {@code KeySetting} is
 * also a row of the Keybinds page (they are all in {@code KeySetting.all()}), so both places exist for every key. Neither holds a value
 * of its own: both read and write the game's own key mapping, so they always edit the same key.
 */
public class KeyFieldsTest {
	private static final String ROOT = "src/main/java";
	/** Classes that make keys for a feature: the class that lists them in the feature's settings. */
	private static final Map<String, String> LISTED_BY = Map.of("MusicKeys.java", "MusicFeature.java", "CinematicKeys.java", "CinematicCameraFeature.java",
		"WaypointPlacing.java", "WaypointsFeature.java");

	/** @return the source files under a root, by file name */
	public static List<Path> sources(String root) throws IOException {
		try (Stream<Path> files = Files.walk(Path.of(root))) {
			return files.filter(path -> path.toString().endsWith(".java")).toList();
		}
	}

	public static boolean lists(String source) {
		return source.contains("addKey(") || source.contains("addMenuKey(");
	}

	@Test
	void everyKeyIsAFieldInItsFeaturesSettings() throws IOException {
		List<Path> all = sources(ROOT);
		List<String> missing = new ArrayList<>();
		for (Path file : all) {
			String source = Files.readString(file);
			if (!source.contains("new KeyMapping(")) {
				continue;
			}
			String name = file.getFileName().toString();
			String holder = LISTED_BY.getOrDefault(name, name);
			Path holderFile = all.stream().filter(path -> path.getFileName().toString().equals(holder)).findFirst().orElse(null);
			if (holderFile == null || !lists(Files.readString(holderFile))) {
				missing.add(name);
			}
		}
		assertEquals(List.of(), missing, "classes that make a key which is no field in a feature's settings");
	}

	@Test
	void aKeyFieldKeepsNoValueOfItsOwn() throws IOException {
		// the field in a feature's sheet and the row of the Keybinds page are the same kind of setting over the game's key mapping
		String keySetting = Files.readString(Path.of(ROOT, "dev/tim1337/lumio/config/setting/KeySetting.java"));
		assertTrue(keySetting.contains("extends Setting<Void>"), "a key field stores nothing in the mod's config");
		assertTrue(keySetting.contains("ALL.add(this)"), "every key field is listed for the Keybinds page");
	}
}
