package dev.tim1337.lumio.ui;

import net.minecraft.network.chat.Component;

/** The depth effect of a single HUD element: the menu's switch, or on or off for this element only. */
public enum PanelDepth {
	MENU("Like menu", null),
	ON("On", Boolean.TRUE),
	OFF("Off", Boolean.FALSE);

	private final Component displayName;
	private final Boolean depth;

	PanelDepth(String displayName, Boolean depth) {
		this.displayName = Component.literal(displayName);
		this.depth = depth;
	}

	public Component displayName() {
		return displayName;
	}

	/** @return whether the element has the depth effect, or null to follow the menu settings */
	public Boolean depth() {
		return depth;
	}
}
