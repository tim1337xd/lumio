package dev.tim1337.lumio.config;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.config.setting.CommandBindsSetting;
import dev.tim1337.lumio.features.misc.keybinds.CommandBind;
import dev.tim1337.lumio.features.misc.keybinds.KeybindImport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Configs the player saved under a custom name, one file each in {@code config/lumio/configs/}, stored in the same compressed Base64
 * form used for sharing. A config can be loaded, copied to share it, renamed and deleted; a shared one can be imported from the clipboard. Each
 * file also notes where it came from (saved here, imported, or an automatic backup made before a look or a feature set) and when; the parts it
 * has settings of are read from its settings.
 */
public final class ConfigStore {
	private static final String EXTENSION = ".lumioconfig";
	/** Characters no file name may have (path separators, Windows' reserved ones, control characters), runs of white space, the names Windows
	 * reserves for devices, and names of only dots. */
	private static final Pattern NOT_IN_FILE_NAMES = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final Pattern DEVICE_NAME = Pattern.compile("(?i)(con|prn|aux|nul|com\\d|lpt\\d)");
	private static final Pattern ONLY_DOTS = Pattern.compile("\\.+");
	/** The longest name of a saved config, in characters. */
	private static final int LONGEST_NAME = 40;
	/** How many automatic backups are kept: making a new one removes the oldest beyond this. */
	private static final int MAX_AUTOMATIC = 3;
	private static final String SOURCE = "source";
	private static final String TIME = "time";

	/** Where a saved config came from. */
	public enum Source {
		SAVED, IMPORTED, AUTOMATIC
	}

	/** A saved config: its name, its file, where it came from, when it was saved (in milliseconds) and the parts it has settings of. */
	public record Saved(String name, Path file, Source source, long time, Set<ConfigPart> parts) {
		public boolean automatic() {
			return source == Source.AUTOMATIC;
		}
	}

	private static List<Saved> cached;

	private ConfigStore() {
	}

