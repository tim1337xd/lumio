package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.feature.AddonFeature;

import java.util.List;
import java.util.Set;

/**
 * An add-on's part of the feature sets on the Configs page. Recommended turns on its basics (every feature it registered that is not advanced);
 * its own sets add more.
 */
public interface FeatureSetProvider {
	/** @return the name of its basics in the note of Recommended ("SkyBlock basics") */
	String basicsName();

	/** @return its features for experienced players or one kind of activity, which Recommended leaves off */
	Set<AddonFeature> advanced();

	/** @return its own sets, in order */
	List<FeatureSetSpec> sets();

	/** Switches its features on as a whole (its master switch), when a set that decides them is applied. */
	void switchOn();
}
