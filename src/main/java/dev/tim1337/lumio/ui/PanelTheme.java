package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;

import net.minecraft.network.chat.Component;

/** The theme of a single HUD element: the shared HUD theme, or its own. */
public enum PanelTheme {
	HUD("Like the HUD", null),
	GLASS("Glass", MacTheme.GLASS),
	DARK("Dark", MacTheme.DARK),
	LIGHT("Light", MacTheme.LIGHT),
	COLORED("Colored", MacTheme.COLORED);

	private final Component displayName;
	private final MacTheme theme;

	PanelTheme(String displayName, MacTheme theme) {
		this.displayName = Component.literal(displayName);
		this.theme = theme;
	}

	public Component displayName() {
		return displayName;
	}

	/** @return the theme, or null for the HUD theme */
	public MacTheme theme() {
		return theme;
	}

	/** @return the theme to draw with: its own, or the HUD's also while something is drawn outside of the HUD, such as a preview in a menu */
	public MacTheme themeOrHud() {
		return theme != null || ModFeatures.MENU == null ? theme : ModFeatures.MENU.hudTheme();
	}

	/** @return a short name for the small HUD editor buttons, using the same words as the long name so they are recognizable */
	public String shortName() {
		return this == HUD ? "HUD" : displayName.getString();
	}
}
