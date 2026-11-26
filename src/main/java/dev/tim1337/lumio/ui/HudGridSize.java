package dev.tim1337.lumio.ui;

import net.minecraft.network.chat.Component;

/** The spacing of the HUD editor's grid, in GUI units: what elements snap to, and one step of Shift + an arrow key. */
public enum HudGridSize {
	SMALL("Small", 4),
	MEDIUM("Medium", 8),
	LARGE("Large", 16);

	private final Component displayName;
	private final int step;

	HudGridSize(String displayName, int step) {
		this.displayName = Component.literal(displayName);
		this.step = step;
	}

	public Component displayName() {
		return displayName;
	}

	/** @return the distance between two grid lines, in GUI units */
	public int step() {
		return step;
	}
}
