package dev.tim1337.lumio.features.music;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lyrics in the world have no dark backdrop behind them in any situation, and no setting for one: their contrast comes from their own tones
 * ({@link LyricTones}). No class, field, method or constant of the music sources is named for one.
 */
class NoPlateTest {
	private static final Path MUSIC = Path.of("src/main/java/dev/tim1337/lumio/features/music");
	/** A plate in any spelling of a Java name: CutoutPlate, needsPlate, PLATE_SECONDS, plateHeight (not "template"). */
	private static final Pattern PLATE = Pattern.compile("Plate|PLATE|\bplate");

	@Test
	void noPlateIsLeftInTheMusicSources() throws IOException {
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MUSIC)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				if (PLATE.matcher(file.getFileName().toString()).find()) {
					found.add(file.getFileName().toString());
				}
				List<String> lines = Files.readAllLines(file);
				for (int i = 0; i < lines.size(); i++) {
					if (PLATE.matcher(lines.get(i)).find()) {
						found.add(file.getFileName() + ":" + (i + 1));
					}
				}
			}
		}
		assertEquals(List.of(), found, "a backdrop behind the lyrics in the world");
	}
}
