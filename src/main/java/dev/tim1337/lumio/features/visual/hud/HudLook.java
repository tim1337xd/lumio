package dev.tim1337.lumio.features.visual.hud;

import net.minecraft.network.chat.Component;

/** The look shared by the hotbar and the status bars: the standard glass bars, or slim lines with the numbers above them. */
public enum HudLook {
	STANDARD("Standard"),
	SLIM("Slim");

	private final Component displayName;

	HudLook(String displayName) {
		this.displayName = Component.literal(displayName);
	}

	public Component displayName() {
		return displayName;
	}
}
