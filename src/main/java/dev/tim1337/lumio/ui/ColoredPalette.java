package dev.tim1337.lumio.ui;

/**
 * What the Colored theme makes of the player's one to three colors: the colors of its soft fields and of the dark base under them, and how dark the
 * layer between them and the content has to be. Plain numbers, so it can be tested without the game.
 */
public final class ColoredPalette {
	/** Which of the colors each field has, and which are its deep tone: rows of four, five and four, where no neighbors are alike. */
	private static final int[] FIELD_TONES = {0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 2, 0, 1};
	private static final boolean[] FIELD_DEEP = {false, false, false, true, false, false, true, false, false, true, false, false, true};
	/** The rounds of swapping when new colors are matched to the fields, and how much a swap has to win. */
	private static final int MATCH_ROUNDS = 4;
	private static final double MATCH_MARGIN = 1e-4;
	/** How many fields of color drift over the base: enough, and small enough, that even a thin bar always has two colors flowing across it. */
	public static final int FIELDS = 13;
	/** The most colors a player can pick, and what a fresh config has. */
	public static final int MOST_COLORS = 3;
	public static final int[] DEFAULT_COLORS = {0xFF2E63, 0x2F6BFF, 0xA23BFF};
	/** The color of the layer between the colors and the content. */
	public static final int LAYER_RGB = 0x08060E;

	/** White text needs 4.5:1 against what is behind it, so that may be this luminance at most: (1 + 0.05) / 4.5 - 0.05. */
	static final double BRIGHTEST_BEHIND_TEXT = 1.05 / 4.5 - 0.05;
	/** A little in hand, for the gloss along the top of a pane. */
	private static final double GLOSS_MARGIN = 0.012;

	/** A deep tone of a color, and how much white a light one has. */
	private static final float DEEP = 0.72f;
	private static final float LIGHT = 0.22f;
	/** How much of the colors the dark base has. */
	private static final float BASE = 0.28f;

	private static final int LAYER_SEARCH_STEPS = 12;
	/** The contrast white text has at least on what has the accent color: the 3:1 of large text and of controls. */
	private static final double ACCENT_CONTRAST = 3.0;

	private ColoredPalette() {
	}

	/**
	 * @param colors one to three colors as 0xRRGGBB, in the order they flow
	 * @return the colors of the fields, in the places of {@code ColoredFlow} (rows of four, five and four across the screen): neighbors always differ,
	 *         so the boundaries between them show. One color flows through its light and deep tones; two through each other and their mix.
	 */
	public static int[] fields(int[] colors) {
		int a = colors[0];
		int[] tones = switch (colors.length) {
			case 1 -> new int[]{a, deep(a), light(a)};
			case 2 -> new int[]{a, colors[1], mix(a, colors[1], 0.5f)};
			default -> new int[]{a, colors[1], colors[2]};
		};
		int[] fields = new int[FIELDS];
		for (int i = 0; i < FIELDS; i++) {
			int tone = tones[FIELD_TONES[i]];
			fields[i] = FIELD_DEEP[i] && colors.length > 1 ? deep(tone) : tone;
		}
		return fields;
	}

	/**
	 * Puts new colors in the order that fits the fields as they are now, so each field goes to the color nearest to its own and none crosses the screen
	 * to swap places. Tries every pair of swaps, not all orders, which is plenty: it runs when the colors change, not per frame.
	 *
	 * @return {@code wanted} in a new order
	 */
	public static int[] matched(int[] shown, int[] wanted) {
		int[] order = wanted.clone();
		boolean better = true;
		for (int round = 0; better && round < MATCH_ROUNDS; round++) {
			better = false;
			for (int i = 0; i < order.length; i++) {
				for (int j = i + 1; j < order.length; j++) {
					double now = distance(shown[i], order[i]) + distance(shown[j], order[j]);
					double swapped = distance(shown[i], order[j]) + distance(shown[j], order[i]);
					if (swapped + MATCH_MARGIN < now) {
						int keep = order[i];
						order[i] = order[j];
						order[j] = keep;
						better = true;
					}
				}
			}
		}
		return order;
	}

	/** @return how far apart two colors look, in OKLab */
	public static double distance(int a, int b) {
		float[] first = oklab(a);
		float[] second = oklab(b);
		double dl = first[0] - second[0];
		double da = first[1] - second[1];
		double db = first[2] - second[2];
		return Math.sqrt(dl * dl + da * da + db * db);
	}

	/** @return two colors mixed as the eye sees it, in OKLab: a blend between two colors passes no gray or dark muddy step, unlike a mix of their numbers */
	public static int mixPerceptual(int a, int b, float share) {
		if (share <= 0f) {
			return a & 0xFFFFFF;
		}
		if (share >= 1f) {
			return b & 0xFFFFFF;
		}
		float[] first = oklab(a);
		float[] second = oklab(b);
		return rgb(first[0] + (second[0] - first[0]) * share, first[1] + (second[1] - first[1]) * share, first[2] + (second[2] - first[2]) * share);
	}

