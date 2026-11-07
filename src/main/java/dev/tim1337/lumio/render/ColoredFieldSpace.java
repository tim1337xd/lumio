package dev.tim1337.lumio.render;

/**
 * Where the Colored theme's field lies, in screen pixels. For a HUD panel it belongs to that panel: larger than it, centered on it and travelling around it, so it moves along when the panel moves and colors pass through, and no panel looks like a hole in one
 * picture behind them all. For a window it belongs to the window, which holds all of it and nothing is stretched. Plain numbers, so it is unit tested.
 */
final class ColoredFieldSpace {
	/** How much larger than a panel its field is, so that it shows a part of the colors and they can pass. */
	static final float PANEL_ZOOM = 1.6f;
	/** How far it travels from the panel's middle, as a share of the field's height, and how long a round takes, in the seconds of the field's motion. */
	static final float PANEL_TRAVEL = 0.2f;
	static final float PANEL_ROUND_SECONDS = 9f;
	/** Panels of different widths are at different places of their rounds, so that they do not all show the same at once; by steps, so that a width that changes a little changes nothing. */
	private static final float WIDTH_STEP = 32f;
	private static final float PHASE_PER_STEP = 1.37f;
	/** A second, smaller circle on top of the first, of a length that never fits into it, so the way never closes. */
	private static final float SECOND_CIRCLE = 0.35f;
	private static final float SECOND_ROUNDS_ACROSS = 2.3f;
	private static final float SECOND_ROUNDS_UP = 1.7f;
	private static final float TAU = (float) (Math.PI * 2.0);

	private ColoredFieldSpace() {
	}

	/** Writes the place and size of a HUD panel's field at a time of the field's motion: x, y, width, height. Nothing moves by itself: the time stands still when the motion does. */
	static void ofPanel(float[] out, float x, float y, float width, float height, float screenWidth, float screenHeight, float seconds) {
		float scale = Math.max(width / screenWidth, height / screenHeight) * PANEL_ZOOM;
		float fieldWidth = screenWidth * scale;
		float fieldHeight = screenHeight * scale;
		float phase = Math.round(width / WIDTH_STEP) * PHASE_PER_STEP;
		float angle = TAU * (seconds % (PANEL_ROUND_SECONDS * 1000f)) / PANEL_ROUND_SECONDS + phase;
		float reach = fieldHeight * PANEL_TRAVEL;
		out[0] = x + (width - fieldWidth) / 2f + reach * ((float) Math.cos(angle) + SECOND_CIRCLE * (float) Math.cos(SECOND_ROUNDS_ACROSS * angle));
		out[1] = y + (height - fieldHeight) / 2f + reach * ((float) Math.sin(angle) + SECOND_CIRCLE * (float) Math.sin(SECOND_ROUNDS_UP * angle));
		out[2] = fieldWidth;
		out[3] = fieldHeight;
	}

	/** @return the place and size of the field that covers a window, centered on it: x, y, width, height */
	static float[] ofWindow(float x, float y, float width, float height, float screenWidth, float screenHeight) {
		float scale = Math.max(width / screenWidth, height / screenHeight);
		float fieldWidth = screenWidth * scale;
		float fieldHeight = screenHeight * scale;
		return new float[]{x + (width - fieldWidth) / 2f, y + (height - fieldHeight) / 2f, fieldWidth, fieldHeight};
	}
}
