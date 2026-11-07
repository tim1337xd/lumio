package dev.tim1337.lumio.api.experimental.hud;

import dev.tim1337.lumio.api.experimental.draw.PanelLook;

/** A feature that is not a HUD panel but has a look of its own, such as a label in the world: it has the panels' settings card, and takes part in "Copy style to other panels". */
public interface StyledFeature {
	/** @return its theme, which it hands to the other panels when its style is copied */
	PanelLook look();

	/** Takes the theme of the panel whose style is copied. */
	void takeLook(PanelLook look);

	/** Its style was copied to the other panels: it can give features of its own kind what panels do not have. */
	default void styleCopied() {
	}
}
