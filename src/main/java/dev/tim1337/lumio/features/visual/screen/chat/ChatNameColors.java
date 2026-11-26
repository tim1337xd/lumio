package dev.tim1337.lumio.features.visual.screen.chat;

/**
 * The two colors the name in a "[Lumio]" line runs between: the accent and a lighter shade of it turned a little in hue, or the Colored look's
 * first two colors; both lifted where they are too dark to read on the dark chat. Static, never animated. Plain math, tested without the game.
 */
public final class ChatNameColors {
	/** The least brightness (relative luminance) a letter needs on the chat's dark background. */
	static final double LEAST_LUMINANCE = 0.42;
	/** How far the second color turns in hue, in degrees, and how much lighter it is. */
	private static final float HUE_TURN = 14f;
	private static final float LIGHTER = 0.28f;
	private static final float LIFT_STEP = 0.05f;

	private ChatNameColors() {
	}

	/** @return the two colors for one color: it and a lighter, slightly turned shade, both readable */
	public static int[] fromAccent(int rgb) {
		return of(rgb, mix(turnHue(rgb, HUE_TURN), 0xFFFFFF, LIGHTER));
	}

	/** @return two colors made readable */
	public static int[] of(int from, int to) {
		return new int[] {readable(from), readable(to)};
	}

	/** @return the color, mixed towards white until it is bright enough on the dark chat */
	static int readable(int rgb) {
		int color = rgb & 0xFFFFFF;
		float share = 0f;
		while (luminance(color) < LEAST_LUMINANCE && share < 1f) {
			share += LIFT_STEP;
			color = mix(rgb & 0xFFFFFF, 0xFFFFFF, share);
		}
		return color;
	}

	static double luminance(int rgb) {
		return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
	}

	private static double linear(int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static int mix(int a, int b, float share) {
		int r = Math.round((a >> 16 & 0xFF) * (1 - share) + (b >> 16 & 0xFF) * share);
		int g = Math.round((a >> 8 & 0xFF) * (1 - share) + (b >> 8 & 0xFF) * share);
		int bl = Math.round((a & 0xFF) * (1 - share) + (b & 0xFF) * share);
		return r << 16 | g << 8 | bl;
	}

	private static int turnHue(int rgb, float degrees) {
		float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
		return java.awt.Color.HSBtoRGB((hsb[0] + degrees / 360f) % 1f, hsb[1], hsb[2]) & 0xFFFFFF;
	}
}
