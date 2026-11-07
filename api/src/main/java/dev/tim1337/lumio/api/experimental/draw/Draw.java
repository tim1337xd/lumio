package dev.tim1337.lumio.api.experimental.draw;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.spi.Backend;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Drawing helpers shared by every {@link Canvas}: the window opacity everything is faded by, the menu's zoom, color math, and making a canvas
 * for drawing outside a panel. The color math is plain and works anywhere; the rest needs the running game.
 */
public final class Draw {
	/** Global opacity from 0 to 1 for windows fading in and out; everything drawn on a canvas multiplies it into its alpha. */
	private static float alpha = 1f;
	/** Extra scale of the menu, set by the screen being drawn. */
	private static float zoom = 1f;
	/** The baseline of text in units of its scale, for lining up text of two sizes. */
	private static final float BASELINE = 7f;

	private Draw() {
	}

	/** Sets the opacity everything drawn from now on is faded by, from 0 to 1; set it back to 1 when done. */
	public static void setAlpha(float value) {
		alpha = Mth.clamp(value, 0f, 1f);
	}

	public static float alpha() {
		return alpha;
	}

	/** @return the color with the current opacity applied */
	public static int faded(int color) {
		return alpha >= 1f ? color : withOpacity(color, alpha);
	}

	public static void setZoom(float value) {
		zoom = value;
	}

	public static float zoom() {
		return zoom;
	}

	/** @return the color with its alpha multiplied by the factor */
	public static int withOpacity(int argb, float factor) {
		int a = Mth.clamp(Math.round((argb >>> 24) * factor), 0, 255);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** @return the result of alpha-compositing {@code top} over {@code under} */
	public static int over(int top, int under) {
		int ta = top >>> 24;
		int ua = under >>> 24;
		if (ta == 0) {
			return under;
		}
		if (ua == 0) {
			return top;
		}
		float t = ta / 255f;
		float u = ua / 255f * (1f - t);
		float a = t + u;
		int r = Math.round(((top >> 16 & 0xFF) * t + (under >> 16 & 0xFF) * u) / a);
		int g = Math.round(((top >> 8 & 0xFF) * t + (under >> 8 & 0xFF) * u) / a);
		int b = Math.round(((top & 0xFF) * t + (under & 0xFF) * u) / a);
		return (Math.round(a * 255f) << 24) | (r << 16) | (g << 8) | b;
	}

	/** @return the color between two colors, at the amount from 0 (from) to 1 (to) */
	public static int mix(int from, int to, float amount) {
		int a = Math.round(Mth.lerp(amount, from >>> 24, to >>> 24));
		int r = Math.round(Mth.lerp(amount, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(amount, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(amount, from & 0xFF, to & 0xFF));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** @return how light a color looks, from 0 (black) to 1 (white), weighting its channels as the eye does */
	public static float luminance(int argb) {
		return (0.299f * (argb >> 16 & 0xFF) + 0.587f * (argb >> 8 & 0xFF) + 0.114f * (argb & 0xFF)) / 255f;
	}

	/**
	 * @return the number without trailing fractional zeros, and without the decimal point if nothing is left ("1.50" -> "1.5", "2.00" -> "2");
	 *         numbers without a point are returned unchanged. A loop rather than a regex, since it runs every frame
	 */
	public static String trimZeros(String number) {
		if (number.indexOf('.') < 0) {
			return number;
		}
		int end = number.length();
		while (end > 0 && number.charAt(end - 1) == '0') {
			end--;
		}
		if (end > 0 && number.charAt(end - 1) == '.') {
			end--;
		}
		return number.substring(0, end);
	}

	/** @return the top of small text that shares its baseline with large text at largeTop */
	public static float besideTop(float largeTop, float largeScale, float smallScale) {
		return largeTop + BASELINE * (largeScale - smallScale);
	}

	/** @return the width of text at a scale, as a canvas would draw it, without a canvas */
	public static float measureScaled(String text, float scale) {
		return Backend.get().measureScaled(text, scale);
	}

	/** @return a canvas in the HUD panels' look, for drawing outside a panel (a toast, an overlay over a screen) */
	public static Canvas hudCanvas(GuiGraphicsExtractor graphics, int accent, float deltaSeconds, boolean animated) {
		return Backend.get().hudCanvas(graphics, accent, deltaSeconds, animated);
	}
}
