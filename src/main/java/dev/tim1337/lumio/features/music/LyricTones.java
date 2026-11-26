package dev.tim1337.lumio.features.music;

/**
 * Colors of the lyrics in the world that read against what lies behind them, by contrast alone: no backdrop is drawn, so the letters
 * themselves must stand out. Contrast is WCAG's: the relative luminance of two colors, as {@code (lighter + 0.05) / (darker + 0.05)}.
 * <ul>
 * <li>On a dark background (night, Molten, a cave) the words are a clean light tint of the line's color, the word being sung white, and they glow.</li>
 * <li>On a bright one (the day's sky, snow, sand) they are a rich, saturated tone of it, and nothing light glows around them.</li>
 * <li>In between, where neither a tint nor a tone that still has color reads well enough, the tint gets a thin tight shadow, as strong as it
 * needs to be.</li>
 * </ul>
 * Lyrics are large text: from {@link #LARGE_FROM_PIXELS} on the screen they need {@link #LARGE_CONTRAST}, below {@link #SMALL_BELOW_PIXELS}
 * {@link #SMALL_CONTRAST}. A color never turns grey: it is made darker by scaling it in linear light (which keeps its saturation), yellows turning
 * a little toward orange as they do, since a dark yellow reads as olive; and lighter by mixing it toward white only so far that it stays a tint.
 * Plain logic, tested without the game.
 */
final class LyricTones {
	/** The least contrast of large and of small words and tile letters against what is behind them, and of a tile against what lies behind it. */
	static final float LARGE_CONTRAST = 3f;
	static final float SMALL_CONTRAST = 4.5f;
	static final float TILE_CONTRAST = 2f;
	/** Text this tall on the screen (its em, in pixels) is large; below the other it is small; in between the contrast goes over evenly, so it never jumps. */
	static final float LARGE_FROM_PIXELS = 24f;
	static final float SMALL_BELOW_PIXELS = 20f;
	/** The word being sung before a bright background stands out from the others by being deeper: this contrast. */
	static final float SUNG_DEEP_CONTRAST = 4.5f;
	/** A line already sung dims toward what lies behind it, keeping this share of the difference, never to less than {@link #SUNG_CONTRAST}. */
	static final float DIM_KEEPS = 0.4f;
	static final float SUNG_CONTRAST = 3f;
	/** Where a tone has no room left to dim (it stands at the least contrast already), it recedes by keeping this much of its saturation. */
	private static final float QUIET_SATURATION = 0.88f;
	/** A light tone made darker takes this share of the saturation it still lacks: dark and pale together would read as grey. */
	private static final float DIM_RICHER = 0.35f;
	/** A dimming that changes the luminance (plus the flare of 0.05) by less than this share has no visible effect. */
	private static final float DIM_SEEN = 0.15f;
	/** Each target is aimed at this much above it, so rounding the color to whole channels never falls short of it. */
	private static final float MARGIN = 1.03f;
	/** The light words: a tint of the line's color with this share of its saturation, kept between the two bounds, and at least this light. */
	private static final float TINT_SHARE = 1f;
	static final float TINT_LEAST_SATURATION = 0.55f;
	private static final float TINT_MOST_SATURATION = 0.75f;
	private static final float TINT_LEAST_LUMINANCE = 0.42f;
	/** The deep words: at least this saturated, richer than the line's own color by this much, and no darker than this value (HSV). */
	static final float DEEP_LEAST_SATURATION = 0.75f;
	private static final float DEEP_RICHER = 1.35f;
	private static final float DEEP_LEAST_VALUE = 0.4f;
	/** A deep word is no lighter than this, even where the background would allow a lighter one. */
	static final float DEEP_MOST_LUMINANCE = 0.2f;
	/** Yellows (between these hues, fading out over the outer ones) turn this many degrees toward orange at full darkness. */
	private static final float YELLOW_FROM = 25f;
	private static final float YELLOW_FULL_FROM = 38f;
	private static final float YELLOW_FULL_TO = 62f;
	private static final float YELLOW_TO = 80f;
	private static final float YELLOW_TURN = 14f;
	/** From this luminance down a yellow starts to turn, and from the other it has turned all the way. */
	private static final float TURN_FROM_LUMINANCE = 0.5f;
	private static final float TURN_FULL_LUMINANCE = 0.12f;
	/** A tile: at least this saturated, richer than the line's color by this much. */
	private static final float TILE_LEAST_SATURATION = 0.7f;
	private static final float TILE_RICHER = 1.3f;
	/** How much of the lightest tone its white letters allow each kind of tile takes: tiles glow as far as their letters still read. */
	static final float DARK_TILE_SHARE = 0.7f;
	static final float PALE_TILE_SHARE = 0.95f;
	/** The darkest a tile gets, where it must be darker than a dim background. */
	private static final float TILE_LEAST_LUMINANCE = 0.003f;
	/** A dimmed tile keeps at least this contrast to what lies behind it, so it is still seen as a tile. */
	private static final float DIM_TILE_CONTRAST = 1.5f;
	/** The shadow: its color, the most it may be, and how strong it is at least where the background is busy. */
	static final int SHADOW_COLOR = 0xFF0A0610;
	static final float SHADOW_MOST = 0.8f;
	private static final float BUSY_FROM = 0.15f;
	private static final float BUSY_SHADOW = 0.35f;
	/** A color with less saturation than this has no hue of its own (white, grey): it is given this one, a cool blue. */
	private static final float NO_HUE_BELOW = 0.02f;
	private static final float NEUTRAL_HUE = 220f;
	/** The word being sung on a dark background, and the letters on a tile on a bright one. */
	static final int WHITE = 0xFFFFFFFF;