	private static Path dir() {
		Path dir = ModFiles.file("configs");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			Logs.warn("Could not make the folder {}", dir.getFileName(), e);
		}
		return dir;
	}

	/** @return the name with every character that is not allowed in file names removed */
	static String fileName(String name) {
		String safe = SPACES.matcher(NOT_IN_FILE_NAMES.matcher(name.strip()).replaceAll("")).replaceAll(" ");
		if (safe.length() > LONGEST_NAME) {
			safe = safe.substring(0, LONGEST_NAME).strip();
		}
		// reserved Windows device names and dot-only names are not valid file names
		if (DEVICE_NAME.matcher(safe).matches() || ONLY_DOTS.matcher(safe).matches()) {
			safe = "_" + safe;
		}
		return safe;
	}

	/** @return the saved configs, newest first; read once and kept until a config is written or deleted */
	public static List<Saved> list() {
		if (cached != null) {
			return cached;
		}
		List<Saved> found = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir())) {
			files.filter(path -> path.getFileName().toString().endsWith(EXTENSION)).forEach(path -> found.add(describe(path)));
		} catch (IOException e) {
			Logs.warn("Could not read the saved configs", e);
		}
		found.sort(Comparator.comparingLong(Saved::time).reversed());
		cached = found;
		return found;
	}

	/** Reads what a config file is: its note of where it came from (without one: saved, at the file's time) and its parts. */
	private static Saved describe(Path path) {
		String file = path.getFileName().toString();
		String name = file.substring(0, file.length() - EXTENSION.length());
		long time = 0;
		Source source = Source.SAVED;
		Set<ConfigPart> parts = EnumSet.noneOf(ConfigPart.class);
		try {
			time = Files.getLastModifiedTime(path).toMillis();
			JsonObject root = parse(Files.readString(path));
			if (root != null) {
				parts = FeatureManager.partsIn(root);
				if (root.get(FeatureManager.META_KEY) instanceof JsonObject meta) {
					source = sourceOf(meta.get(SOURCE));
					if (meta.get(TIME) instanceof JsonElement stamp && stamp.isJsonPrimitive() && stamp.getAsJsonPrimitive().isNumber()) {
						time = stamp.getAsLong();
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the config {}", name, e);
		}
		return new Saved(name, path, source, time, parts);
	}

	private static Source sourceOf(JsonElement value) {
		if (value != null && value.isJsonPrimitive()) {
			for (Source source : Source.values()) {
				if (source.name().equalsIgnoreCase(value.getAsString())) {
					return source;
				}
			}
		}
		return Source.SAVED;
	}

	/** @return the settings of a config text as JSON, or null when it is not settings */
	private static JsonObject parse(String text) {
		try {
			return JsonParser.parseString(FeatureManager.decode(text)) instanceof JsonObject root ? root : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** @return the settings text with its note of where it came from and when, encoded */
	private static String withNote(String text, Source source, long time) {
		JsonObject root = parse(text);
		if (root == null) {
			return text;
		}
		JsonObject meta = new JsonObject();
		meta.addProperty(SOURCE, source.name().toLowerCase(Locale.ROOT));
		meta.addProperty(TIME, time);
		root.add(FeatureManager.META_KEY, meta);
		return FeatureManager.encode(root.toString());
	}

	/** Saves the current settings under the name, replacing a config of the same name. @return whether it was saved */
	public static boolean save(String name) {
		return write(name, withNote(FeatureManager.exportText(), Source.SAVED, System.currentTimeMillis()));
	}

	/** Saves settings text (from the clipboard) under the name. @return whether the text was valid settings and was saved */
	public static boolean add(String name, String text) {
		if (!FeatureManager.isSettings(text)) {
			return false;
		}
		// always stored encoded, also when it came as plain JSON
		return write(name, withNote(FeatureManager.encode(FeatureManager.decode(text)), Source.IMPORTED, System.currentTimeMillis()));
	}

	/**
	 * Saves settings text as an automatic backup, such as the colors before a look was kept, under a free variant of the name. Only the newest
	 * few automatic backups are kept. @return the backup, or null if it could not be written
	 */
	public static Saved saveAutomatic(String name, String text) {
		String free = freeName(name);
		if (!write(free, withNote(text, Source.AUTOMATIC, System.currentTimeMillis()))) {
			return null;
		}
		Saved made = null;
		int kept = 0;
		for (Saved saved : List.copyOf(list())) {
			if (!saved.automatic()) {
				continue;
			}
			if (saved.name().equals(free)) {
				made = saved;
			}
			if (++kept > MAX_AUTOMATIC) {
				delete(saved);
			}
		}
		return made;
	}

	/** Writes a config through a temporary file, so a failed write never leaves half a config behind. */
	private static boolean write(String name, String text) {
		String file = fileName(name);
		if (file.isEmpty()) {
			return false;
		}
		Path target = dir().resolve(file + EXTENSION);
		try {
			ModFiles.writeAtomically(target, text);
			cached = null;
			return true;
		} catch (IOException e) {
			Logs.warn("Could not save the config {}", file, e);
			return false;
		}
	}

	/** @return the shareable text of a saved config, or null if it cannot be read */
	public static String read(Saved saved) {
		try {
			return Files.readString(saved.file()).strip();
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Loads a config, the parts chosen in "What is loaded". The keybinds of a config someone else shared are left out, unless the player chose
	 * to import them too; then they are added to the player's own (see {@link KeybindImport}).
	 */
	public static boolean load(Saved saved, boolean withKeybinds) {
		String text = read(saved);
		if (text == null) {
			return false;
		}
		Set<ConfigPart> parts = ModFeatures.CONFIGS.selectedParts();
		if (saved.source() != Source.IMPORTED) {
			return FeatureManager.importText(text, parts);
		}
		boolean loaded = FeatureManager.importText(text, parts, KeybindImport.withoutKeybinds(), false);
		if (!withKeybinds || !parts.contains(ConfigPart.FEATURES)) {
			return loaded;
		}
		int added = ModFeatures.COMMAND_KEYBINDS.addKeybinds(keybindsToAdd(text));
		if (added > 0) {
			FeatureManager.save();
		}
		return loaded || added > 0;
	}

	/** @return the keybinds a config someone else shared would add to the player's; empty for the player's own configs */
	public static List<CommandBind> keybindsToAdd(Saved saved) {
		String text = saved.source() == Source.IMPORTED ? read(saved) : null;
		return text == null ? List.of() : keybindsToAdd(text);
	}

	private static List<CommandBind> keybindsToAdd(String text) {
		return KeybindImport.toAdd(ModFeatures.COMMAND_KEYBINDS.binds().binds(), KeybindImport.inConfig(parse(text)), CommandBindsSetting.MOST);
	}

	/** Restores a backup: all it has, which is only the part it was made of. */
	public static boolean restore(Saved saved) {
		String text = read(saved);
		return text != null && FeatureManager.importText(text, ConfigPart.all());
	}

	/** Renames a config, keeping its settings; an automatic backup becomes a config of the player's. @return the new name, or null on failure */
	public static String rename(Saved saved, String wanted) {
		String text = read(saved);
		if (text == null) {
			return null;
		}
		String name = freeName(wanted);
		if (!write(name, saved.automatic() ? withNote(text, Source.SAVED, saved.time()) : text)) {
			return null;
		}
		delete(saved);
		return name;
	}

	public static void delete(Saved saved) {
		try {
			Files.deleteIfExists(saved.file());
		} catch (IOException e) {
			Logs.warn("Could not delete the config {}", saved.name(), e);
		}
		cached = null;
	}

	/** @return the wanted name, or the first free variant of it: "Imported", "Imported 2", ... */
	public static String freeName(String wanted) {
		String base = fileName(wanted).isEmpty() ? "My config" : fileName(wanted);
		String name = base;
		int number = 2;
		while (Files.exists(dir().resolve(name + EXTENSION))) {
			name = base + " " + number++;
		}
		return name;
	}
}
