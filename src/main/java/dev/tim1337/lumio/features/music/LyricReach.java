package dev.tim1337.lumio.features.music;

import net.minecraft.network.chat.Component;

/**
 * How large the main words of the lyrics in the world are on the screen, whatever their distance and the field of view (also the cinematic camera's,
 * shot by shot): the visible height of their capitals, as a share of the screen's height, by the size the player chose ({@link Size}). A line is
 * placed at the target, and while it stands it is drawn larger where it would come out under the least and smaller where it would be over the
 * most. Also how much of the screen a Cut-out block and a Classic line may take. Plain logic, tested without the
 * game.
 */
final class LyricReach {
	/**
	 * The size of the lyrics in the world: Small about 15 % smaller than Normal, Large the size of before (capitals 4.5 % of the screen), and Auto
	 * (the default) chosen per block from the room at its placement, between Small and Normal ({@link #autoScales}).
	 */
	enum Size {
		SMALL("Small", 0.85f),
		NORMAL("Normal", 1f),
		LARGE("Large", 0.045f / 0.035f),
		AUTO("Auto", 1f);

		private final Component displayName;
		final float factor;

		Size(String displayName, float factor) {
			this.displayName = Component.literal(displayName);
			this.factor = factor;
		}

		Component displayName() {
			return displayName;
		}
	}

	/** The capitals of Inter, the lyrics' font, are this share of its em tall. */
	static final float CAP_HEIGHT = 0.727f;
	/** At the Normal size the main words' capitals are this tall, as a share of the screen's height. */
	static final float NORMAL_CAP_SHARE = 0.035f;
	/** The least size is this share of the target: under it a standing line grows. */
	private static final float LEAST_OF_TARGET = 0.89f;
	/** At the Normal size a key word's capitals are at most this tall; a key word is this many times a main word. */
	static final float NORMAL_KEY_CAP_SHARE = 0.05f;
	static final float KEY_OF_MAIN = CutoutWords.KEY_BIG / CutoutWords.REGULAR;
	/** A Cut-out block takes at most this share of the screen's height and of its width. */
	static final float MOST_BLOCK_HEIGHT = 0.4f;
	static final float MOST_BLOCK_WIDTH = 0.55f;
	/**
	 * In open sky the lyrics stand this far away, whatever the render distance (they are drawn over far terrain anyway), so they are part of the sky
	 * and hardly shift as the player walks. The other distances are with the spots ({@link LyricSpots}).
	 */
	static final double SKY_BLOCKS = 112.0;
	/** Small words keep at least this share of the main words' size, and capitals at least this tall at a screen 1080 pixels high. */
	static final float SMALL_LEAST_SHARE = 0.75f;
	static final float SMALL_LEAST_PIXELS_AT_1080 = 16f;
	static final int REFERENCE_HEIGHT = 1080;
	/** A line grows by this much at most: only a line placed for a far narrower view comes near it. */
	static final float MOST_GROWTH = 4f;

	/** The size chosen: set by the lyrics each frame from their setting, Normal until then. */
	private static Size size = Size.NORMAL;

	private LyricReach() {
	}

	static void use(Size chosen) {
		size = chosen;
	}

	static Size size() {
		return size;
	}

	/** @return how tall the main words' capitals are placed at the size chosen, as a share of the screen's height */
	static float targetCapShare() {
		return NORMAL_CAP_SHARE * size.factor;
	}

	/** @return the size under which a standing line grows */
	static float leastCapShare() {
		return targetCapShare() * LEAST_OF_TARGET;
	}

	/** @return the size above which a line is drawn smaller: a key word then stays within its own limit, and nothing else is larger than that */
	static float mostCapShare() {
		return NORMAL_KEY_CAP_SHARE * size.factor / KEY_OF_MAIN;
	}

	/** @return the height of the capitals of an em this large at this distance, as a share of the screen's height */
	static double capShare(double emBlocks, double distance, double halfFovY) {
		return emBlocks * CAP_HEIGHT / distance / (2.0 * Math.tan(Math.toRadians(halfFovY)));
	}

	/**
	 * @param emBlocks how large an em of the main words is, in blocks
	 * @param distance how far the line is from the eye, in blocks
	 * @param halfFovY half the vertical field of view, in degrees
	 * @return how much the line is scaled as it is drawn: 1 where its words are between the least and the most size
	 */
	static float grow(double emBlocks, double distance, double halfFovY) {
		if (emBlocks <= 0 || distance <= 1e-3) {
			return 1f;
		}
		double share = capShare(emBlocks, distance, halfFovY);
		double wanted = share < leastCapShare() ? leastCapShare() / share : share > mostCapShare() ? mostCapShare() / share : 1.0;
		return (float) Math.clamp(wanted, 1.0 / MOST_GROWTH, MOST_GROWTH);
	}

