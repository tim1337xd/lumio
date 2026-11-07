package dev.tim1337.lumio.features.music;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Prepares a cover for the GPU on the thread that downloaded it, never on the render thread: cropped to a square, scaled down once with area
 * averaging, and with rounded corners. A large image for the places the cover is shown big (Home, the main menu card) and a small one for the
 * HUD player, so neither is blurred by the GPU stretching or shrinking it far.
 */
final class CoverImages {
	/** Largest side of the big cover: enough for the largest place it is drawn at high GUI scales, and no more. */
	static final int LARGE = 512;
	/** Side of the small cover, for the HUD player and other small places. */
	static final int SMALL = 128;
	/** Corner radius as a share of the side, the same as the shapes drawn around covers. */
	private static final float CORNER = 0.24f;

	/** How many hue groups the cover's colors are sorted into, and how little color a cover may have before it counts as having none. */
	private static final int HUES = 24;
	private static final float LEAST_COLOR = 0.02f;

	/**
	 * The two sizes of one cover, and its most vivid color (0 for a cover without color); the images belong to whoever takes them, who closes
	 * them or hands them to a texture.
	 */
	record Prepared(NativeImage large, NativeImage small, int color, int[] palette) {
		void close() {
			large.close();
			small.close();
		}
	}

	private CoverImages() {
	}

	static Prepared prepare(BufferedImage source) {
		int side = Math.min(source.getWidth(), source.getHeight());
		int[] argb = source.getRGB((source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side, null, 0, side);
		return new Prepared(scaled(argb, side, Math.min(LARGE, side)), scaled(argb, side, Math.min(SMALL, side)), vivid(argb), CoverColors.pick(CoverColors.small(argb, side)));
	}

	/**
	 * @return the cover's most vivid color: its pixels sorted into hue groups, each weighted by how saturated and bright it is, and the average
	 *         of the strongest group; 0 when the cover is nearly grey
	 */
	private static int vivid(int[] argb) {
		float[] weight = new float[HUES];
		float[] red = new float[HUES];
		float[] green = new float[HUES];
		float[] blue = new float[HUES];
		float[] hsb = new float[3];
		float total = 0f;
		int step = Math.max(1, argb.length / 4096);
		for (int i = 0; i < argb.length; i += step) {
			int pixel = argb[i];
			int r = pixel >> 16 & 0xFF;
			int g = pixel >> 8 & 0xFF;
			int b = pixel & 0xFF;
			Color.RGBtoHSB(r, g, b, hsb);
			float w = hsb[1] * hsb[2] * hsb[1];
			int hue = Math.min(HUES - 1, (int) (hsb[0] * HUES));
			weight[hue] += w;
			red[hue] += r * w;
			green[hue] += g * w;
			blue[hue] += b * w;
			total += w;
		}
		int best = 0;
		for (int hue = 1; hue < HUES; hue++) {
			if (weight[hue] > weight[best]) {
				best = hue;
			}
		}
		int samples = (argb.length + step - 1) / step;
		if (total / Math.max(1, samples) < LEAST_COLOR || weight[best] <= 0f) {
			return 0;
		}
		return 0xFF000000 | Math.round(red[best] / weight[best]) << 16 | Math.round(green[best] / weight[best]) << 8 | Math.round(blue[best] / weight[best]);
	}

	/** @return the square picture scaled to a size by averaging the source pixels each target pixel covers, with rounded corners */
	private static NativeImage scaled(int[] argb, int side, int size) {
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		float radius = size * CORNER;
		for (int y = 0; y < size; y++) {
			int y0 = y * side / size;
			int y1 = Math.max(y0 + 1, (y + 1) * side / size);
			for (int x = 0; x < size; x++) {
				int x0 = x * side / size;
				int x1 = Math.max(x0 + 1, (x + 1) * side / size);
				long r = 0;
				long g = 0;
				long b = 0;
				int count = 0;
				for (int sy = y0; sy < y1; sy++) {
					for (int sx = x0; sx < x1; sx++) {
						int pixel = argb[sy * side + sx];
						r += pixel >> 16 & 0xFF;
						g += pixel >> 8 & 0xFF;
						b += pixel & 0xFF;
						count++;
					}
				}
				float dx = Math.max(0f, Math.abs(x + 0.5f - size / 2f) - (size / 2f - radius));
				float dy = Math.max(0f, Math.abs(y + 0.5f - size / 2f) - (size / 2f - radius));
				float coverage = Mth.clamp(radius - Mth.sqrt(dx * dx + dy * dy) + 0.5f, 0f, 1f);
				image.setPixelABGR(x, y, (Math.round(coverage * 255f) << 24) | ((int) (b / count) << 16) | ((int) (g / count) << 8) | (int) (r / count));
			}
		}
		return image;
	}
}
