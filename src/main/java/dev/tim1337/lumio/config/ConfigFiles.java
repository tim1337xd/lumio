package dev.tim1337.lumio.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Reading and writing the settings files safely:
 * <ul>
 *   <li>before the first write of a session the file as it was is kept beside it ({@code config.bak}), and a file that could not be read at all is kept
 *   as {@code config.unreadable.bak}, so nothing the player set is ever lost to a write;</li>
 *   <li>when another game changed the file since this one read or wrote it (two games of the same instance), only what this game changed is
 *   written over it, so neither undoes the other's changes;</li>
 *   <li>every write is logged with the instance, the version of the game, each value changed (old → new, private ones hidden) and who saved.</li>
 * </ul>
 */
final class ConfigFiles {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MOST_LOGGED_CHANGES = 24;
	private static final int MOST_VALUE_CHARS = 60;
	/** The text of each file as this game last read or wrote it: what another game changed is told apart from it. */
	private static final Map<Path, String> KNOWN = new ConcurrentHashMap<>();
	private static final Set<Path> BACKED_UP = ConcurrentHashMap.newKeySet();

	private ConfigFiles() {
	}

	/** @return the text of a settings file, or null when there is none; remembered as what this game knows of it */
	static String read(Path file) throws IOException {
		if (!Files.exists(file)) {
			return null;
		}
		String text = Files.readString(file);
		KNOWN.put(file, text);
		return text;
	}

	/** Keeps a copy of a file that could not be read, before anything is written over it. */
	static void keepUnreadable(Path file) {
		try {
			Files.copy(file, sibling(file, ".unreadable.bak"), StandardCopyOption.REPLACE_EXISTING);
			Logs.warn("Kept the settings file that could not be read as {}", sibling(file, ".unreadable.bak").getFileName());
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not keep a copy of {}", file.getFileName(), e);
		}
	}

	/**
	 * Writes a settings file: merged with another game's changes when there are any, after a backup of the file as it was, and logged.
	 *
	 * @param caller who asked for the save, for the log
	 */
	static void write(Path file, String text, String caller) throws IOException {
		String disk = Files.exists(file) ? Files.readString(file) : null;
		String known = KNOWN.get(file);
		String result = text;
		if (disk != null && known != null && !disk.equals(known)) {
			result = merged(known, disk, text);
			Logs.info("Settings: {} was changed by another game since this one read it; only this game's changes are written over it", file.getFileName());
		}
		if (result.equals(disk)) {
			KNOWN.put(file, result);
			return;
		}
		if (disk != null && BACKED_UP.add(file)) {
			Files.copy(file, sibling(file, ".bak"), StandardCopyOption.REPLACE_EXISTING);
		}
		Logs.info("Settings saved to {} ({}, Minecraft {}): {}; from {}", file.getFileName(), instance(), minecraftVersion(), changes(disk, result), caller);
		ModFiles.writeAtomically(file, result);
		KNOWN.put(file, result);
	}

	/**
	 * @param base   the file as this game last knew it
	 * @param theirs the file as another game left it
	 * @param ours   what this game wants to write
	 * @return theirs, with every setting this game changed since base set to this game's value
	 */
	static String merged(String base, String theirs, String ours) {
		JsonObject baseRoot = parse(base);
		JsonObject theirRoot = parse(theirs);
		JsonObject ourRoot = parse(ours);
		if (theirRoot == null || ourRoot == null) {
			return ours;
		}
		JsonObject result = theirRoot.deepCopy();
		for (Map.Entry<String, JsonElement> part : ourRoot.entrySet()) {
			JsonElement before = baseRoot == null ? null : baseRoot.get(part.getKey());
			if (part.getValue() instanceof JsonObject ourPart && before instanceof JsonObject basePart && result.get(part.getKey()) instanceof JsonObject resultPart) {
				for (Map.Entry<String, JsonElement> setting : ourPart.entrySet()) {
					if (!Objects.equals(setting.getValue(), basePart.get(setting.getKey()))) {
						resultPart.add(setting.getKey(), setting.getValue());
					}
				}
			} else if (!Objects.equals(part.getValue(), before)) {
				result.add(part.getKey(), part.getValue());
			}
		}
		return GSON.toJson(result);
	}

	/** @return the settings that differ between two texts of a file, as "feature.key old → new", private values hidden */
	static String changes(String before, String after) {
		JsonObject old = before == null ? new JsonObject() : parse(before);
		JsonObject now = parse(after);
		if (old == null || now == null) {
			return before == null ? "new file" : "whole file";
		}
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, JsonElement> part : now.entrySet()) {
			JsonElement was = old.get(part.getKey());
			if (part.getValue() instanceof JsonObject nowPart && was instanceof JsonObject oldPart) {
				for (Map.Entry<String, JsonElement> setting : nowPart.entrySet()) {
					JsonElement oldValue = oldPart.get(setting.getKey());
					if (!Objects.equals(setting.getValue(), oldValue)) {
						lines.add(part.getKey() + "." + setting.getKey() + " " + shown(setting.getKey(), oldValue) + " → " + shown(setting.getKey(), setting.getValue()));
					}
				}
			} else if (!Objects.equals(part.getValue(), was)) {
				lines.add(part.getKey() + " " + shown(part.getKey(), was) + " → " + shown(part.getKey(), part.getValue()));
			}
		}
		if (lines.isEmpty()) {
			return "no values changed";
		}
		int more = lines.size() - MOST_LOGGED_CHANGES;
		String text = String.join(", ", lines.subList(0, Math.min(lines.size(), MOST_LOGGED_CHANGES)));
		return more > 0 ? text + " and " + more + " more" : text;
	}

	private static String shown(String key, JsonElement value) {
		if (value == null) {
			return "(none)";
		}
		if (FeatureManager.isPrivate(key)) {
			return "(private)";
		}
		String text = value.toString();
		return text.length() > MOST_VALUE_CHARS ? text.substring(0, MOST_VALUE_CHARS) + "…" : text;
	}

	private static JsonObject parse(String text) {
		try {
			return GSON.fromJson(text, JsonObject.class);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** @return config.json → config.bak (with ".bak") beside it */
	private static Path sibling(Path file, String suffix) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return file.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + suffix);
	}

	/** @return the name of the instance's folder, as the launcher calls it (the folder above the game's folder) */
	private static String instance() {
		Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath();
		Path above = game.getParent();
		return "instance " + (above == null || above.getFileName() == null ? game.getFileName() : above.getFileName());
	}

	private static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}
}
