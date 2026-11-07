package dev.tim1337.lumio.config;

import dev.tim1337.lumio.hud.PanelLook;
import dev.tim1337.lumio.addon.AddonConfigs;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.features.visual.world.HeldItemTransformFeature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Holds every registered feature and persists their settings to {@code config/lumio/config.json}. */
public final class FeatureManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Feature> FEATURES = new ArrayList<>();

	private FeatureManager() {
	}

	public static <F extends Feature> F register(F feature) {
		FEATURES.add(feature);
		return feature;
	}

	/** The add-on each add-on feature belongs to, by feature; main mod features are not in it. */
	private static final Map<Feature, String> ADDON_OF = new HashMap<>();

	/** Registers a feature of an add-on at a place in the list (the sidebar lists pages in this order); its settings go to the add-on's file. */
	public static <F extends Feature> F registerAddonFeature(int index, F feature, String addonId) {
		FEATURES.add(Math.max(0, Math.min(FEATURES.size(), index)), feature);
		ADDON_OF.put(feature, addonId);
		return feature;
	}

	/** @return the id of the add-on a feature belongs to, or null for a feature of the main mod */
	public static String addonOf(Feature feature) {
		return ADDON_OF.get(feature);
	}

	public static List<Feature> features() {
		return FEATURES;
	}

	private static Path configPath() {
		return ModFiles.file("config.json");
	}

	/** Whether this is the first launch: no settings exist. */
	private static boolean freshInstall;

	public static boolean freshInstall() {
		return freshInstall;
	}

	/**
	 * Features whose settings could not be loaded: what the file had for them, and what they saved right after (their defaults). Their saved values
	 * are written back, with only what the player changed since put over them, so a feature that fails to load never wipes its settings.
	 */
	private static final Map<Feature, JsonObject[]> UNLOADED = new HashMap<>();

	public static void load() {
		Path path = configPath();
		freshInstall = !Files.exists(path);
		JsonObject main = read(path);
		// every add-on reads its own file
		Map<String, JsonObject> addons = new LinkedHashMap<>();
		for (Feature feature : FEATURES) {
			String addon = ADDON_OF.get(feature);
			if (addon != null && !addons.containsKey(addon)) {
				JsonObject own = read(AddonConfigs.fileOf(addon));
				if (own != null) {
					addons.put(addon, own);
				}
			}
		}
		if (main == null && addons.isEmpty()) {
			PanelLook.settleAll();
			return;
		}
		try {
			JsonObject root = AddonConfigMerge.merged(main, addons);
			ConfigUpgrade.upgrade(root);
			for (Feature feature : FEATURES) {
				JsonElement json = root.get(feature.id());
				if (json != null && json.isJsonObject()) {
					// one feature that cannot load keeps its defaults; every other one still loads, and its saved values stay in the file
					try {
						feature.load(json.getAsJsonObject());
					} catch (RuntimeException e) {
						Logs.warn("Could not load the settings of {}, it keeps its defaults", feature.id(), e);
						UNLOADED.put(feature, new JsonObject[]{json.getAsJsonObject().deepCopy(), feature.save()});
					}
				}
			}
		} catch (RuntimeException e) {
			Logs.warn("Could not read {}, using defaults", path.getFileName(), e);
		}
		PanelLook.settleAll();
	}

	/** @return a settings file, or null when there is none or it cannot be read */
	private static JsonObject read(Path path) {
		try {
			String text = ConfigFiles.read(path);
			return text == null ? null : GSON.fromJson(text, JsonObject.class);
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read {}, using defaults", path.getFileName(), e);
			ConfigFiles.keepUnreadable(path);
			return null;
		}
	}

	/** Keys that are never shared: private data (tokens, account ids) and state the game fills in by itself (pets seen, ...). */
	private static final Set<String> NOT_SHARED = Set.of("cider_token", "spotify_client_id", "pets", "last_pet", "pets_of_profiles", "config_name", "profit", "load_parts",
		// the player carried in carry mode is another player's name
		"carry_name",
		// the last server is private, and a foreign config must not be able to point the Rejoin button at another server
		"last_server_name", "last_server_ip", "saved_packs", "learned_cooldown_ms", "hidden_presets", "active_config", "config_colors", "hotm_tree", "loaded_print", "chosen_vinyl",
		// state observed in game (worn equipment, mineshaft pity) belongs to the player and changes while playing; it is not a setting
		"items", "last_mineshaft", "total", "widget_seen", "favorites", "pet_icons", "slayers", "borderless_active",
		// where the menu window is belongs to this screen
		"menu_window_x", "menu_window_y", "menu_window_size", "menu_pane_floating", "menu_pane_x", "menu_pane_y");

	/** Upper limit for decoded settings; real settings are a few tens of kilobytes. */
	private static final int MAX_SETTINGS_BYTES = 2 * 1024 * 1024;

	/** Prefix of encoded settings text: the format version. */
	private static final String PREFIX = "LUMIO1:";

	/** @return whether a key is private to the player (a token, observed game state), so configs neither export nor overwrite it */
	public static boolean isPrivate(String key) {
		return NOT_SHARED.contains(key);
	}

	/** @return all shareable settings as one line of text: compressed and Base64-encoded, so it is short and not plain JSON */
	public static String exportText() {
		return exportText(ConfigPart.all());
	}

	/** @return the shareable settings of the given parts only, as {@link #exportText()} writes them, such as the colors for a backup of them */
	public static String exportText(Set<ConfigPart> parts) {
		JsonObject root = new JsonObject();
		root.addProperty(ConfigUpgrade.VERSION_KEY, ConfigUpgrade.VERSION);
		for (Feature feature : FEATURES) {
			JsonObject json = feature.save();
			NOT_SHARED.forEach(json::remove);
			if (parts.size() < ConfigPart.values().length) {
				for (var setting : feature.settings()) {
					if (!parts.contains(ConfigPart.of(feature, setting.key(), setting))) {
						json.remove(setting.key());
					}
				}
			}
			if (!json.isEmpty()) {
				root.add(feature.id(), json);
			}
		}
		// store the GUI size the HUD positions refer to, so they can be fitted when the config is loaded on another screen
		int[] screen = parts.contains(ConfigPart.LAYOUT) ? guiScreen() : null;
		if (screen != null) {
			JsonObject size = new JsonObject();
			size.addProperty("w", screen[0]);
			size.addProperty("h", screen[1]);
			root.add(SCREEN_KEY, size);
		}
		return encode(new Gson().toJson(root));
	}

	/** Key under which a config stores the GUI size it was made on. */
	private static final String SCREEN_KEY = "_screen";
	/** Key under which a saved config keeps where it came from and when, see {@link ConfigStore}; it is not a setting. */
	public static final String META_KEY = "_meta";

	/** @return the current screen size in GUI units (which depend on resolution and GUI scale), or null */
	private static int[] guiScreen() {
		try {
			var window = Minecraft.getInstance().getWindow();
			int w = window.getGuiScaledWidth();
			int h = window.getGuiScaledHeight();
			return w > 0 && h > 0 ? new int[]{w, h} : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Fits the HUD layout of a config made on another screen to this one: horizontal and vertical distances are stretched with the screen's width
	 * and height, and sizes with the smaller of the two factors, so the layout looks the same.
	 */
	private static void fitLayout(JsonObject root, Feature feature, JsonObject wanted) {
		if (!root.has(SCREEN_KEY) || !root.get(SCREEN_KEY).isJsonObject() || feature instanceof HeldItemTransformFeature) {
			return;
		}
		int[] now = guiScreen();
		JsonObject from = root.getAsJsonObject(SCREEN_KEY);
		if (now == null || !from.has("w") || !from.has("h")) {
			return;
		}
		double oldW = from.get("w").getAsDouble();
		double oldH = from.get("h").getAsDouble();
		if (oldW < 50 || oldH < 50 || oldW == now[0] && oldH == now[1]) {
			return;
		}
		double rx = now[0] / oldW;
		double ry = now[1] / oldH;
		double rs = Math.min(rx, ry);
		for (var setting : feature.settings()) {
			JsonElement value = wanted.get(setting.key());
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
				|| ConfigPart.of(feature, setting.key(), setting) != ConfigPart.LAYOUT) {
				continue;
			}
			double ratio = switch (ConfigPart.axisOf(setting.key())) {
				case 'x' -> rx;
				case 'y' -> ry;
				case 's' -> rs;
				default -> 1.0;
			};
			if (ratio != 1.0) {
				double fitted = value.getAsDouble() * ratio;
				if (setting instanceof FloatSetting number) {
					fitted = Math.max(number.min(), Math.min(number.max(), fitted));
				}
				wanted.addProperty(setting.key(), fitted);
			}
		}
	}

	/** Encodes settings JSON as gzip + Base64, behind the format prefix. */
	public static String encode(String json) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream zip = new GZIPOutputStream(bytes)) {
				zip.write(json.getBytes(StandardCharsets.UTF_8));
			}
			return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** @return the JSON of text made by {@link #encode}, or the text itself when it already is plain JSON (a hand-made config) */
	public static String decode(String text) {
		String trimmed = text == null ? "" : text.strip();
		if (!trimmed.startsWith(PREFIX)) {
			return trimmed;
		}
		try {
			byte[] packed = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()).replaceAll("\\s", ""));
			try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
				// reject text that decompresses to more than real settings ever are: a few bytes can expand to gigabytes (zip bomb) and
				// exhaust the game's memory
				byte[] unpacked = zip.readNBytes(MAX_SETTINGS_BYTES + 1);
				if (unpacked.length > MAX_SETTINGS_BYTES) {
					return "";
				}
				return new String(unpacked, StandardCharsets.UTF_8);
			}
		} catch (IOException | IllegalArgumentException e) {
			return "";
		}
	}

	/** @return the settings of a text as plain JSON without never-shared keys, for comparing two configs */
	public static String comparable(String text) {
		try {
			JsonObject root = GSON.fromJson(decode(text), JsonObject.class);
			if (root == null) {
				return "";
			}
			// the screen size is not a setting: identical settings made on a differently sized window are equal
			root.remove(SCREEN_KEY);
			root.remove(META_KEY);
			root.remove(ConfigUpgrade.VERSION_KEY);
			for (var entry : root.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					NOT_SHARED.forEach(entry.getValue().getAsJsonObject()::remove);
				}
			}
			return new Gson().toJson(root);
		} catch (RuntimeException e) {
			return "";
		}
	}

	/** @return the parts a settings text has settings of: a backup of the colors has only colors, a whole config all three */
	public static Set<ConfigPart> partsIn(String text) {
		try {
			JsonObject root = GSON.fromJson(decode(text), JsonObject.class);
			return root == null ? EnumSet.noneOf(ConfigPart.class) : partsIn(root);
		} catch (RuntimeException e) {
			return EnumSet.noneOf(ConfigPart.class);
		}
	}

	/** @return the parts the settings of a decoded config have settings of */
	public static Set<ConfigPart> partsIn(JsonObject root) {
		Set<ConfigPart> found = EnumSet.noneOf(ConfigPart.class);
		try {
			for (Feature feature : FEATURES) {
				JsonElement json = root.get(feature.id());
				if (json == null || !json.isJsonObject()) {
					continue;
				}
				for (var setting : feature.settings()) {
					if (json.getAsJsonObject().has(setting.key()) && !NOT_SHARED.contains(setting.key())) {
						found.add(ConfigPart.of(feature, setting.key(), setting));
					}
				}
				if (found.size() == ConfigPart.values().length) {
					break;
				}
			}
		} catch (RuntimeException ignored) {
			// not settings: no parts
		}
		return found;
	}

	/** @return whether the text contains valid settings */
	public static boolean isSettings(String text) {
		try {
			JsonObject root = GSON.fromJson(decode(text), JsonObject.class);
			return root != null && FEATURES.stream().anyMatch(feature -> root.get(feature.id()) != null);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Imports settings text made by {@link #exportText()}. @return whether it was valid settings and was applied */
	public static boolean importText(String text) {
		return importText(text, ConfigPart.all());
	}

	/** Imports only the given parts of settings text; everything else stays unchanged. @return whether it was valid settings and was applied */
	public static boolean importText(String text, Set<ConfigPart> parts) {
		return importText(text, parts, feature -> true, false);
	}

	/**
	 * Imports the given parts, for the selected features only. With {@code keepSwitches} the enabled state of every feature is kept, so a preset can
	 * be applied on top of the current settings and layout while still deciding itself which features are on.
	 */
	public static boolean importText(String text, Set<ConfigPart> parts, Predicate<Feature> which, boolean keepSwitches) {
		boolean any = apply(text, parts, which, keepSwitches);
		if (any) {
			save();
		}
		return any;
	}

	/** Applies settings text as {@link #importText} does, without saving. @return whether any setting was applied */
	static boolean apply(String text, Set<ConfigPart> parts, Predicate<Feature> which, boolean keepSwitches) {
		try {
			JsonObject root = GSON.fromJson(decode(text), JsonObject.class);
			if (root == null || root.isEmpty() || parts.isEmpty()) {
				return false;
			}
			ConfigUpgrade.upgrade(root);
			boolean any = false;
			for (Feature feature : FEATURES) {
				JsonElement json = which.test(feature) ? root.get(feature.id()) : null;
				if (json != null && json.isJsonObject()) {
					JsonObject wanted = new JsonObject();
					for (var setting : feature.settings()) {
						JsonElement value = json.getAsJsonObject().get(setting.key());
						if (keepSwitches && setting.key().equals("enabled")) {
							continue;
						}
						// never-shared keys are never imported either: a hand-made config cannot set a token or the rejoin server
						if (value != null && !NOT_SHARED.contains(setting.key()) && parts.contains(ConfigPart.of(feature, setting.key(), setting))) {
							wanted.add(setting.key(), value);
						}
					}
					if (!wanted.isEmpty()) {
						if (parts.contains(ConfigPart.LAYOUT)) {
							fitLayout(root, feature, wanted);
						}
						feature.load(wanted);
						any = true;
					}
				}
			}
			PanelLook.settleAll();
			return any;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Time of the first unsaved change, or 0. */
	private static long dirtySince;

	/**
	 * Schedules a save instead of writing on every change: dragging a slider changes a setting many times per second, and writing the file each
	 * time is slow and wears the disk. {@link #flushIfDue()} does the writing.
	 */
	public static void saveSoon() {
		if (dirtySince == 0) {
			dirtySince = System.currentTimeMillis();
		}
	}

	/** Writes pending changes once they have been pending for a moment, so the file is written after a drag and not during it. Called every tick. */
	public static void flushIfDue() {
		if (dirtySince != 0 && System.currentTimeMillis() - dirtySince > 700) {
			save();
		}
	}

	/** Writes the settings file off the game's thread, one write at a time. */
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio settings");
		thread.setDaemon(true);
		return thread;
	});
	/** Numbers the saves, so an older state never overwrites a newer one (the save at quitting can overtake a queued one). */
	private static final AtomicLong SAVES = new AtomicLong();
	private static final Object WRITE_LOCK = new Object();
	/** The number of the save last written; guarded by {@link #WRITE_LOCK}. */
	private static long written;

	/** Saves the settings: they are collected now, and written to the file on a background thread. */
	public static void save() {
		Map<Path, String> text = settingsText();
		long number = SAVES.incrementAndGet();
		String caller = caller();
		WRITER.execute(() -> write(text, number, caller));
	}

	/** Saves the settings and writes them at once, on the calling thread: when the game closes, a background write might not run any more. */
	public static void saveNow() {
		write(settingsText(), SAVES.incrementAndGet(), caller());
	}

	/**
	 * @return the text of every settings file: the main file with the main mod's features, and one file per
	 *         add-on with its features
	 */
	private static Map<Path, String> settingsText() {
		dirtySince = 0;
		JsonObject main = new JsonObject();
		main.addProperty(ConfigUpgrade.VERSION_KEY, ConfigUpgrade.VERSION);
		Map<String, JsonObject> addons = new LinkedHashMap<>();
		for (Feature feature : FEATURES) {
			String addon = ADDON_OF.get(feature);
			if (addon == null) {
				main.add(feature.id(), saved(feature));
				continue;
			}
			addons.computeIfAbsent(addon, key -> {
				JsonObject root = new JsonObject();
				root.addProperty(ConfigUpgrade.VERSION_KEY, ConfigUpgrade.VERSION);
				return root;
			}).add(feature.id(), saved(feature));
		}
		Map<Path, String> files = new LinkedHashMap<>();
		files.put(configPath(), GSON.toJson(main));
		addons.forEach((addon, root) -> files.put(AddonConfigs.fileOf(addon), GSON.toJson(root)));
		return files;
	}

	/** @return who asked for a save, a few calls up, for the log of the write */
	private static String caller() {
		return StackWalker.getInstance().walk(frames -> frames.skip(2).limit(4)
			.map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName())
			.collect(java.util.stream.Collectors.joining(" < ")));
	}

	/** @return a feature's settings to write: its own, or for one that could not load what the file had, with what changed since put over it */
	private static JsonObject saved(Feature feature) {
		JsonObject now = feature.save();
		JsonObject[] kept = UNLOADED.get(feature);
		if (kept == null) {
			return now;
		}
		JsonObject result = kept[0].deepCopy();
		for (var setting : now.entrySet()) {
			if (!setting.getValue().equals(kept[1].get(setting.getKey()))) {
				result.add(setting.getKey(), setting.getValue());
			}
		}
		return result;
	}

	private static void write(Map<Path, String> files, long number, String caller) {
		synchronized (WRITE_LOCK) {
			if (number <= written) {
				return;
			}
			for (Map.Entry<Path, String> file : files.entrySet()) {
				try {
					ConfigFiles.write(file.getKey(), file.getValue(), caller);
				} catch (IOException e) {
					Logs.warn("Could not write {}", file.getKey().getFileName(), e);
				}
			}
			written = number;
		}
	}
}
