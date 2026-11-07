package dev.tim1337.lumio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SkyBlock add-on is a project of its own (Lumio: Hypixel SkyBlock); the main mod contains nothing of it: no SkyBlock package, no
 * reference to one in its code or its API, and none of its resources. Checked on the sources, so a leftover fails the build.
 */
class AddonBoundaryTest {
	private static final String SKYBLOCK_PACKAGE = "dev.tim1337.lumio.skyblock";
	private static final Path MAIN = Path.of("src/main/java/dev/tim1337/lumio");
	private static final Path API = Path.of("api/src/main/java/dev/tim1337/lumio/api");
	private static final Path RESOURCES = Path.of("src/main/resources");

	@Test
	void theMainModHasNoSkyBlockPackage() {
		assertFalse(Files.exists(MAIN.resolve("skyblock")), "the SkyBlock code belongs to the add-on project");
	}

	@Test
	void neitherTheMainModNorTheApiUsesTheAddon() {
		Set<String> wrong = new TreeSet<>();
		for (Path root : List.of(MAIN, API)) {
			for (Path file : files(root, ".java")) {
				if (read(file).contains(SKYBLOCK_PACKAGE)) {
					wrong.add(root.relativize(file).toString());
				}
			}
		}
		assertTrue(wrong.isEmpty(), "These use the SkyBlock add-on:\n" + String.join("\n", wrong));
	}

	@Test
	void theCelebrationsAreTheAddons() {
		assertFalse(read(MAIN.resolve("menu/MenuFeature.java")).contains("elebrations"), "the setting belongs to the add-on");
	}

	@Test
	void theMainModHasNoSkyBlockResources() {
		assertFalse(Files.exists(RESOURCES.resolve("lumio-skyblock.mixins.json")), "the add-on's mixins belong to the add-on");
		assertFalse(Files.exists(RESOURCES.resolve("assets/lumio/skyblock")), "the add-on's data belongs to the add-on");
		assertFalse(read(RESOURCES.resolve("fabric.mod.json")).contains(SKYBLOCK_PACKAGE), "the main mod registers no SkyBlock entrypoint");
		assertFalse(read(RESOURCES.resolve("lumio.mixins.json")).contains("skyblock"), "the main mixins have no SkyBlock mixin");
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<Path> files(Path folder, String extension) {
		try (Stream<Path> files = Files.walk(folder)) {
			return files.filter(path -> path.toString().endsWith(extension)).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