	/**
	 * @param emUnits  an em of the main words, in text units
	 * @param halfFovY half the vertical field of view, in degrees
	 * @return the size a block is placed at, in blocks per text unit and block of distance: what gives its main words the size chosen on the screen
	 */
	static float placed(float emUnits, double halfFovY) {
		return (float) (targetCapShare() * 2.0 * Math.tan(Math.toRadians(halfFovY)) / (emUnits * CAP_HEIGHT));
	}

	/** The size's key, and the hidden setting that says a config was offered Auto (new configs have it; one saved before Auto has not). */
	static final String SIZE_KEY = "world_lyric_size";
	static final String AUTO_OFFERED_KEY = "world_size_auto_offered";

	/**
	 * A config from before Auto with the size Normal, the default then, which most never changed, gets Auto, once; Small and Large stay as
	 * picked, and a Normal picked after this stays too.
	 */
	static void offerAuto(com.google.gson.JsonObject json) {
		if (!json.has(AUTO_OFFERED_KEY) && json.has(SIZE_KEY) && json.get(SIZE_KEY).isJsonPrimitive() && Size.NORMAL.name().equals(json.get(SIZE_KEY).getAsString())) {
			json.addProperty(SIZE_KEY, Size.AUTO.name());
		}
	}

	/** Auto's steps below Normal, and the lines it takes as short (started at Normal) and as long (two steps lower), in words. */
	static final float AUTO_STEP = 0.075f;
	static final int SHORT_WORDS = 3;
	static final int LONG_WORDS = 9;

	/**
	 * @param words how many words the line has
	 * @param full  whether the block is full (all the rows it may have)
	 * @return the sizes Auto tries for a block, largest first, as shares of Normal: never above Normal, never under Small; a short line starts at
	 *         Normal, a long line or a full block two steps lower, any other line one step lower
	 */
	static float[] autoScales(int words, boolean full) {
		int skipped = words <= SHORT_WORDS ? 0 : words >= LONG_WORDS || full ? 2 : 1;
		float least = Size.SMALL.factor;
		int steps = Math.max(0, (int) Math.ceil((1f - least) / AUTO_STEP - 1e-4f));
		int from = Math.min(skipped, steps);
		float[] scales = new float[steps - from + 1];
		for (int i = 0; i < scales.length; i++) {
			scales[i] = Math.max(least, 1f - (from + i) * AUTO_STEP);
		}
		return scales;
	}

	/** @return the least height the capitals of small words have on a screen this high, in pixels */
	static float smallLeastPixels(int screenHeight) {
		return SMALL_LEAST_PIXELS_AT_1080 * screenHeight / REFERENCE_HEIGHT;
	}

	/**
	 * @param perBlock     the size a Cut-out column would be placed at, in blocks per text unit and block of distance
	 * @param emUnits      an em of its main words, in text units
	 * @param columnUnits  the width of its words, in text units
	 * @param halfFovY     half the vertical field of view, in degrees
	 * @param aspect       the screen's width over its height
	 * @return that size, made smaller where the main words' capitals would be taller than the most or the words wider than
	 *         {@link #MOST_BLOCK_WIDTH} of the screen
	 */
	static float limited(float perBlock, float emUnits, float columnUnits, double halfFovY, double aspect) {
		double tan = Math.tan(Math.toRadians(halfFovY));
		double cap = capShare(emUnits * perBlock, 1.0, halfFovY);
		double wide = perBlock * columnUnits / (2.0 * tan * aspect);
		double scale = Math.min(1.0, Math.min(mostCapShare() / cap, MOST_BLOCK_WIDTH / wide));
		return (float) (perBlock * scale);
	}

	/** A line of the Classic preset is at most this share of the screen's width wide: wider, it is broken into rows. */
	static final float MOST_LINE_WIDTH = 0.26f;

	/** @return half the width a Classic line may have, in degrees, at a field of view and a window shape */
	static float mostLineHalfWidth(double halfFovY, double aspect) {
		return (float) Math.toDegrees(Math.atan(MOST_LINE_WIDTH * Math.tan(Math.toRadians(halfFovY)) * aspect));
	}
}
