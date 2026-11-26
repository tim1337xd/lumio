package dev.tim1337.lumio.render;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A picture of the glyphs of a font as distances (multi-channel signed distance field, with the true distance in the fourth channel): from
 * it a shader draws the letters with clean anti-aliased edges at any size and angle, and a soft glow from the fourth channel. Each glyph is
 * drawn from its outline (no picture of the letter in between), in cells packed into rows. A value of 0.5 is the edge, more is inside; one
 * {@link #range} of distance in the atlas's pixels is a value of 0.5. Plain logic on AWT, tested without the game.
 */
public final class SdfAtlas {
	/** Where a glyph lies: its box from the pen's place on the baseline in em (down positive), its cell in the atlas in pixels, its advance. */
	public record Glyph(float left, float top, float right, float bottom, int atlasX, int atlasY, int width, int height, float advance) {
	}

	/** The characters of the lyrics' atlas: printable ASCII, the Latin-1 letters and marks (umlauts, ß, accents), and typographic quotes and dashes. */
	public static final String LATIN = latin();

	private static String latin() {
		StringBuilder chars = new StringBuilder();
		for (char c = 33; c < 127; c++) {
			chars.append(c);
		}
		for (char c = 161; c <= 255; c++) {
			chars.append(c);
		}
		return chars.append("\u2018\u2019\u201A\u201C\u201D\u201E\u2013\u2014\u2026\u2022\u20AC\u0152\u0153\u0160\u0161\u017D\u017E\u0178").toString();
	}

	private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
	/** A pixel this far past the edge in the other channels' favor is taken from the true distance instead (where the channels disagree). */
	private static final double CLASH = 0.5;

	public final int width;
	public final int height;
	/** The atlas's pixels, four bytes each: red, green, blue (the three distances) and alpha (the true distance). */
	public final byte[] rgba;
	/** The glyphs' size in the atlas (pixels per em) and the distance one half of the value covers, in pixels. */
	public final int em;
	public final int range;
	private final Map<Character, Glyph> glyphs;

	private SdfAtlas(int width, int height, byte[] rgba, int em, int range, Map<Character, Glyph> glyphs) {
		this.width = width;
		this.height = height;
		this.rgba = rgba;
		this.em = em;
		this.range = range;
		this.glyphs = glyphs;
	}

	/** @return a character's glyph, or null when the atlas has none */
	public Glyph glyph(char c) {
		return glyphs.get(c);
	}

	/** @return whether the atlas has a glyph for every character of a text (spaces need none) */
	public boolean covers(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (!Character.isWhitespace(c) && !glyphs.containsKey(c)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Builds the atlas.
	 *
	 * @param font  the font, at any size
	 * @param chars the characters to put in
	 * @param em    the glyphs' size in pixels per em
	 * @param range half the distance the values span, in pixels
	 * @param size  the atlas's width and height, in pixels
	 */
	public static SdfAtlas build(Font font, String chars, int em, int range, int size) {
		Font sized = font.deriveFont((float) em);
		byte[] rgba = new byte[size * size * 4];
		Map<Character, Glyph> glyphs = new HashMap<>();
		int x = 0;
		int y = 0;
		int rowHeight = 0;
		double[] info = new double[3];
		for (int i = 0; i < chars.length(); i++) {
			char c = chars.charAt(i);
			if (Character.isWhitespace(c) || glyphs.containsKey(c) || !sized.canDisplay(c)) {
				continue;
			}
			GlyphVector vector = sized.createGlyphVector(CONTEXT, String.valueOf(c));
			Shape outline = vector.getGlyphOutline(0);
			Rectangle2D bounds = outline.getBounds2D();
			float advance = vector.getGlyphMetrics(0).getAdvance() / em;
			if (bounds.isEmpty()) {
				glyphs.put(c, new Glyph(0, 0, 0, 0, 0, 0, 0, 0, advance));
				continue;
			}
			int left = (int) Math.floor(bounds.getMinX()) - range;
			int top = (int) Math.floor(bounds.getMinY()) - range;
			int w = (int) Math.ceil(bounds.getMaxX()) + range - left;
			int h = (int) Math.ceil(bounds.getMaxY()) + range - top;
			if (x + w > size) {
				x = 0;
				y += rowHeight + 1;
				rowHeight = 0;
			}
			if (y + h > size) {
				throw new IllegalStateException("the glyphs do not fit an atlas of " + size + " pixels");
			}
			Shape moved = AffineTransform.getTranslateInstance(-left, -top).createTransformedShape(outline);
			List<List<SdfEdges.Edge>> contours = SdfEdges.contours(moved);
			fill(rgba, size, x, y, w, h, contours, moved, range, info);
			glyphs.put(c, new Glyph(left / (float) em, top / (float) em, (left + w) / (float) em, (top + h) / (float) em, x, y, w, h, advance));
			x += w + 1;
			rowHeight = Math.max(rowHeight, h);
		}
		return new SdfAtlas(size, size, rgba, em, range, glyphs);
	}

	/** Fills a glyph's cell: per pixel the three channels' distances and the true one. */
	private static void fill(byte[] rgba, int size, int cellX, int cellY, int w, int h, List<List<SdfEdges.Edge>> contours, Shape shape, int range,
							 double[] info) {
		// the edges' own sides are the right way round when the nearest edge agrees with the outline's inside: the font may wind either way
		int agree = 0;
		int disagree = 0;
		double[][] values = new double[w * h][4];
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				double sx = px + 0.5;
				double sy = py + 0.5;
				double[] channel = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
				double[] along = {2, 2, 2};
				double[] value = {0, 0, 0};
				double nearest = Double.MAX_VALUE;
				double nearestSigned = 0;
				for (List<SdfEdges.Edge> contour : contours) {
					for (SdfEdges.Edge edge : contour) {
						SdfEdges.distance(edge, sx, sy, info);
						if (info[0] < nearest) {
							nearest = info[0];
							nearestSigned = info[1] >= 0 ? info[0] : -info[0];
						}
						for (int ch = 0; ch < 3; ch++) {
							if ((edge.color & (1 << ch)) == 0) {
								continue;
							}
							// the nearest edge of the channel; between two as near, the one that runs more across the way to the point
							if (info[0] < channel[ch] - 1e-9 || Math.abs(info[0] - channel[ch]) <= 1e-9 && info[2] < along[ch]) {
								channel[ch] = info[0];
								along[ch] = info[2];
								value[ch] = info[1];
							}
						}
					}
				}
				boolean inside = shape.contains(sx, sy);
				if (nearest < range * 4 && nearest > 0.5) {
					if (nearestSigned > 0 == inside) {
						agree++;
					} else {
						disagree++;
					}
				}
				double[] v = values[py * w + px];
				v[0] = value[0];
				v[1] = value[1];
				v[2] = value[2];
				v[3] = inside ? nearest : -nearest;
			}
		}
		double flip = disagree > agree ? -1 : 1;
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				double[] v = values[py * w + px];
				double r = v[0] * flip;
				double g = v[1] * flip;
				double b = v[2] * flip;
				double truth = v[3];
				// where the channels' median falls on the other side than the outline says, it would show as a speck: the true distance there
				double median = Math.max(Math.min(r, g), Math.min(Math.max(r, g), b));
				if (median > 0 != truth > 0 && Math.abs(truth) > CLASH) {
					r = truth;
					g = truth;
					b = truth;
				}
				int at = ((cellY + py) * size + cellX + px) * 4;
				rgba[at] = encode(r, range);
				rgba[at + 1] = encode(g, range);
				rgba[at + 2] = encode(b, range);
				rgba[at + 3] = encode(truth, range);
			}
		}
	}

	private static byte encode(double distance, int range) {
		double value = 0.5 + distance / (2.0 * range);
		return (byte) Math.round(Math.max(0, Math.min(1, value)) * 255);
	}

	/** @return a stored value as a distance in the atlas's pixels: inside positive */
	public double distance(int x, int y, int channel) {
		int value = rgba[(y * width + x) * 4 + channel] & 0xFF;
		return (value / 255.0 - 0.5) * 2.0 * range;
	}
}
