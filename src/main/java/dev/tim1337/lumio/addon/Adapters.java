package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.api.spi.FeatureBackend;
import dev.tim1337.lumio.api.spi.FeatureHandle;
import dev.tim1337.lumio.api.spi.PanelHandle;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.menu.FeatureListFeature;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes the main mod's side of the add-ons' features, panels and pages ({@link FeatureBackend}), and maps between the two: the API's feature
 * and the {@link Feature} the main mod keeps for it. Used while features are made and registered and when the player clicks something, never
 * while drawing a frame.
 */
public final class Adapters implements FeatureBackend {
	static final Adapters INSTANCE = new Adapters();

	/** The main mod's feature of every add-on feature made so far. */
	private static final Map<AddonFeature, Feature> FEATURES = new IdentityHashMap<>();
	/** The add-on features in the order they were made, so those of an add-on that failed to start can be forgotten. */
	private static final List<AddonFeature> MADE = new ArrayList<>();

	private Adapters() {
	}

	@Override
	public FeatureHandle feature(AddonFeature owner, String id, String name, String description, boolean enabledByDefault, boolean hasSwitch) {
		AddonFeatureAdapter adapter = new AddonFeatureAdapter(owner, id, name, description, enabledByDefault, hasSwitch);
		FEATURES.put(owner, adapter);
		MADE.add(owner);
		return adapter.handle;
	}

	@Override
	public PanelHandle panel(AddonPanel owner, String id, String name, String description, boolean enabledByDefault, AddonPanel.Anchor anchor,
		boolean fromBottom, float defaultX, float defaultY) {
		AddonPanelAdapter adapter = new AddonPanelAdapter(owner, id, name, description, enabledByDefault, anchor, fromBottom, defaultX, defaultY);
		FEATURES.put(owner, adapter);
		MADE.add(owner);
		return adapter.handle;
	}

	@Override
	public AddonPage page(String id, String name, String description, int rgb, String icon) {
		return new FeatureListFeature(id, name, description, rgb, icon);
	}

	/** @return how many add-on features were made so far, a mark for {@link #forgetFrom} */
	static int made() {
		return MADE.size();
	}

	/** Forgets the features made since the mark: those of an add-on that failed to start, which are never registered. */
	static void forgetFrom(int mark) {
		while (MADE.size() > mark) {
			FEATURES.remove(MADE.removeLast());
		}
	}

	/** @return the main mod's feature of a feature the API shows: itself for a main feature, its adapter for an add-on's; null if unknown */
	public static Feature featureOf(FeatureView view) {
		if (view instanceof Feature own) {
			return own;
		}
		return view instanceof AddonFeature addon ? FEATURES.get(addon) : null;
	}

	/** @return a feature as the API shows it to add-ons: the add-on's own object for an add-on's feature, else the main feature itself */
	public static FeatureView viewOf(Feature feature) {
		if (feature instanceof AddonFeatureAdapter adapter) {
			return adapter.addon();
		}
		return feature instanceof AddonPanelAdapter adapter ? adapter.addon() : feature;
	}
}
