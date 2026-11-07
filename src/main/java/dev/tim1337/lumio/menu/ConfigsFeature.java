package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.ConfigPart;
import dev.tim1337.lumio.config.ConfigStore;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ConfigListSetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.features.misc.keybinds.CommandBind;
import dev.tim1337.lumio.features.misc.keybinds.CommandKeybindsFeature;
import dev.tim1337.lumio.render.ScreenFade;
import dev.tim1337.lumio.ui.Confirm;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The configs page: the player's own configs (saved, imported, and automatic backups) and the presets that ship with Lumio ({@link Presets}). A
 * preset is a whole setup and is tried live before it is kept; when it is kept, all settings from before are saved as an automatic backup, and the
 * notice offers to undo it.
 */
public class ConfigsFeature extends Feature {
	/** A card of the player's: a saved or imported config or an automatic backup, with its card colors. */
	public record Entry(String id, String name, int primary, int secondary, ConfigStore.Saved saved) {
	}

	/** How long the colors cross-fade when a look is tried, kept or undone, in seconds. */
	private static final float LOOK_FADE_SECONDS = 0.3f;

	/** Card color pairs for the player's configs, picked by name hash. */
	private static final int[][] OWN_COLORS = {{SystemColors.BLUE_RGB, SystemColors.TEAL_RGB}, {SystemColors.PURPLE_RGB, 0xFF6FA5}, {SystemColors.GREEN_RGB, SystemColors.YELLOW_RGB}, {SystemColors.ORANGE_RGB, SystemColors.RED_RGB}, {SystemColors.INDIGO_RGB, 0x00E5FF},
		{0xFF2D95, SystemColors.PURPLE_RGB}, {SystemColors.TEAL_RGB, SystemColors.GREEN_RGB}, {SystemColors.YELLOW_RGB, SystemColors.ORANGE_RGB}};

	/** Which parts a load applies: one checkbox per part. */
	private final Map<ConfigPart, BooleanSetting> parts = new EnumMap<>(ConfigPart.class);
	/** The most recently loaded config, which gets the check mark. Not shared. */
	private final StringSetting activeConfig = add(new StringSetting("active_config", Component.literal("Config in use"), "", false), "Configs");
	/** Card colors of the player's configs, the menu colors at creation time: one "name=rrggbb,rrggbb" per line. Not shared. */
	private final StringSetting configColors = add(new StringSetting("config_colors", Component.literal("Colors of the configs"), "", false), "Configs");
	/** Fingerprint of the settings right after a config was loaded: while they are unchanged, loading that config again restores them. */
	private final StringSetting loadedPrint = add(new StringSetting("loaded_print", Component.literal("Settings when loaded"), "", false), "Configs");

	// the preset being tried: all settings before the trial, the preset that was in use then (or null), and the preset shown now
	private String trialSettings;
	private Presets.Preset trialBefore;
	private Presets.Preset trying;
	// what the header buttons do, given by the page's control
	private Runnable importAction;
	private Runnable saveAction;
	private final List<HeaderButton> headerButtons = List.of(
		new HeaderButton("Import", false, () -> run(importAction)),
		new HeaderButton("Save current", true, () -> run(saveAction)));

	private Map<String, int[]> colors() {
		Map<String, int[]> map = new HashMap<>();
		for (String line : configColors.get().split("\n")) {
			int eq = line.lastIndexOf('=');
			if (eq <= 0) {
				continue;
			}
			String[] pair = line.substring(eq + 1).split(",");
			try {
				map.put(line.substring(0, eq), new int[]{Integer.parseInt(pair[0], 16), Integer.parseInt(pair[1], 16)});
			} catch (RuntimeException ignored) {
			}
		}
		return map;
	}

	private void storeColors(Map<String, int[]> map) {
		StringBuilder out = new StringBuilder();
		map.forEach((name, pair) -> out.append(name).append('=').append(String.format(Locale.ROOT, "%06x,%06x", pair[0] & 0xFFFFFF, pair[1] & 0xFFFFFF)).append('\n'));
		configColors.set(out.toString().strip());
		FeatureManager.saveSoon();
	}