	/** Each channel's value in linear light, for the 256 values of a channel. */
	private static final float[] LINEAR = new float[256];

	static {
		for (int i = 0; i < LINEAR.length; i++) {
			float c = i / 255f;
			LINEAR[i] = c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
		}
	}

	private LyricTones() {
	}

	/** @return the relative luminance of a color, from 0 to 1, as WCAG counts it */
	static float luminance(int rgb) {
		return 0.2126f * LINEAR[rgb >> 16 & 0xFF] + 0.7152f * LINEAR[rgb >> 8 & 0xFF] + 0.0722f * LINEAR[rgb & 0xFF];
	}

	/** @return the contrast of two relative luminances, from 1 to 21 */
	static float contrast(float a, float b) {
		return (Math.max(a, b) + 0.05f) / (Math.min(a, b) + 0.05f);
	}

	/** @return the luminance that has exactly this contrast to another, lighter than it (or darker), unbounded */
	private static float lighterBy(float other, float contrast) {
		return contrast * (other + 0.05f) - 0.05f;
	}

	private static float darkerBy(float other, float contrast) {
		return (other + 0.05f) / contrast - 0.05f;
	}

	/** @return how dark what lies behind a word is once its shadow is under it, at this strength */
	static float shaded(float behind, float shadow) {
		return behind + (luminance(SHADOW_COLOR) - behind) * shadow;
	}

