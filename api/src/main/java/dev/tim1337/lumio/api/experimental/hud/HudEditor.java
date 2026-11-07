package dev.tim1337.lumio.api.experimental.hud;

import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.spi.Backend;

/** The HUD editor, where the player moves and sizes the panels: panels show their sample content and hidden parts while it is open. */
public final class HudEditor {
	/** How visible a part that is switched off is drawn in the editor, so it can be clicked to show it again. */
	public static final float HIDDEN_ALPHA = 0.3f;

	private HudEditor() {
	}

	/** @return where the HUD elements shown are: left, top, width and height of each, in GUI pixels; for what has to stay clear of them */
	public static java.util.List<int[]> shownBoxes() {
		return Backend.get().hudBoxes();
	}

	public static boolean isOpen() {
		return Backend.get().hudEditorOpen();
	}

	/** Starts simulating one of a simulator's activities in the HUD editor, as its Simulate menu does; nothing happens for an unknown one. */
	public static void simulate(HudSimulator simulator, String simulationId, int option) {
		Backend.get().simulate(simulator, simulationId, option);
	}

	/** @return how visible a part of a panel is drawn: fully when on, faded in the HUD editor when off, not at all otherwise */
	public static float partAlpha(boolean on) {
		return on ? 1f : isOpen() ? HIDDEN_ALPHA : 0f;
	}
}
