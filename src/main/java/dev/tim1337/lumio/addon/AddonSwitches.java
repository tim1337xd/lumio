package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Which add-ons are switched off, from the Add-ons page: saved in config/lumio/addons/state.json and read once at the start, since an
 * add-on is only loaded or left out while the game starts. A switch therefore takes effect after a restart.
 */
public final class AddonSwitches {
	private static final String FILE = "state.json";
	private static final String DISABLED = "disabled";
	/** The most add-ons the file lists; it never needs more. */
	private static final int MOST = 64;
	private static final int LONGEST_ID = 64;

	/** Writes the file off the game's thread, one write at a time. */
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio add-on switches");
		thread.setDaemon(true);
		return thread;
	});

	private static Set<String> saved;

	private AddonSwitches() {
	}

	/** @return the ids of the add-ons that are off: the saved ones and those in the comma separated list (the system property) */
	static Set<String> disabledIds(String property) {
		Set<String> off = new LinkedHashSet<>(saved());
		for (String part : property.split(",")) {
			String id = clean(part);
			if (!id.isEmpty()) {
				off.add(id);
			}
		}
		return off;
	}

	/** @return whether the add-on is switched on on the Add-ons page */
	public static boolean enabled(String id) {
		return !saved().contains(clean(id));
	}

	/** Switches an add-on on or off; it takes effect at the next start. */
	public static void setEnabled(String id, boolean enabled) {
		Set<String> off = saved();
		boolean changed = enabled ? off.remove(clean(id)) : off.size() < MOST && off.add(clean(id));
		if (!changed) {
			return;
		}
		JsonArray list = new JsonArray();
		off.forEach(list::add);
		JsonObject root = new JsonObject();
		root.add(DISABLED, list);
		String text = root.toString();
		// written off the game's thread; one writer, so the last switch is the one that stays
		WRITER.execute(() -> {
			try {
				ModFiles.writeAtomically(file(), text);
			} catch (IOException e) {
				Logs.warn("Could not save which add-ons are on", e);
			}
		});
	}

	private static Set<String> saved() {
		if (saved != null) {
			return saved;
		}
		saved = new LinkedHashSet<>();
		Path file = file();
		if (!Files.exists(file)) {
			return saved;
		}
		try {
			if (JsonParser.parseString(Files.readString(file)) instanceof JsonObject root && root.get(DISABLED) instanceof JsonArray list) {
				for (JsonElement element : list) {
					if (saved.size() < MOST && element.isJsonPrimitive()) {
						saved.add(clean(element.getAsString()));
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read which add-ons are on; all are on", e);
		}
		return saved;
	}

	private static Path file() {
		return ModFiles.file(AddonConfigs.FOLDER).resolve(FILE);
	}

	/** @return an add-on id from outside data: lower case, only letters, digits, "-" and "_", and not too long */
	static String clean(String id) {
		StringBuilder out = new StringBuilder();
		for (char c : id.strip().toLowerCase(Locale.ROOT).toCharArray()) {
			if (out.length() < LONGEST_ID && (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_')) {
				out.append(c);
			}
		}
		return out.toString();
	}
}
