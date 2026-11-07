package dev.tim1337.lumio.features.music;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the colors of a cover for the Colored theme: up to three strong, distinct ones. Near-black, near-white and gray areas do not count, and what is picked
 * is made a bit richer. Plain numbers, so it can be tested without the game.
 */
public final class CoverColors {
	/** The side of the small picture of the cover that the colors are read from. */
	public static final int SIDE = 32;
	/** The most colors picked. */
	public static final int MOST = 3;

	private static final int HUES = 24;
	/** Below this brightness a pixel counts as black, and with less saturation than this as gray; above this brightness with little saturation, as white. */
	private static final float LEAST_BRIGHTNESS = 0.14f;
	private static final float LEAST_SATURATION = 0.18f;
	/** How little of the cover a color may cover and still be picked: a speck is no color of the cover. */
	private static final float LEAST_SHARE = 0.012f;
	/** How far apart two picked colors lie on the color wheel at least, as a share of it. */
	private static final float LEAST_HUE_DISTANCE = 1f / 12f;
	/** How much richer a picked color is made, and the least brightness it ends up with. */
	private static final float RICHER = 1.18f;
	private static final float LEAST_RESULT_BRIGHTNESS = 0.45f;

	private CoverColors() {
	}

	/**
	 * @param rgb the pixels, 0xRRGGBB in the low bytes
	 * @return up to three colors, the strongest first; empty when the cover is gray, black or white
	 */
	public static int[] pick(int[] rgb) {
		float[] weight = new float[HUES];
		float[] red = new float[HUES];
		float[] green = new float[HUES];
		float[] blue = new float[HUES];
		float[] count = new float[HUES];
		float[] hsb = new float[3];
		for (int pixel : rgb) {
			int r = pixel >> 16 & 0xFF;
			int g = pixel >> 8 & 0xFF;
			int b = pixel & 0xFF;
			toHsb(r, g, b, hsb);
			if (hsb[2] < LEAST_BRIGHTNESS || hsb[1] < LEAST_SATURATION) {
				continue;
			}
			int group = Math.min(HUES - 1, (int) (hsb[0] * HUES));
			float strength = hsb[1] * hsb[2];
			weight[group] += strength;
			red[group] += r * strength;
			green[group] += g * strength;
			blue[group] += b * strength;
			count[group]++;
		}
		float leastCount = rgb.length * LEAST_SHARE;
		List<float[]> picked = new ArrayList<>(MOST);
		for (int round = 0; round < MOST; round++) {
			int best = -1;
			for (int group = 0; group < HUES; group++) {
				if (count[group] >= leastCount && weight[group] > 0f && (best < 0 || weight[group] > weight[best]) && farFrom(picked, group)) {
					best = group;
				}
			}
			if (best < 0) {
				break;
			}
			picked.add(new float[]{best, red[best] / weight[best], green[best] / weight[best], blue[best] / weight[best]});
			weight[best] = 0f;
		}
		int[] colors = new int[picked.size()];
		for (int i = 0; i < colors.length; i++) {
			float[] color = picked.get(i);
			colors[i] = richer(color[1], color[2], color[3]);
		}
		return colors;
	}

	private static boolean farFrom(List<float[]> picked, int group) {
		for (float[] color : picked) {
			float distance = Math.abs(color[0] - group) / HUES;
			if (Math.min(distance, 1f - distance) < LEAST_HUE_DISTANCE) {
				return false;
			}
		}
		return true;
	}

	private static int richer(float r, float g, float b) {
		float[] hsb = new float[3];
		toHsb(Math.round(r), Math.round(g), Math.round(b), hsb);
		return fromHsb(hsb[0], Math.min(1f, hsb[1] * RICHER), Math.max(LEAST_RESULT_BRIGHTNESS, hsb[2]));
	}

	private static void toHsb(int r, int g, int b, float[] out) {
		int most = Math.max(r, Math.max(g, b));
		int least = Math.min(r, Math.min(g, b));
		float range = most - least;
		out[2] = most / 255f;
		out[1] = most == 0 ? 0f : range / most;
		if (range == 0f) {
			out[0] = 0f;
			return;
		}
		float hue = most == r ? (g - b) / range : most == g ? 2f + (b - r) / range : 4f + (r - g) / range;
		hue /= 6f;
		out[0] = hue < 0f ? hue + 1f : hue;
	}

	private static int fromHsb(float hue, float saturation, float brightness) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		int sector = (int) h;
		float f = h - sector;
		float p = brightness * (1f - saturation);
		float q = brightness * (1f - saturation * f);
		float t = brightness * (1f - saturation * (1f - f));
		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> { r = brightness; g = t; b = p; }
			case 1 -> { r = q; g = brightness; b = p; }
			case 2 -> { r = p; g = brightness; b = t; }
			case 3 -> { r = p; g = q; b = brightness; }
			case 4 -> { r = t; g = p; b = brightness; }
			default -> { r = brightness; g = p; b = q; }
		}
		return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
	}

	/**
	 * @param argb a square cover's pixels
	 * @param side its side
	 * @return the cover scaled to {@link #SIDE} by averaging, for {@link #pick}
	 */
	static int[] small(int[] argb, int side) {
		int[] out = new int[SIDE * SIDE];
		for (int y = 0; y < SIDE; y++) {
			int y0 = y * side / SIDE;
			int y1 = Math.max(y0 + 1, (y + 1) * side / SIDE);
			for (int x = 0; x < SIDE; x++) {
				int x0 = x * side / SIDE;
				int x1 = Math.max(x0 + 1, (x + 1) * side / SIDE);
				int r = 0;
				int g = 0;
				int b = 0;
				int n = 0;
				for (int py = y0; py < y1 && py < side; py++) {
					for (int px = x0; px < x1 && px < side; px++) {
						int pixel = argb[py * side + px];
						r += pixel >> 16 & 0xFF;
						g += pixel >> 8 & 0xFF;
						b += pixel & 0xFF;
						n++;
					}
				}
				out[y * SIDE + x] = n == 0 ? 0 : r / n << 16 | g / n << 8 | b / n;
			}
		}
		return out;
	}
}
