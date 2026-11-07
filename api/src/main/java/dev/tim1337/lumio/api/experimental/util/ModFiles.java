package dev.tim1337.lumio.api.experimental.util;

import dev.tim1337.lumio.api.LumioApi;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Locations of the mod's files: a dedicated {@code config/lumio/} folder instead of loose files in the game's config folder.
 */
public final class ModFiles {
	private ModFiles() {
	}

	public static Path dir() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(LumioApi.MOD_ID);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			Logs.warn("Could not make the folder {}", dir.getFileName(), e);
		}
		return dir;
	}

	public static Path file(String name) {
		return dir().resolve(name);
	}

	/** Writes a text file through a temporary file next to it, so quitting the game or a full disk during the write never leaves half a file. */
	public static void writeAtomically(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		// a name of its own for every write, so two games writing the same file never write into the same temporary file
		Path temporary = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
		Files.writeString(temporary, text);
		try {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
