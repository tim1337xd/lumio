package dev.tim1337.lumio.ui;

import net.minecraft.network.chat.Component;

/** What a dragged HUD element snaps to in the editor: the screen's center lines, its edges and the grid, the grid only, or nothing. */
public enum HudSnapMode {
	CENTER_GRID("Center + grid"),
	GRID_ONLY("Grid only"),
	OFF("Off");

	private final Component displayName;

	HudSnapMode(String displayName) {
		this.displayName = Component.literal(displayName);
	}

	public Component displayName() {
		return displayName;
	}
}