	/** @return the saturation of a color (HSV), from 0 to 1 */
	static float saturation(int rgb) {
		int most = Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF));
		int least = Math.min(rgb >> 16 & 0xFF, Math.min(rgb >> 8 & 0xFF, rgb & 0xFF));
		return most == 0 ? 0f : (most - least) / (float) most;
	}

	/** @return the hue of a color in degrees, or {@link #NEUTRAL_HUE} for one with no hue of its own */
	static float hue(int rgb) {
		float r = (rgb >> 16 & 0xFF) / 255f;
		float g = (rgb >> 8 & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float most = Math.max(r, Math.max(g, b));
		float span = most - Math.min(r, Math.min(g, b));
		if (most <= 0f || span / most < NO_HUE_BELOW) {
			return NEUTRAL_HUE;
		}
		float hue;
		if (most == r) {
			hue = (g - b) / span;
		} else if (most == g) {
			hue = 2f + (b - r) / span;
		} else {
			hue = 4f + (r - g) / span;
		}
		hue *= 60f;
		return hue < 0f ? hue + 360f : hue;
	}

	/** @return an opaque color of this hue (degrees), saturation and value (HSV, 0 to 1) */
	static int hsv(float hue, float saturation, float value) {
		float h = ((hue % 360f) + 360f) % 360f / 60f;
		float c = value * saturation;
		float x = c * (1f - Math.abs(h % 2f - 1f));
		float m = value - c;
		float r;
		float g;
		float b;
		if (h < 1f) {
			r = c; g = x; b = 0f;
		} else if (h < 2f) {
			r = x; g = c; b = 0f;
		} else if (h < 3f) {
			r = 0f; g = c; b = x;
		} else if (h < 4f) {
			r = 0f; g = x; b = c;
		} else if (h < 5f) {
			r = x; g = 0f; b = c;
		} else {
			r = c; g = 0f; b = x;
		}
		return 0xFF000000 | channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m);
	}

	private static int channel(float value) {
		return Math.clamp(Math.round(value * 255f), 0, 255);
	}

	/** @return a channel value in linear light, encoded again */
	private static int encode(float linear) {
		float c = Math.clamp(linear, 0f, 1f);
		float s = c <= 0.0031308f ? c * 12.92f : 1.055f * (float) Math.pow(c, 1.0 / 2.4) - 0.055f;
		return channel(s);
	}

	/**
	 * @return the color at the target luminance with its hue kept: darker by scaling it in linear light (which keeps its saturation); lighter by
	 *         scaling it up to its full value, and from there by mixing it toward white
	 */
	static int fit(int rgb, float target) {
		float r = LINEAR[rgb >> 16 & 0xFF];
		float g = LINEAR[rgb >> 8 & 0xFF];
		float b = LINEAR[rgb & 0xFF];
		float own = 0.2126f * r + 0.7152f * g + 0.0722f * b;
		float wanted = Math.clamp(target, 0f, 1f);
		float most = Math.max(r, Math.max(g, b));
		if (own <= 0f || most <= 0f) {
			// black has no hue to keep: a grey of that luminance
			int grey = encode(wanted);
			return 0xFF000000 | grey << 16 | grey << 8 | grey;
		}
		float full = own / most;
		if (wanted <= full) {
			float k = wanted / own;
			return 0xFF000000 | encode(r * k) << 16 | encode(g * k) << 8 | encode(b * k);
		}
		// as bright as the color goes: then toward white
		r /= most;
		g /= most;
		b /= most;
		float t = (wanted - full) / (1f - full);
		return 0xFF000000 | encode(r + (1f - r) * t) << 16 | encode(g + (1f - g) * t) << 8 | encode(b + (1f - b) * t);
	}

	/** @return the color in the same hue at full value, at least this saturated and its own saturation raised by this much */
	private static int vivid(int rgb, float least, float richer) {
		return hsv(hue(rgb), Math.clamp(saturation(rgb) * richer, least, 1f), 1f);
	}

	/** @return the contrast text needs that is this tall on the screen (its em, in pixels) */
	static float contrastFor(float pixels) {
		float large = Math.clamp((pixels - SMALL_BELOW_PIXELS) / (LARGE_FROM_PIXELS - SMALL_BELOW_PIXELS), 0f, 1f);
		return SMALL_CONTRAST + (LARGE_CONTRAST - SMALL_CONTRAST) * large;
	}

	/** @return the hue a color of this hue takes at this luminance: yellows turn toward orange as they get dark, every other hue stays */
	static float darkHue(float hue, float luminance) {
		float yellow = Math.min(Math.clamp((hue - YELLOW_FROM) / (YELLOW_FULL_FROM - YELLOW_FROM), 0f, 1f),
			Math.clamp((YELLOW_TO - hue) / (YELLOW_TO - YELLOW_FULL_TO), 0f, 1f));
		float dark = Math.clamp((TURN_FROM_LUMINANCE - luminance) / (TURN_FROM_LUMINANCE - TURN_FULL_LUMINANCE), 0f, 1f);
		return hue - YELLOW_TURN * yellow * dark;
	}

	/** @return the color at this luminance, at least this saturated and its saturation raised by this much, in the hue it has that dark */
	private static int rich(int rgb, float least, float richer, float target) {
		int full = hsv(darkHue(hue(rgb), target), Math.clamp(saturation(rgb) * richer, least, 1f), 1f);
		return fit(full, target);
	}

	/** @return the light tone of a color for words standing free on a dark background: a clean tint, saturated and bright, never white, never grey */
	static int tint(int rgb) {
		int tint = hsv(hue(rgb), Math.clamp(saturation(rgb) * TINT_SHARE, TINT_LEAST_SATURATION, TINT_MOST_SATURATION), 1f);
		// blues are dark even at full value: they are lifted as far as a light word needs
		return luminance(tint) < TINT_LEAST_LUMINANCE ? fit(tint, TINT_LEAST_LUMINANCE) : tint;
	}

	/** @return the tint, made as much lighter as it must be to reach this contrast before this background with the strongest shadow under it */
	static int tint(int rgb, float behind, float contrast) {
		int tint = tint(rgb);
		float least = lighterBy(shaded(behind, SHADOW_MOST), contrast * MARGIN);
		return luminance(tint) < least ? fit(tint, least) : tint;
	}

	/**
	 * @return the deep tone of a color for words standing free before this background: saturated, as light as this contrast allows; it may come out
	 *         darker than {@link #DEEP_LEAST_VALUE} (see {@link #deepReads})
	 */
	static int deep(int rgb, float behind, float contrast) {
		float target = Math.clamp(Math.min(darkerBy(behind, contrast * MARGIN), DEEP_MOST_LUMINANCE), 0f, 1f);
		int full = vivid(rgb, DEEP_LEAST_SATURATION, DEEP_RICHER);
		return rich(rgb, DEEP_LEAST_SATURATION, DEEP_RICHER, Math.min(target, luminance(full)));
	}

	/** @return whether a deep tone still has color: no darker than {@link #DEEP_LEAST_VALUE} */
	static boolean deepReads(int deep) {
		int most = Math.max(deep >> 16 & 0xFF, Math.max(deep >> 8 & 0xFF, deep & 0xFF));
		return most / 255f >= DEEP_LEAST_VALUE;
	}

	/** @return whether the words before this background are light ones (a tint, maybe with a shadow); else they are deep ones */
	static boolean light(int rgb, float behind, float contrast) {
		int deep = deep(rgb, behind, contrast);
		return !(deepReads(deep) && contrast(luminance(deep), behind) >= contrast);
	}

	/** The same for large text. */
	static boolean light(int rgb, float behind) {
		return light(rgb, behind, LARGE_CONTRAST);
	}

	/**
	 * @param lightest the least luminance among the light words
	 * @param behind   what lies behind them
	 * @param busy     how much that varies (its standard deviation)
	 * @param contrast the contrast they need
	 * @return how strong the shadow under light words must be for them to read: 0 where they do without one
	 */
	static float shadow(float lightest, float behind, float busy, float contrast) {
		float wanted = darkerBy(lightest, contrast * MARGIN);
		float needed = behind <= wanted ? 0f : Math.min(SHADOW_MOST, (behind - wanted) / Math.max(1e-4f, behind - luminance(SHADOW_COLOR)));
		return busy > BUSY_FROM ? Math.max(needed, BUSY_SHADOW) : needed;
	}

	/**
	 * @return a tone dimmed toward what lies behind it, keeping {@link #DIM_KEEPS} of the difference in luminance, but never to less than
	 *         {@code least} against it; where that leaves it no visible room, it recedes by its saturation instead. It never turns grey
	 */
	static int dim(int tone, float behind, float least) {
		float own = luminance(tone);
		float wanted = behind + (own - behind) * DIM_KEEPS;
		wanted = own > behind ? Math.max(wanted, lighterBy(behind, least * MARGIN)) : Math.min(wanted, darkerBy(behind, least * MARGIN));
		float hue = hue(tone);
		float saturation = saturation(tone);
		if (contrast(own, behind) < least * MARGIN || Math.abs(wanted - own) < DIM_SEEN * (own + 0.05f)) {
			return withAlpha(fit(hsv(hue, saturation * QUIET_SATURATION, 1f), own), tone);
		}
		if (wanted < own) {
			return withAlpha(fit(hsv(darkHue(hue, wanted), saturation + (1f - saturation) * DIM_RICHER, 1f), wanted), tone);
		}
		return withAlpha(fit(hsv(hue, saturation, 1f), wanted), tone);
	}

	/**
	 * @param share    how much of the lightest tone its white letters allow the tile takes ({@link #DARK_TILE_SHARE}, {@link #PALE_TILE_SHARE})
	 * @param contrast the contrast its letters need
	 * @return the tone of a tile of the color before this background, for white letters: as far from what lies behind as {@link #TILE_CONTRAST}
	 *         asks, lighter than a dark background where the letters still read on it, so it glows, else darker
	 */
	static int tile(int rgb, float behind, float share, float contrast) {
		float most = darkerBy(luminance(WHITE), contrast * MARGIN);
		float lighterFrom = lighterBy(behind, TILE_CONTRAST * MARGIN);
		float wanted;
		if (lighterFrom <= most) {
			wanted = Math.max(lighterFrom, most * share);
		} else {
			wanted = Math.max(TILE_LEAST_LUMINANCE, Math.min(darkerBy(behind, TILE_CONTRAST * MARGIN), most) * share);
		}
		return rich(rgb, TILE_LEAST_SATURATION, TILE_RICHER, wanted);
	}

	/** @return a tile dimmed toward what lies behind it like a word, still a tile against it, and its white letters still read on it */
	static int dimTile(int tile, float behind, int letters) {
		float own = luminance(tile);
		float wanted = behind + (own - behind) * DIM_KEEPS;
		float most = darkerBy(luminance(letters), SUNG_CONTRAST * MARGIN);
		float apart = own > behind ? lighterBy(behind, DIM_TILE_CONTRAST * MARGIN) : darkerBy(behind, DIM_TILE_CONTRAST * MARGIN);
		wanted = own > behind ? Math.max(wanted, apart) : Math.min(wanted, apart);
		return withAlpha(fit(tile, Math.clamp(wanted, 0f, Math.max(0f, most))), tile);
	}

	/** @return the color with the alpha of another */
	private static int withAlpha(int rgb, int alphaOf) {
		return alphaOf & 0xFF000000 | rgb & 0xFFFFFF;
	}

	/** @return a tone made as light or as dark as it must be to have at least this contrast to what lies behind it, its hue kept */
	static int atLeast(int tone, float behind, float least, boolean lighter) {
		float own = luminance(tone);
		if (contrast(own, behind) >= least * MARGIN && own > behind == lighter) {
			return tone;
		}
		return withAlpha(fit(tone, lighter ? lighterBy(behind, least * MARGIN) : darkerBy(behind, least * MARGIN)), tone);
	}

	/** @return the color between two, each channel and the alpha mixed */
	static int mix(int from, int to, float amount) {
		int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * amount);
		int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
		int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** The tones of a large Classic line before an even background (see the method below). */
	static void classic(int text, int color, float tinted, float behind, float busy, int[] out, int[] dimmed) {
		classic(text, color, tinted, behind, behind, busy, LARGE_CONTRAST, out, dimmed);
	}

	/** What the tones of the Classic preset are for: the sung part, the part still waiting, the classic glow's tinted line, its glow, the shadow. */
	enum Classic {
		SUNG, REST, TINTED, GLOW, SHADOW
	}

	/**
	 * The tones of a line of the Classic preset before what lies behind it. On a dark background the sung part has the text color chosen (made
	 * lighter only where it would not read) and the glow is the glow color; on a bright one the text is the deep, saturated glow color and no light
	 * glow washes it out. The part still waiting is dimmed toward what lies behind, never below {@link #SUNG_CONTRAST}.
	 *
	 * @param text   the text color chosen
	 * @param color  the glow color, the line's color
	 * @param tinted how far the classic glow's waiting line is mixed from the glow color toward the text color
	 * @param darkest how light the darkest of what lies behind the line is, and {@code brightest} the brightest: it must read against all of it
	 * @param contrast the contrast the line needs at its size on the screen ({@link #contrastFor})
	 * @param out    receives the tones; the shadow's strength is its alpha, the glow's how much it shows
	 * @param dimmed receives them dimmed for a line already sung
	 */
	static void classic(int text, int color, float tinted, float darkest, float brightest, float busy, float contrast, int[] out, int[] dimmed) {
		int opaque = 0xFF000000 | color;
		// deep only where it reads against the darkest part; light text then answers to the brightest part
		boolean light = light(opaque, darkest, contrast);
		float behind = light ? brightest : darkest;
		int sung;
		float shadow = 0f;
		if (light) {
			sung = 0xFF000000 | text;
			// a dark text color chosen is made light enough to read, with the most shadow
			if (contrast(luminance(sung), shaded(behind, SHADOW_MOST)) < contrast * MARGIN || luminance(sung) < behind) {
				sung = tint(opaque, behind, contrast);
			}
			shadow = shadow(luminance(sung), behind, busy, contrast);
		} else {
			sung = deep(opaque, behind, contrast);
		}
		float shaded = shaded(behind, shadow);
		int rest = dim(sung, shaded, SUNG_CONTRAST);
		out[Classic.SUNG.ordinal()] = sung;
		out[Classic.REST.ordinal()] = rest;
		out[Classic.TINTED.ordinal()] = light ? atLeast(mix(opaque, sung, tinted), shaded, SUNG_CONTRAST, true) : rest;
		out[Classic.GLOW.ordinal()] = light ? opaque : opaque & 0xFFFFFF;
		out[Classic.SHADOW.ordinal()] = withAlpha(SHADOW_COLOR, Math.clamp(Math.round(shadow * 255f), 0, 255) << 24);
		dimmed[Classic.SUNG.ordinal()] = dim(sung, shaded, SUNG_CONTRAST);
		dimmed[Classic.REST.ordinal()] = dim(rest, shaded, SUNG_CONTRAST);
		dimmed[Classic.TINTED.ordinal()] = dim(out[Classic.TINTED.ordinal()], shaded, SUNG_CONTRAST);
		dimmed[Classic.GLOW.ordinal()] = withAlpha(out[Classic.GLOW.ordinal()], Math.round((out[Classic.GLOW.ordinal()] >>> 24) * DIM_KEEPS) << 24);
		dimmed[Classic.SHADOW.ordinal()] = out[Classic.SHADOW.ordinal()];
	}
}