	private static float[] oklab(int rgb) {
		double r = linear(rgb >> 16 & 0xFF);
		double g = linear(rgb >> 8 & 0xFF);
		double b = linear(rgb & 0xFF);
		double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
		double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
		double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
		return new float[]{(float) (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s), (float) (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s),
			(float) (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)};
	}

	private static int rgb(float lightness, float a, float b) {
		double l = cube(lightness + 0.3963377774 * a + 0.2158037573 * b);
		double m = cube(lightness - 0.1055613458 * a - 0.0638541728 * b);
		double s = cube(lightness - 0.0894841775 * a - 1.2914855480 * b);
		return encoded(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s) << 16 | encoded(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s) << 8
			| encoded(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
	}

	private static double cube(double value) {
		return value * value * value;
	}

	private static int encoded(double linear) {
		double c = Math.clamp(linear, 0.0, 1.0);
		return (int) Math.round((c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055) * 255.0);
	}

	/** @return the dark base under the fields, made from the colors */
	public static int base(int[] colors) {
		int r = 0;
		int g = 0;
		int b = 0;
		for (int color : colors) {
			r += color >> 16 & 0xFF;
			g += color >> 8 & 0xFF;
			b += color & 0xFF;
		}
		int count = colors.length;
		return scale(r / count << 16 | g / count << 8 | b / count, BASE);
	}

	/**
	 * How dark the layer between the colors and the content has to be, so white text has its contrast over the brightest part of the fields. It darkens the
	 * layer only, never the colors.
	 *
	 * @param colors     the colors
	 * @param wanted     how dark the layer is when the colors allow it, from 0 to 1
	 * @param worldShare how much of the world shows through under the colors; it counts as white, the worst it can be
	 * @return the layer's opacity, from 0 to 1, never below {@code wanted}
	 */
	public static float layer(int[] colors, float wanted, float worldShare) {
		return layerOver(fields(colors), wanted, worldShare);
	}

	/** Same over fields as they are shown, with what the player's Intensity and the music do to them. */
	public static float layerOver(int[] fields, float wanted, float worldShare) {
		int brightest = 0;
		double most = -1.0;
		for (int color : fields) {
			double luminance = luminance(mix(color, 0xFFFFFF, worldShare));
			if (luminance > most) {
				most = luminance;
				brightest = mix(color, 0xFFFFFF, worldShare);
			}
		}
		double limit = BRIGHTEST_BEHIND_TEXT - GLOSS_MARGIN;
		if (luminance(mix(brightest, LAYER_RGB, wanted)) <= limit) {
			return wanted;
		}
		float low = wanted;
		float high = 1f;
		for (int i = 0; i < LAYER_SEARCH_STEPS; i++) {
			float middle = (low + high) / 2f;
			if (luminance(mix(brightest, LAYER_RGB, middle)) <= limit) {
				high = middle;
			} else {
				low = middle;
			}
		}
		return high;
	}

	/**
	 * @param rgb the first color
	 * @return the accent that follows it: the color itself, or as much darker as white text on a button of it needs
	 */
	public static int accent(int rgb) {
		if (whiteContrast(rgb) >= ACCENT_CONTRAST) {
			return rgb;
		}
		float low = 0f;
		float high = 1f;
		for (int i = 0; i < LAYER_SEARCH_STEPS; i++) {
			float middle = (low + high) / 2f;
			if (whiteContrast(scale(rgb, middle)) >= ACCENT_CONTRAST) {
				low = middle;
			} else {
				high = middle;
			}
		}
		return scale(rgb, low);
	}

	/** @return the contrast of white text over a color, from 1 to 21 */
	public static double whiteContrast(int rgb) {
		return 1.05 / (luminance(rgb) + 0.05);
	}

	/** @return how bright a color looks, from 0 to 1 */
	public static double luminance(int rgb) {
		return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
	}

	private static double linear(int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	/** @return two colors mixed: a share of the second one */
	public static int mix(int a, int b, float share) {
		return channel(a >> 16 & 0xFF, b >> 16 & 0xFF, share) << 16 | channel(a >> 8 & 0xFF, b >> 8 & 0xFF, share) << 8 | channel(a & 0xFF, b & 0xFF, share);
	}

	private static int channel(int a, int b, float share) {
		return Math.round(a + (b - a) * share);
	}

	private static int deep(int rgb) {
		return scale(rgb, DEEP);
	}

	private static int light(int rgb) {
		return mix(rgb, 0xFFFFFF, LIGHT);
	}

	private static int scale(int rgb, float by) {
		return mix(0, rgb, by);
	}
}
