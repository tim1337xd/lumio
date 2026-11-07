package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.features.visual.screen.MenusFeature;
import dev.tim1337.lumio.menu.FeatureSets;
import dev.tim1337.lumio.menu.Presets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the player picks on "Main menu" (which one, the startup animation and the loading screens) and on "How you play" (where they play, what
 * they do there, and switches they changed by hand). All of it is only applied when the setup ends ({@link #apply}). On a first start every listed feature follows the picks; when the setup runs
 * again it starts from the current settings, and only what the player changes in it is changed.
 */
final class SetupChoices {
	private final boolean again;
	/** Whether only the part of an add-on installed later is asked: nothing of the main mod is listed or changed. */
	private final boolean addonOnly;
	private boolean inWorld;
	private final Set<SetupGroup> picks = new LinkedHashSet<>();
	/** Switches changed by hand, which win over the picks until their group goes away. */
	private final Map<Feature, Boolean> overrides = new IdentityHashMap<>();
	/** Whether the player changed anything on "How you play"; skipping before that keeps the features as they are. */
	private boolean touched;
	/** The preset picked on the Look step, applied when it was picked; null for none. */
	private Presets.Preset preset;
	// where the setup started, when it runs again: the picks and the switches as they were
	private final boolean startInWorld;
	private final Set<SetupGroup> startPicks = new LinkedHashSet<>();
	private final Map<Feature, Boolean> startStates = new HashMap<>();
	// the main menu step
	private boolean livingWorld;
	private boolean startupAnimation;
	private boolean smoothLoading;

	private SetupChoices(boolean again, boolean addonOnly) {
		this.again = again;
		this.addonOnly = addonOnly;
		for (SetupGroup group : SetupGroup.all()) {
			for (Feature feature : group.features()) {
				startStates.put(feature, feature.isAnyOn());
			}
		}
		if (again) {
			// what the player plays now, read from what is on
			for (SetupGroup group : SetupGroup.activities()) {
				if (group.features().stream().anyMatch(Feature::isAnyOn)) {
					startPicks.add(group);
				}
			}
			SetupGroup world = SetupGroup.world();
			boolean anyInWorld = !startPicks.isEmpty() || world != null && world.features().stream().anyMatch(Feature::isAnyOn);
			startInWorld = world != null && SetupGroup.worldOn() && anyInWorld;
			if (!startInWorld) {
				startPicks.clear();
			}
		} else {
			// with an add-on that offers a world, the setup starts with it; what the player does there is theirs to pick
			startInWorld = SetupGroup.world() != null;
		}
		inWorld = startInWorld;
		picks.addAll(startPicks);
		if (again) {
			MenusFeature menus = ModFeatures.MENUS;
			livingWorld = menus.isEnabled() && menus.mainMenu() == MenusFeature.MainMenu.LIVING_WORLD;
			startupAnimation = menus.isEnabled() && menus.startupOn();
			smoothLoading = menus.isEnabled() && menus.loadingOn();
		} else {
			recommendMenu();
		}
	}

	/** @return the choices of a first start */
	static SetupChoices fresh() {
		return new SetupChoices(false, false);
	}

	/** @return the choices of the current settings, for running the setup again */
	static SetupChoices current() {
		return new SetupChoices(true, false);
	}

	/** @return the choices of an add-on's part alone, for an add-on installed after the setup was finished */
	static SetupChoices addonPart() {
		return new SetupChoices(false, true);
	}

	boolean addonOnly() {
		return addonOnly;
	}

	boolean again() {
		return again;
	}

	boolean inWorld() {
		return inWorld;
	}

	void setInWorld(boolean value) {
		touched = true;
		inWorld = value;
		dropHiddenOverrides();
	}

	boolean picked(SetupGroup group) {
		return picks.contains(group);
	}

	/** @return the picked activities, in the order of the grid */
	List<SetupGroup> picks() {
		List<SetupGroup> shown = new ArrayList<>();
		for (SetupGroup group : SetupGroup.activities()) {
			if (picks.contains(group)) {
				shown.add(group);
			}
		}
		return shown;
	}

	void togglePick(SetupGroup group) {
		touched = true;
		if (!picks.remove(group)) {
			picks.add(group);
		}
		dropHiddenOverrides();
	}

	/** @return the groups listed under "Turned on for you": always the first, then the world's and its picked activities; the add-on's part alone leaves out the first */
	List<SetupGroup> shownGroups() {
		List<SetupGroup> shown = new ArrayList<>();
		if (!addonOnly) {
			shown.add(SetupGroup.ALWAYS);
		}
		if (inWorld && SetupGroup.world() != null) {
			shown.add(SetupGroup.world());
			shown.addAll(picks());
		}
		return shown;
	}

	private boolean shown(SetupGroup group) {
		return group == SetupGroup.ALWAYS || inWorld && (group == SetupGroup.world() || picks.contains(group));
	}

	private boolean shownAtStart(SetupGroup group) {
		return group == SetupGroup.ALWAYS || startInWorld && (group == SetupGroup.world() || startPicks.contains(group));
	}

	/** @return whether a feature of a group will be on: a switch changed by hand, or what the picks give */
	boolean wanted(Feature feature, SetupGroup group) {
		Boolean override = overrides.get(feature);
		if (override != null) {
			return override;
		}
		if (!again) {
			return shown(group);
		}
		// running again: a group that stays keeps its switches, a new one turns its features on, a dropped one turns them off
		boolean before = startStates.getOrDefault(feature, false);
		if (shown(group)) {
			return !shownAtStart(group) || before;
		}
		return shownAtStart(group) ? false : before;
	}

	void toggle(Feature feature, SetupGroup group) {
		touched = true;
		overrides.put(feature, !wanted(feature, group));
	}

	/** @return how many of the listed features will be on */
	int countOn() {
		int count = 0;
		for (SetupGroup group : shownGroups()) {
			for (Feature feature : group.features()) {
				if (wanted(feature, group)) {
					count++;
				}
			}
		}
		return count;
	}

	/** @return how many features are listed */
	int countListed() {
		int count = 0;
		for (SetupGroup group : shownGroups()) {
			count += group.features().size();
		}
		return count;
	}

	/** A switch changed by hand stays only while its group is listed. */
	private void dropHiddenOverrides() {
		for (SetupGroup group : SetupGroup.all()) {
			if (!shown(group)) {
				group.features().forEach(overrides::remove);
			}
		}
	}

	// --------------------------------------------------------------------------------------------------------- main menu

	boolean livingWorld() {
		return livingWorld;
	}

	void setLivingWorld(boolean value) {
		livingWorld = value;
	}

	/** @return whether the startup animation will play; it leads into the living world, so with the classic main menu it is off */
	boolean startupAnimation() {
		return livingWorld && startupAnimation;
	}

	void toggleStartupAnimation() {
		if (livingWorld) {
			startupAnimation = !startupAnimation;
		}
	}

	boolean smoothLoading() {
		return smoothLoading;
	}

	void toggleSmoothLoading() {
		smoothLoading = !smoothLoading;
	}

	/** Picks what the recommended setup uses: the living world, with the startup animation and the smooth loading screens. */
	void recommendMenu() {
		livingWorld = true;
		startupAnimation = true;
		smoothLoading = true;
	}

	/**
	 * Sets the main menu as picked; the menus feature is turned on where a pick needs it. The startup switch is kept as set even with the classic
	 * main menu, where it does not play, so it is back when the living world is picked again.
	 */
	private void applyMenu() {
		MenusFeature menus = ModFeatures.MENUS;
		menus.setMenus(livingWorld ? MenusFeature.MainMenu.LIVING_WORLD : MenusFeature.MainMenu.CLASSIC, startupAnimation, smoothLoading);
		if ((livingWorld || smoothLoading) && !menus.isEnabled()) {
			menus.setEnabled(true);
		}
	}

	/** @return the preset picked to start from, or null */
	Presets.Preset preset() {
		return preset;
	}

	/** Starts from a preset: its whole setup at once, as the look's rows apply at once. */
	void pickPreset(Presets.Preset picked) {
		if (picked == preset) {
			return;
		}
		preset = picked;
		Presets.apply(picked);
	}

	/**
	 * Turns the features on and off as picked. On a first start every group follows the picks, so "Anywhere else" turns no feature of the world on;
	 * when the setup runs again, only features whose state the setup changed are touched. With {@code recommended} and nothing changed on "How
	 * you play", the Recommended feature set (essentials and the add-ons' basics) is turned on and the rest stays as it is. The main menu is set as picked either way.
	 */
	void apply(boolean recommended) {
		if (preset != null && !touched) {
			// a preset decides which features are on; only the main menu is set as picked
			applyMenu();
			return;
		}
		if (recommended && !touched) {
			// the Recommended feature set of the configs page, only turning on: what the player has on besides stays
			for (Feature feature : FeatureSets.recommended()) {
				feature.setEnabled(true);
			}
			SetupGroup.setWorldOn(true);
			applyMenu();
			return;
		}
		for (SetupGroup group : SetupGroup.all()) {
			for (Feature feature : group.features()) {
				boolean want = wanted(feature, group);
				if (want != feature.isAnyOn()) {
					feature.setEnabled(want);
				}
			}
		}
		if (inWorld && !SetupGroup.worldOn()) {
			SetupGroup.setWorldOn(true);
		}
		// after the features, so a main menu that needs the menus feature turns it on again
		applyMenu();
	}

	/** Turns the add-on's features on and off as picked, and nothing of the main mod. */
	void applyAddonPart() {
		for (SetupGroup group : SetupGroup.all()) {
			if (group != SetupGroup.ALWAYS) {
				for (Feature feature : group.features()) {
					boolean want = wanted(feature, group);
					if (want != feature.isAnyOn()) {
						feature.setEnabled(want);
					}
				}
			}
		}
		if (inWorld && !SetupGroup.worldOn()) {
			SetupGroup.setWorldOn(true);
		}
	}

	/**
	 * Turns every feature off, so the game looks and plays as without the mod: the game's own main menu, without the startup animation and the
	 * mod's loading screen. The look of the mod's own menus stays.
	 */
	static void turnEverythingOff() {
		ModFeatures.MENUS.setMenus(MenusFeature.MainMenu.CLASSIC, false, false);
		for (Feature feature : FeatureManager.features()) {
			if (feature.isSwitchable() && !feature.isChild() && feature.isAnyOn()) {
				feature.setEnabled(false);
			}
		}
	}
}
