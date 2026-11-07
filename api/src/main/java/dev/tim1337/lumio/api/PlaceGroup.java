package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.feature.FeatureView;

import java.util.List;

/**
 * A group of features that fit where the player is, for the Home page's "You are in" card.
 *
 * @param name     the group's name ("Mining")
 * @param page     the page the group links to
 * @param features its features, in order
 */
public record PlaceGroup(String name, AddonPage page, List<FeatureView> features) {
	public PlaceGroup {
		features = List.copyOf(features);
	}
}
