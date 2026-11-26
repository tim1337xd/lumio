package dev.tim1337.lumio.render;

import java.awt.image.BufferedImage;

/**
 * The Cut-out symbols as a signed distance field: they are drawn in code as shapes ({@link CutoutIcons}), so their picture is turned into
 * distances once (an exact Euclidean distance transform of what they cover and what they do not), and the shader draws them with clean edges
 * at any size like the letters. Plain logic, tested without the game.
 */
final class SdfIcons {
	/** A symbol's cell in pixels, and the distance half the value covers, in pixels. */
	static final int CELL = 96;
	static final int RANGE = 6;
	private static final double FAR = 1e20;

	private SdfIcons() {
	}

	/** @return a value per pixel of the sheet: 128 on the outline, more inside, less outside, one {@link #RANGE} being 127 */
	static byte[] field(BufferedImage sheet, int range) {
		int w = sheet.getWidth();
		int h = sheet.getHeight();
		double[] inside = new double[w * h];
		double[] outside = new double[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				boolean covered = (sheet.getRGB(x, y) >>> 24) >= 128;
				inside[y * w + x] = covered ? FAR : 0;
				outside[y * w + x] = covered ? 0 : FAR;
			}
		}
		transform(inside, w, h);
		transform(outside, w, h);
		byte[] field = new byte[w * h];
		for (int i = 0; i < field.length; i++) {
			// inside: the distance to the nearest uncovered pixel; outside: to the nearest covered one; the outline lies half a pixel between
			double distance = inside[i] > 0 ? Math.sqrt(inside[i]) - 0.5 : -(Math.sqrt(outside[i]) - 0.5);
			double value = 0.5 + distance / (2.0 * range);
			field[i] = (byte) Math.round(Math.max(0, Math.min(1, value)) * 255);
		}
		return field;
	}

	/** Squared Euclidean distance transform in place (Felzenszwalb and Huttenlocher): rows, then columns. */
	private static void transform(double[] grid, int w, int h) {
		double[] line = new double[Math.max(w, h)];
		double[] out = new double[Math.max(w, h)];
		int[] v = new int[Math.max(w, h)];
		double[] z = new double[Math.max(w, h) + 1];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				line[y] = grid[y * w + x];
			}
			one(line, h, out, v, z);
			for (int y = 0; y < h; y++) {
				grid[y * w + x] = out[y];
			}
		}
		for (int y = 0; y < h; y++) {
			System.arraycopy(grid, y * w, line, 0, w);
			one(line, w, out, v, z);
			System.arraycopy(out, 0, grid, y * w, w);
		}
	}

	private static void one(double[] f, int n, double[] d, int[] v, double[] z) {
		int k = 0;
		v[0] = 0;
		z[0] = -FAR;
		z[1] = FAR;
		for (int q = 1; q < n; q++) {
			double s = ((f[q] + q * (double) q) - (f[v[k]] + v[k] * (double) v[k])) / (2.0 * q - 2.0 * v[k]);
			while (s <= z[k]) {
				k--;
				s = ((f[q] + q * (double) q) - (f[v[k]] + v[k] * (double) v[k])) / (2.0 * q - 2.0 * v[k]);
			}
			k++;
			v[k] = q;
			z[k] = s;
			z[k + 1] = FAR;
		}
		k = 0;
		for (int q = 0; q < n; q++) {
			while (z[k + 1] < q) {
				k++;
			}
			d[q] = (q - v[k]) * (double) (q - v[k]) + f[v[k]];
		}
	}
}
