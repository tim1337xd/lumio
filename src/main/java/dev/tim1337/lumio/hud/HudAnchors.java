package dev.tim1337.lumio.hud;

/**
 * Where a HUD element lies from its anchor, in GUI units. The math is shared with add-ons in the experimental API
 * ({@link dev.tim1337.lumio.api.experimental.hud.HudAnchors}); this keeps the HUD's own name for it.
 */
public final class HudAnchors {
	public static final int START = dev.tim1337.lumio.api.experimental.hud.HudAnchors.START;
	public static final int MIDDLE = dev.tim1337.lumio.api.experimental.hud.HudAnchors.MIDDLE;
	public static final int END = dev.tim1337.lumio.api.experimental.hud.HudAnchors.END;

	private HudAnchors() {
	}

	/** @return the left (or top) of an element of a size placed at an offset from an anchor on a screen of a size */
	public static float start(int anchor, float offset, float size, float screen) {
		return dev.tim1337.lumio.api.experimental.hud.HudAnchors.start(anchor, offset, size, screen);
	}

	/** @return the offset from an anchor that puts an element of a size with its left (or top) at a place: the inverse of {@link #start} */
	public static float offset(int anchor, float start, float size, float screen) {
		return dev.tim1337.lumio.api.experimental.hud.HudAnchors.offset(anchor, start, size, screen);
	}

	/** @return the anchor nearest to an element: the third of the screen its center falls in */
	public static int nearest(float start, float size, float screen) {
		return dev.tim1337.lumio.api.experimental.hud.HudAnchors.nearest(start, size, screen);
	}
}
