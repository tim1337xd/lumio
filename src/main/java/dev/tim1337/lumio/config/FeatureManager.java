package dev.tim1337.lumio.config;

import dev.tim1337.lumio.hud.PanelLook;
import dev.tim1337.lumio.addon.AddonConfigs;
import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.Threads;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.features.visual.world.HeldItemTransformFeature;

import com.google.gson.Gson;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Holds every registered feature and persists their settings to {@code config/lumio/config.json}. */
public final class FeatureManager {
	private static final Gson GSON = ConfigFiles.GSON;
	/** Writes JSON on one line, for text that is shared or compared and not read in a file. */
	private static final Gson COMPACT = new Gson();
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

	/** @return the features with a switch of their own that are on now: what "turn everything off" switches off */
	public static List<Feature> switchedOn() {
		List<Feature> on = new java.util.ArrayList<>();
		for (Feature feature : features()) {
			if (feature.isSwitchable() && !feature.isChild() && feature.isAnyOn()) {
				on.add(feature);
			}
		}
		return on;
	}

	/**
	 * Switches off every feature that is on; their settings, the HUD layout and the keys stay as they are.
	 *
	 * @return what was switched off, to put back exactly (a feature made of parts gets back the parts that were on, not all of them)
	 */
	public static SwitchedOff switchAllOff() {
		List<Feature> on = switchedOn();
		Map<Feature, Boolean> before = new java.util.LinkedHashMap<>();
		for (Feature feature : on) {
			if (feature.children().isEmpty()) {
				before.put(feature, feature.ownSwitch());
			} else {
				for (Feature part : feature.children()) {
					before.put(part, part.ownSwitch());
				}
			}
			feature.setEnabled(false);
		}
		return new SwitchedOff(before, on.size());
	}

	/** What "turn everything off" switched off: each switch as it was, and how many features were on. */
	public static final class SwitchedOff {
		private final Map<Feature, Boolean> before;
		private final int features;

		private SwitchedOff(Map<Feature, Boolean> before, int features) {
			this.before = before;
			this.features = features;
		}

		/** @return how many features were on and are off now */
		public int features() {
			return features;
		}

		/** Puts every switch back as it was before; features that were off stay off. */
		public void restore() {
			before.forEach(Feature::setOwnSwitch);
		}
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
	private static final Map<Feature, Unloaded> UNLOADED = new HashMap<>();

	/** What the file had for a feature that could not load, and what the feature saved right after: its defaults. */
	private record Unloaded(JsonObject fromFile, JsonObject defaults) {
	}

	/** The shipped default settings, in the main mod's assets and in each add-on's: {@code /assets/<mod or add-on id>/defaults.json}. */
	private static final String SHIPPED_DEFAULTS = "defaults.json";
	/** The shipped settings a fresh install starts with besides the defaults, beside them: {@code /assets/<mod or add-on id>/defaults-fresh.json}. */
	private static final String FRESH_INSTALL_SETTINGS = "defaults-fresh.json";

	/** @return whether the game tests run, which keep the defaults of the code their scenes count on */
	private static boolean codeDefaultsOnly() {
		return Boolean.getBoolean("lumio.test.codeDefaults");
	}

	/**
	 * Takes the shipped default settings as the features' defaults, before any saved file is read: so they are what a fresh install starts with and
	 * what "reset to default" goes back to. A player's saved values are read over them as before, and every value is saved, so nobody who has
	 * played before sees a setting change by itself. Private values, debugging tools and HUD positions are never taken from it.
	 */
	static void applyShippedDefaults() {
		if (!codeDefaultsOnly()) {
			takeShipped(SHIPPED_DEFAULTS, addon -> true, true);
		}
	}

	/**
	 * On a true fresh install only (no settings file of that part yet), sets the shipped fresh-install settings as values, not as defaults: settings
	 * a player of an earlier version keeps at their old default (they are new since, and nothing may change by itself for that player) start set
	 * as shipped here. The same values are left out as of the defaults.
	 *
	 * @param fresh whether a part has no settings file yet, by its add-on id (null for the main mod)
	 */
	static void applyFreshInstallSettings(Predicate<String> fresh) {
		if (!codeDefaultsOnly()) {
			takeShipped(FRESH_INSTALL_SETTINGS, fresh, false);
		}
	}

