package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.FeatureSetProvider;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.features.misc.BorderlessFeature;
import dev.tim1337.lumio.features.misc.DebugFeature;
import dev.tim1337.lumio.features.misc.KeepServerPacksFeature;
import dev.tim1337.lumio.features.visual.screen.MenusFeature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which features the first-start setup turns on: the essentials everyone gets, and with them the basics of each add-on (its features that are not
 * advanced). Preferences that are not about a look or a helper (the main menu, the window, the resource packs, the developer log) are never switched.
 * The presets to start from are {@link Presets}.
 */
public final class FeatureSets {
	/** The features everyone gets, as the setup turns them on. */
	public static final List<Feature> ESSENTIALS = List.of(ModFeatures.SKY, ModFeatures.HOTBAR, ModFeatures.STATUS, ModFeatures.INFO_BAR,
		ModFeatures.ACTION_BAR, ModFeatures.BOSS_BARS, ModFeatures.CHAT, ModFeatures.MENUS, ModFeatures.HELD_ITEM_OUTLINE, ModFeatures.ITEM_TOOLTIPS);

	private FeatureSets() {
	}

	/** @return what the setup turns on when nothing was changed on "How you play": the essentials and the add-ons' basics */
	public static Set<Feature> recommended() {
		Set<Feature> all = new HashSet<>(basics());
		all.addAll(ESSENTIALS);
		return all;
	}

	/** @return the advanced features of every add-on */
	private static Set<Feature> advanced() {
		Set<Feature> advanced = new HashSet<>();
		for (FeatureSetProvider provider : Addons.featureSets()) {
			for (AddonFeature feature : provider.advanced()) {
				advanced.add(Adapters.featureOf(feature));
			}
		}
		return advanced;
	}

	/** @return every feature of an add-on that is not advanced */
	public static Set<Feature> basics() {
		Set<Feature> advanced = advanced();
		Set<Feature> basics = new HashSet<>();
		for (Feature feature : switchable()) {
			if (FeatureManager.addonOf(feature) != null && !advanced.contains(feature)) {
				basics.add(feature);
			}
		}
		return basics;
	}

	/** @return the features the setup can switch: each one with a switch of its own, without the preferences that are not a look or a helper */
	public static List<Feature> switchable() {
		List<Feature> found = new ArrayList<>();
		for (Feature feature : FeatureManager.features()) {
			if (feature.isSwitchable() && feature.children().isEmpty() && !preference(feature)) {
				found.add(feature);
			}
		}
		return found;
	}

	private static boolean preference(Feature feature) {
		return feature instanceof HomeFeature || feature instanceof MenuFeature || feature instanceof MenusFeature || feature instanceof ConfigsFeature
			|| feature instanceof BorderlessFeature || feature instanceof KeepServerPacksFeature || feature instanceof DebugFeature
			|| feature instanceof FeatureListFeature;
	}
}