	/** Gives the card of a newly created config the current menu colors: the accent and the second glow color. */
	public void rememberColors(String name) {
		Map<String, int[]> map = colors();
		int accent = MenuFeature.accentRgb() & 0xFFFFFF;
		int[] glow = ModFeatures.MENU.glowPalette();
		int second = glow.length > 1 ? glow[1] & 0xFFFFFF : glow.length == 1 ? glow[0] & 0xFFFFFF : accent;
		map.put(name, new int[]{accent, second == accent ? Ui.mix(0xFF000000 | accent, 0xFFFFFFFF, 0.35f) & 0xFFFFFF : second});
		storeColors(map);
	}

	/** Carries a config's card colors over to its new name. */
	public void renameColors(String from, String to) {
		Map<String, int[]> map = colors();
		int[] pair = map.remove(from);
		if (pair != null) {
			map.put(to, pair);
			storeColors(map);
		}
	}

	public ConfigsFeature() {
		super("configs", "Configs", "Start from a preset, or save, share and load your own configs.", false, false);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("layers");
		activeConfig.visibleWhen(() -> false);
		configColors.visibleWhen(() -> false);
		loadedPrint.visibleWhen(() -> false);

		add(new ConfigListSetting("config_list", Component.literal("Configs")), "");

		List<BooleanSetting> options = new ArrayList<>();
		for (ConfigPart part : ConfigPart.values()) {
			BooleanSetting option = new BooleanSetting("load_" + part.key, Component.literal(part.label), true, BooleanSetting.Style.CHECKBOX);
			parts.put(part, option);
			options.add(option);
		}
		var load = add(new MultiSelectSetting("load_parts", Component.literal("What is loaded"), options), "Configs");
		// shown in the popover of a config, instead of a row of its own
		load.visibleWhen(() -> false);
		loadOptions = options;
	}

	@Override
	public List<HeaderButton> headerButtons() {
		return headerButtons;
	}

	/** Gives the header buttons their actions: the page's control opens its import and save panels. */
	public void onHeaderButtons(Runnable onImport, Runnable onSave) {
		importAction = onImport;
		saveAction = onSave;
	}

	private static void run(Runnable action) {
		if (action != null) {
			action.run();
		}
	}

	/** The switches of what loading a config applies, in the order of the parts; the config popover shows them. */
	private List<BooleanSetting> loadOptions = List.of();

	public List<BooleanSetting> loadOptions() {
		return loadOptions;
	}

	private boolean wants(ConfigPart part) {
		return parts.get(part).get();
	}

	/** @return the parts selected to be applied on load */
	public Set<ConfigPart> selectedParts() {
		Set<ConfigPart> chosen = EnumSet.noneOf(ConfigPart.class);
		parts.forEach((part, option) -> {
			if (option.get()) {
				chosen.add(part);
			}
		});
		return chosen;
	}

	// ------------------------------------------------------------------------------------------------------- own configs

