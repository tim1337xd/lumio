package dev.tim1337.lumio.api.spi;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.feature.AddonPanel;

/** Makes the main mod's side of the add-ons' features, panels and pages. */
public interface FeatureBackend {
	FeatureHandle feature(AddonFeature owner, String id, String name, String description, boolean enabledByDefault, boolean hasSwitch);

	PanelHandle panel(AddonPanel owner, String id, String name, String description, boolean enabledByDefault, AddonPanel.Anchor anchor,
		boolean fromBottom, float defaultX, float defaultY);

	AddonPage page(String id, String name, String description, int rgb, String icon);
}
