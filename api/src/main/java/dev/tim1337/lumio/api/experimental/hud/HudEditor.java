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

	/**
	 * Draws something above the editor's own marks (the selection outline, the handles) this frame, in GUI pixels, such as a panel's tooltip: a
	 * panel is drawn under them, so what has to stay readable over it goes here. Only while the editor is open; call it while drawing the panel.
	 */
	public static void drawOnTop(java.util.function.Consumer<dev.tim1337.lumio.api.draw.Canvas> drawing) {
		Backend.get().hudDrawOnTop(drawing);
	}

	/** @return a snapper with the editor's grid and logic (the grid's lines and the screen's center lines), for panels an add-on lets the player move */
	public static GridSnap gridSnap() {
		return Backend.get().gridSnap();
	}

	/** Draws the editor's grid over a screen, faintly, as the editor shows it while an element is dragged: one dot per grid position. */
	public static void drawGrid(dev.tim1337.lumio.api.draw.Canvas canvas, int screenWidth, int screenHeight) {
		Backend.get().drawHudGrid(canvas, screenWidth, screenHeight);
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