	/** @return the player's configs, newest first, then the automatic backups, newest first */
	public List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		Map<String, int[]> remembered = colors();
		List<ConfigStore.Saved> ordered = new ArrayList<>(ConfigStore.list());
		ordered.sort((first, second) -> Boolean.compare(first.automatic(), second.automatic()));
		for (ConfigStore.Saved saved : ordered) {
			int[] pair = remembered.getOrDefault(saved.name(), OWN_COLORS[Math.floorMod(saved.name().hashCode(), OWN_COLORS.length)]);
			list.add(new Entry("own:" + saved.name(), saved.name(), pair[0], pair[1], saved));
		}
		return list;
	}

	/** @return whether the card belongs to the most recently loaded config */
	public boolean isActive(Entry entry) {
		return entry.id().equals(activeConfig.get());
	}

	/** @return the config in use, or null; it builds the list of configs, so callers ask for it once in a while, not every frame */
	public Entry activeEntry() {
		for (Entry entry : entries()) {
			if (isActive(entry)) {
				return entry;
			}
		}
		return null;
	}

	/** Remembers the most recently loaded config for its check mark; renaming keeps it. */
	public void setActive(String id) {
		activeConfig.set(id == null ? "" : id);
		FeatureManager.saveSoon();
	}

	/** @return the current settings as plain JSON, without private and menu-only keys */
	private static String currentSettings() {
		return FeatureManager.comparable(FeatureManager.exportText());
	}

	private static String print(String settings) {
		return Integer.toHexString(settings.hashCode()) + ":" + settings.length();
	}

	/** @return whether the current settings exist in no config: they match none of the player's configs and changed since the last load */
	private boolean unsaved() {
		String now = currentSettings();
		if (print(now).equals(loadedPrint.get())) {
			return false;
		}
		for (ConfigStore.Saved saved : ConfigStore.list()) {
			String text = ConfigStore.read(saved);
			if (text != null && FeatureManager.comparable(text).equals(now)) {
				return false;
			}
		}
		return true;
	}

	/** Loads a config; if the current settings are not saved anywhere, asks first because they would be lost. */
	public void load(Entry entry) {
		if (selectedParts().isEmpty()) {
			Toasts.info("Nothing loaded", "Nothing is chosen in \"What is loaded\".");
			return;
		}
		// keybinds of a config someone else shared: every key and command is listed, and they come only when switched on
		List<CommandBind> keybinds = wants(ConfigPart.FEATURES) ? ConfigStore.keybindsToAdd(entry.saved()) : List.of();
		if (!keybinds.isEmpty()) {
			List<String> lines = new ArrayList<>(keybinds.size());
			for (CommandBind bind : keybinds) {
				lines.add(String.join(" + ", CommandKeybindsFeature.keyParts(bind.key, bind.modifiers)) + "   " + bind.command);
			}
			Confirm.askWithOption("Load \"" + entry.name() + "\"",
				"This config was shared by someone else. It has keybinds that send commands; they are only added to yours if you switch this on.",
				"Also import keybinds", lines, "Load", "Cancel", withKeybinds -> loadChecked(entry, withKeybinds));
			return;
		}
		loadChecked(entry, false);
	}

	/** Loads a config; if the current settings are not saved anywhere, asks first because they would be lost. */
	private void loadChecked(Entry entry, boolean withKeybinds) {
		if (unsaved()) {
			Confirm.ask("Your settings are not saved",
				"Loading \"" + entry.name() + "\" replaces your current settings, and they are not saved in any config. Cancel and press Save to keep them, or load anyway.",
				"Load anyway", "Cancel", () -> loadNow(entry, withKeybinds));
			return;
		}
		loadNow(entry, withKeybinds);
	}

	/** After loading: remembers the settings so loading the config again counts as restoring them. */
	private void rememberLoaded() {
		loadedPrint.set(print(currentSettings()));
		FeatureManager.saveSoon();
	}

	private void loadNow(Entry entry, boolean withKeybinds) {
		if (wants(ConfigPart.COLORS) && entry.saved().parts().contains(ConfigPart.COLORS)) {
			crossFade();
		}
		if (ConfigStore.load(entry.saved(), withKeybinds)) {
			setActive(entry.id());
			rememberLoaded();
			UiSounds.play(UiSounds.Cue.DONE);
			if (!wants(ConfigPart.FEATURES)) {
				Toasts.info("Config loaded in part", "Only colors and places: tick \"Features and their settings\" for the rest.");
			} else {
				Toasts.info("Config loaded", "\"" + entry.name() + "\" is on now.");
			}
		} else {
			UiSounds.play(UiSounds.Cue.FAIL);
			Toasts.error("config.load", "Could not load the config", "\"" + entry.name() + "\" could not be read.");
		}
	}

	/** Restores an automatic backup: the part it was made of comes back as it was. */
	public void restore(Entry entry) {
		if (entry.saved().parts().contains(ConfigPart.COLORS)) {
			crossFade();
		}
		if (ConfigStore.restore(entry.saved())) {
			UiSounds.play(UiSounds.Cue.DONE);
			Toasts.info("Restored", "\"" + entry.name() + "\" is back.");
		} else {
			UiSounds.play(UiSounds.Cue.FAIL);
			Toasts.error("config.restore", "Could not restore", "\"" + entry.name() + "\" could not be read.");
		}
	}

	/** Deletes a config of the player's. */
	public void delete(Entry entry) {
		ConfigStore.delete(entry.saved());
		Toasts.info("Config deleted", "\"" + entry.name() + "\" is gone.");
		if (isActive(entry)) {
			setActive(null);
		}
	}

	// ------------------------------------------------------------------------------------------------------------- looks

	/** @return the look being tried, or null */
	public Presets.Preset trying() {
		return trying;
	}

	/** @return the look that was in use before the one being tried, or null when none is tried or the colors were the player's own */
	public Presets.Preset trialBefore() {
		return trying == null ? null : trialBefore;
	}

	/** Shows a look for trying: only the colors change, and leaving the page without keeping it brings the colors from before back. */
	public void tryLook(Presets.Preset look) {
		if (look == trying) {
			return;
		}
		if (trying != null && look == trialBefore) {
			// the look from before is the way back
			goBack();
			return;
		}
		if (trying == null) {
			trialSettings = FeatureManager.exportText();
			trialBefore = null;
		}
		crossFade();
		if (trying != null) {
			// from the colors before the trial, so nothing of the look tried first stays
			FeatureManager.importText(trialSettings, EnumSet.allOf(ConfigPart.class));
		}
		Presets.apply(look);
		trying = look;
		UiSounds.play(UiSounds.Cue.TICK);
	}

	/** Ends the trial and brings the colors from before it back. */
	public void goBack() {
		if (trying == null) {
			return;
		}
		crossFade();
		FeatureManager.importText(trialSettings, EnumSet.allOf(ConfigPart.class));
		endTrial();
		UiSounds.play(UiSounds.Cue.CLOSE);
	}

	/** Keeps the look being tried; the colors from before are saved as an automatic backup, and the notice can undo it. */
	public void keep() {
		Presets.Preset kept = trying;
		if (kept == null) {
			return;
		}
		ConfigStore.Saved backup = ConfigStore.saveAutomatic("Before " + kept.name(), trialSettings);
		endTrial();
		UiSounds.play(UiSounds.Cue.DONE);
		Toasts.info(kept.name() + " kept", backupNote(backup, "settings"), "Undo", () -> undo(backup));
	}

	private void endTrial() {
		trying = null;
		trialBefore = null;
		trialSettings = null;
	}

	/** Leaving the page or closing the menu ends a trial that was not kept. */
	@Override
	public void pageLeft() {
		goBack();
	}

	private static String backupNote(ConfigStore.Saved backup, String part) {
		return backup == null ? "Your old " + part + " could not be saved." : "Your old " + part + " are in \"" + backup.name() + "\".";
	}

	/** Undoes keeping a preset: the backup made just before comes back. */
	private void undo(ConfigStore.Saved backup) {
		if (backup == null) {
			return;
		}
		if (backup.parts().contains(ConfigPart.COLORS)) {
			crossFade();
		}
		if (ConfigStore.restore(backup)) {
			UiSounds.play(UiSounds.Cue.CLOSE);
			Toasts.info("Undone", "It is as it was before.");
		}
	}

	/** Cross-fades the whole screen into the new colors instead of letting them jump; instant with the menu's animations off. */
	private static void crossFade() {
		if (ModFeatures.MENU.animations()) {
			ScreenFade.fadeFromNow(LOOK_FADE_SECONDS);
		}
	}
}
