package dev.tim1337.lumio.features.visual.hud;

import net.minecraft.network.chat.Component;

/** Font of a HUD element's text: the menu's, the mod's, or the game's. */
public enum BarFont {
	MENU("Like the menu", null),
	MOD("Inter", Boolean.FALSE),
	GAME("Minecraft", Boolean.TRUE);

	private final Component displayName;
	private final Boolean override;

	BarFont(String displayName, Boolean override) {
		this.displayName = Component.literal(displayName);
		this.override = override;
	}

	public Component displayName() {
		return displayName;
	}

	/** @return true for the game font, false for the mod font, null to follow the menu setting */
	public Boolean override() {
		return override;
	}
}