	/**
	 * @param mainFileExists whether the main settings file exists
	 * @param addonFileExists whether an add-on's own settings file exists, by its id
	 * @return whether a part (by add-on id, null for the main mod) is freshly installed: no settings file of the main mod, and none of its own
	 */
	static Predicate<String> freshParts(boolean mainFileExists, Predicate<String> addonFileExists) {
		return addon -> !mainFileExists && (addon == null || !addonFileExists.test(addon));
	}

	/** Reads a shipped settings file of each part named by {@code parts} into its features' settings, as values and with {@code asDefault} also as defaults. */
	private static void takeShipped(String name, Predicate<String> parts, boolean asDefault) {
		Map<String, JsonObject> files = new HashMap<>();
		for (Feature feature : FEATURES) {
			String addon = ADDON_OF.get(feature);
			if (!parts.test(addon)) {
				continue;
			}
			String resource = "/assets/" + (addon == null ? LumioApi.MOD_ID : addon) + "/" + name;
			JsonObject file = files.computeIfAbsent(resource, FeatureManager::shippedDefaults);
			JsonElement own = file.get(feature.id());
			if (own == null || !own.isJsonObject()) {
				continue;
			}
			for (dev.tim1337.lumio.config.setting.Setting<?> setting : feature.settings()) {
				JsonElement value = own.getAsJsonObject().get(setting.key());
				if (value == null || isPrivate(feature.id(), setting.key()) || ConfigPart.of(feature, setting.key(), setting) == ConfigPart.LAYOUT) {
					continue;
				}
				try {
					setting.load(value);
					if (asDefault) {
						setting.adoptAsDefault();
					}
				} catch (RuntimeException e) {
					Logs.warn("Could not take the shipped setting {} of {}.{}", name, feature.id(), setting.key(), e);
				}
			}
		}
	}

	/** @return a shipped defaults file, or an empty one when there is none or it cannot be read */
	private static JsonObject shippedDefaults(String resource) {
		try (var in = FeatureManager.class.getResourceAsStream(resource)) {
			if (in == null) {
				return new JsonObject();
			}
			JsonObject json = GSON.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
			return json == null ? new JsonObject() : json;
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the shipped defaults {}", resource, e);
			return new JsonObject();
		}
	}

