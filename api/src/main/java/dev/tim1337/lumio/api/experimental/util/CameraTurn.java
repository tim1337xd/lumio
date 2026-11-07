package dev.tim1337.lumio.api.experimental.util;

/**
 * How much of the mouse's movement turns the camera, from 0 (it does not turn) to 1 (as usual). Only that changes: the cursor in menus, scrolling, clicks and keys stay as
 * they are, and nothing is sent. Whoever sets a lower value must set it back to 1, whatever happens, so the player can never get stuck.
 */
public final class CameraTurn {
	private static final float USUAL = 1f;
	private static volatile float scale = USUAL;

	private CameraTurn() {
	}

	/** @param share how much of the mouse's movement turns the camera, from 0 to 1 */
	public static void set(float share) {
		scale = Math.max(0f, Math.min(USUAL, share));
	}

	/** Back to the usual turning. */
	public static void reset() {
		scale = USUAL;
	}

	/** @return how much of the mouse's movement turns the camera */
	public static float scale() {
		return scale;
	}
}
