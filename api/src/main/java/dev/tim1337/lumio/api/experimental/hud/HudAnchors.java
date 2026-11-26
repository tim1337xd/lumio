package dev.tim1337.lumio.api.experimental.hud;

/**
 * Where a HUD element lies from its anchor, in GUI units: the start (left or top) edge, the middle or the end (right or bottom) edge of the screen,
 * and the distance from there. Distances in GUI units follow the GUI scale like the game's own HUD, so an element keeps its place to its edge on
 * any screen. The HUD's own panels place themselves with it, and so can an add-on's panels that are kept relative to the screen. Plain numbers.
 */
public final class HudAnchors {
	public static final int START = 0;
	public static final int MIDDLE = 1;
	public static final int END = 2;

	private HudAnchors() {
	}

	/** @return the left (or top) of an element of a size placed at an offset from an anchor on a screen of a size */
	public static float start(int anchor, float offset, float size, float screen) {
		return switch (anchor) {
			case START -> offset;
			case END -> screen - offset - size;
			default -> screen / 2f + offset - size / 2f;
		};
	}

	/** @return the offset from an anchor that puts an element of a size with its left (or top) at a place: the inverse of {@link #start} */
	public static float offset(int anchor, float start, float size, float screen) {
		return switch (anchor) {
			case START -> start;
			case END -> screen - start - size;
			default -> start + size / 2f - screen / 2f;
		};
	}

	/** @return the anchor nearest to an element: the third of the screen its center falls in */
	public static int nearest(float start, float size, float screen) {
		float center = start + size / 2f;
		return center < screen / 3f ? START : center > screen * 2f / 3f ? END : MIDDLE;
	}
}
