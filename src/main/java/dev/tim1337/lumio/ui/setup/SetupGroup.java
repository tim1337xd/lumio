package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.SetupChoice;
import dev.tim1337.lumio.api.SetupProvider;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.menu.FeatureSets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The groups of features the setup turns on: the visual features for everyone, and from the add-on that offers it ({@link SetupProvider}) its
 * world's general features and one group per thing the player does there. Every feature is in one group only.
 */
final class SetupGroup {
	static final SetupGroup ALWAYS = new SetupGroup("always", "Always", "Always", "", "sparkle", 0xBF5AF2, 0x5E5CE6, () -> FeatureSets.ESSENTIALS);

	private static SetupGroup world;
	private static List<SetupGroup> activities;
	private static List<SetupGroup> all;

	private final String id;
	private final String displayName;
	private final String shortName;
	private final String note;
	private final String icon;
	private final int light;
	private final int dark;
	private final Supplier<List<Feature>> source;
	private List<Feature> features;

	private SetupGroup(String id, String displayName, String shortName, String note, String icon, int light, int dark, Supplier<List<Feature>> source) {
		this.id = id;
		this.displayName = displayName;
		this.shortName = shortName;
		this.note = note;
		this.icon = icon;
		this.light = light;
		this.dark = dark;
		this.source = source;
	}

	private static SetupGroup of(SetupChoice choice) {
		return new SetupGroup(choice.id(), choice.name(), choice.shortName(), choice.note(), choice.icon(), choice.light(), choice.dark(),
			() -> features(choice.features().get()));
	}

	private static List<Feature> features(List<FeatureView> features) {
		List<Feature> own = new ArrayList<>(features.size());
		for (FeatureView feature : features) {
			Feature known = Adapters.featureOf(feature);
			if (known != null) {
				own.add(known);
			}
		}
		return own;
	}

	/** @return the add-on that offers a world to the setup, or null */
	static SetupProvider provider() {
		return Addons.setup();
	}

	/** @return the group of the add-on's world, or null without one */
	static SetupGroup world() {
		if (world == null && provider() != null) {
			world = of(provider().world());
		}
		return world;
	}

	/** @return what the player can pick under "What you do there", in the order of the grid; empty without an add-on */
	static List<SetupGroup> activities() {
		if (activities == null) {
			List<SetupGroup> found = new ArrayList<>();
			if (provider() != null) {
				provider().activities().forEach(choice -> found.add(of(choice)));
			}
			activities = List.copyOf(found);
		}
		return activities;
	}

	/** @return every group: always, the world, its activities */
	static List<SetupGroup> all() {
		if (all == null) {
			List<SetupGroup> found = new ArrayList<>();
			found.add(ALWAYS);
			if (world() != null) {
				found.add(world());
			}
			found.addAll(activities());
			all = List.copyOf(found);
		}
		return all;
	}

	/** @return whether the add-on's features are on as a whole; true without one */
	static boolean worldOn() {
		return provider() == null || provider().worldOn();
	}

	static void setWorldOn(boolean on) {
		if (provider() != null) {
			provider().setWorldOn(on);
		}
	}

	String id() {
		return id;
	}

	String displayName() {
		return displayName;
	}

	/** @return the name in the summary of the last step: "Coins" for "Making coins" */
	String shortName() {
		return shortName;
	}

	/** @return the line under the name, or "" */
	String note() {
		return note;
	}

	String icon() {
		return icon;
	}

	/** @return the colors of its icon tile, from the top to the bottom, as 0xRRGGBB */
	int light() {
		return light;
	}

	int dark() {
		return dark;
	}

	/** @return the features of the group, in the order they are listed; built once, after all features exist */
	List<Feature> features() {
		if (features == null) {
			features = source.get();
		}
		return features;
	}
}
