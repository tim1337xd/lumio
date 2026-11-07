package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.feature.AddonFeature;

import java.util.Set;

/**
 * A feature set of an add-on for the Configs page: it turns on what Recommended turns on, plus its extra features.
 *
 * @param id    its id
 * @param name  its name ("SkyBlock Advanced")
 * @param note  one short line about it
 * @param icon  the icon of its tile
 * @param light the top color of its tile, 0xRRGGBB
 * @param dark  the bottom color of its tile, 0xRRGGBB
 * @param extra the features it turns on beyond Recommended
 */
public record FeatureSetSpec(String id, String name, String note, String icon, int light, int dark, Set<AddonFeature> extra) {
	public FeatureSetSpec {
		extra = Set.copyOf(extra);
	}
}
