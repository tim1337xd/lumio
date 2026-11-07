package dev.tim1337.lumio.api.experimental.draw;

import net.minecraft.network.chat.Component;

/** The theme of a single element: the one of the HUD, or its own. */
public enum PanelLook {
	HUD("Like the HUD"),
	GLASS("Glass"),
	DARK("Dark"),
	LIGHT("Light"),
	COLORED("Colored");

	private final Component displayName;

	PanelLook(String displayName) {
		this.displayName = Component.literal(displayName);
	}

	public Component displayName() {
		return displayName;
	}
}
