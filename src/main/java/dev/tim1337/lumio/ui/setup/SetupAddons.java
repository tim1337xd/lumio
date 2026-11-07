package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;

import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Which add-ons the player has already seen in the setup, in a small file next to the setup's marker. An add-on with a part in the setup that is not
 * listed was installed after the setup was finished, and its part is shown once on its own. The file is only read and written off the game's thread.
 */
final class SetupAddons {
	private static final String FILE = "setup_addons";
	/** A real list is a few short lines; anything longer is not read. */
	private static final long MOST_BYTES = 8192L;
	private static final int MOST_IDS = 64;

	private SetupAddons() {
	}

	/** @return the add-on ids in the file's text, one per line */
	static Set<String> parse(String text) {
		Set<String> ids = new LinkedHashSet<>();
		for (String line : text.split("\\R")) {
			String id = line.strip();
			if (!id.isEmpty() && ids.size() < MOST_IDS) {
				ids.add(id);
			}
		}
		return ids;
	}

	/**
	 * Asks whether an add-on's part is new to the player. A finished setup without the file comes from before add-ons were remembered: what is
	 * installed now counts as seen, so nobody is asked about what they already set up.
	 *
	 * @return whether its part should be shown
	 */
	static CompletableFuture<Boolean> isNew(String addonId) {
		return CompletableFuture.supplyAsync(() -> {
			Path file = ModFiles.file(FILE);
			if (!Files.exists(file)) {
				write(Set.of(addonId));
				return false;
			}
			return !read(file).contains(addonId);
		}, Util.ioPool());
	}

	/** Remembers that the setup is done with an add-on, or with none (null): the list is then there for an add-on installed later. */
	static void remember(String addonId) {
		CompletableFuture.runAsync(() -> {
			Set<String> ids = read(ModFiles.file(FILE));
			if (addonId != null) {
				ids.add(addonId);
			}
			write(ids);
		}, Util.ioPool());
	}

	private static Set<String> read(Path file) {
		try {
			if (Files.exists(file) && Files.size(file) <= MOST_BYTES) {
				return parse(Files.readString(file));
			}
		} catch (IOException error) {
			Logs.warn("Could not read the add-ons of the setup", error);
		}
		return new LinkedHashSet<>();
	}

	private static void write(Set<String> ids) {
		try {
			ModFiles.writeAtomically(ModFiles.file(FILE), String.join("\n", ids));
		} catch (IOException error) {
			Logs.warn("Could not write the add-ons of the setup", error);
		}
	}
}