	public static void load() {
		applyShippedDefaults();
		Path path = configPath();
		freshInstall = !Files.exists(path);
		applyFreshInstallSettings(freshParts(!freshInstall, addon -> Files.exists(AddonConfigs.fileOf(addon))));
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
						UNLOADED.put(feature, new Unloaded(json.getAsJsonObject().deepCopy(), feature.save()));
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

	/**
	 * Settings that are never shared, as {@code feature.key}: private data (tokens, account ids, other players' names) and state the game fills in
	 * by itself (pets seen, ...). Each with its feature, so a new setting of the same name elsewhere is shared as usual.
	 */
	private static final Set<String> NOT_SHARED = Set.of("music.cider_token", "music.spotify_client_id", "pet_hud.pets", "pet_hud.last_pet", "pet_hud.pets_of_profiles",
		"pet_hud.pet_icons", "configs.load_parts", "configs.active_config", "configs.config_colors", "configs.loaded_print",
		// other players' names: the player carried in carry mode, and the party commands' whitelist and blocked players
		"slayer_phase.carry_name", "party_commands.whitelist", "party_commands.blocked",
		// the last server is private, and a foreign config must not be able to point the Rejoin button at another server
		"menus.last_server_name", "menus.last_server_ip", "keep_server_packs.saved_packs", "mining_ability.learned_cooldown_ms", "hotm_tree_hud.hotm_tree",
		"farming_hud.chosen_vinyl",
		// what the sky quality Auto measured belongs to this computer
		"sky.auto_quality",
		// state observed in game (profits, worn equipment, mineshaft pity, sea creatures) belongs to the player and changes while playing; it is not a setting
		"mining_profit.profit", "farming_profit.profit", "slayer_profit.profit", "slayer_profit.slayers", "fishing_profit.profit", "experiment_profit.profit",
		"dragon_profit.profit", "corpse_profit.profit", "equipment_hud.items", "mineshaft_pity.last_mineshaft", "mineshaft_pity.total", "mineshaft_pity.widget_seen",
		"sea_creatures.total", "item_list.favorites", "borderless.borderless_active",
		// where the menu window is belongs to this screen
		"menu.menu_window_x", "menu.menu_window_y", "menu.menu_window_size", "menu.menu_pane_floating", "menu.menu_pane_x", "menu.menu_pane_y", "menu.whats_new_seen");
	/** A feature whose id starts so is a debugging tool (the Debug log, a debug add-on's): none of it is shared, so a config never turns one on. */
	private static final String DEBUG_PREFIX = "debug_";

	/** Upper limit for decoded settings; real settings are a few tens of kilobytes. */
	private static final int MAX_SETTINGS_BYTES = 2 * 1024 * 1024;

	/** Prefix of encoded settings text: the format version. */
	private static final String PREFIX = "LUMIO1:";

	/**
	 * @param feature the feature's id, or null for a value that belongs to none
	 * @return whether a setting is private to the player (a token, observed game state) or a debugging tool's, so configs neither export nor overwrite it
	 */
	public static boolean isPrivate(String feature, String key) {
		return feature != null && (feature.startsWith(DEBUG_PREFIX) || NOT_SHARED.contains(feature + "." + key));
	}

	/** Takes the private settings of a feature out of its saved values. */
	private static void removePrivate(String feature, JsonObject json) {
		json.keySet().removeIf(key -> isPrivate(feature, key));
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
			removePrivate(feature.id(), json);
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
		return encode(COMPACT.toJson(root));
	}

	/** Key under which a config stores the GUI size it was made on. */
	private static final String SCREEN_KEY = "_screen";
	/** Key under which a saved config keeps where it came from and when, see {@link ConfigStore}; it is not a setting. */
	public static final String META_KEY = "_meta";

	/** The screen size in GUI units unit tests apply configs on, which have no game window; null in the game. */
	static int[] screenForTests;

	/** @return the current screen size in GUI units (which depend on resolution and GUI scale), or null */
	private static int[] guiScreen() {
		if (screenForTests != null) {
			return screenForTests;
		}
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
	 * Fits the HUD layout of a config made on another screen to this one. Elements are placed from an anchor (an edge, a corner or the center) at
	 * a distance in GUI units, and sized in GUI units, which follow the GUI scale like the game's own HUD: so they stay as they are, and the
	 * auto-fit ({@link dev.tim1337.lumio.hud.HudAutoFit}) keeps them on this screen and off each other. Sizes are never stretched: that made a
	 * layout from a small screen huge on a large one. Only what has no anchor, the music window's top left, moves with the screen's size.
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
		for (var setting : feature.settings()) {
			JsonElement value = wanted.get(setting.key());
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
				|| ConfigPart.of(feature, setting.key(), setting) != ConfigPart.LAYOUT) {
				continue;
			}
			if (!ConfigPart.UNANCHORED_KEYS.contains(setting.key())) {
				continue;
			}
			double ratio = switch (ConfigPart.axisOf(setting.key())) {
				case 'x' -> rx;
				case 'y' -> ry;
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
					removePrivate(entry.getKey(), entry.getValue().getAsJsonObject());
				}
			}
			return COMPACT.toJson(root);
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
					if (json.getAsJsonObject().has(setting.key()) && !isPrivate(feature.id(), setting.key())) {
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
						if (value != null && !isPrivate(feature.id(), setting.key()) && parts.contains(ConfigPart.of(feature, setting.key(), setting))) {
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
	private static final ExecutorService WRITER = Threads.daemonExecutor("Lumio settings");
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
		Unloaded kept = UNLOADED.get(feature);
		if (kept == null) {
			return now;
		}
		JsonObject result = kept.fromFile().deepCopy();
		for (var setting : now.entrySet()) {
			if (!setting.getValue().equals(kept.defaults().get(setting.getKey()))) {
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
