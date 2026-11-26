package dev.tim1337.lumio.features.visual.world;

/**
 * The numbers of the Visual Filter's fog switches: which fog counts as thick, where the land fades out at the edge of the loaded world, and how the
 * game's weather fog stays. Plain numbers, so it is unit tested.
 */
public final class FogFilter {
	/** With no distance fog the land still fades out over this many blocks before the edge of the loaded world, so it is not cut off. */
	static final float CLEAR_EDGE_BLOCKS = 8f;
	/** The game fades the land out over a tenth of the render distance, within these bounds. */
	private static final float GAME_EDGE_SHARE = 0.1f;
	private static final float GAME_EDGE_LEAST = 4f;
	private static final float GAME_EDGE_MOST = 64f;
	/** Rain brings the end of the game's fog nearer by up to this many blocks. */
	private static final float RAIN_NEARER_BLOCKS = 256f;
	/**
	 * The farthest end of the sky's fog: where the game's sky itself ends. The sky then keeps its color down to a thin, soft line at the horizon.
	 * Farther than that the sky would end in a hard edge over the fog's color below it.
	 */
	public static final float SKY_EDGE_BLOCKS = 512f;

	private FogFilter() {
	}

	/**
	 * @param placeEnd where the place's own fog ends (the dimension's or biome's value, before the weather)
	 * @return whether the fog is the close, dense kind: one that hides land the game has loaded (the Nether, a boss fight's fog)
	 */
	public static boolean thick(float placeEnd, float renderDistance, boolean bossFog) {
		return bossFog || placeEnd < renderDistance;
	}

	/** @return where the land starts to fade out before the edge of the loaded world: the game's place, or a short way before the edge */
	public static float edgeStart(float renderDistance, boolean clear) {
		float game = Math.max(GAME_EDGE_LEAST, Math.min(GAME_EDGE_MOST, renderDistance * GAME_EDGE_SHARE));
		return renderDistance - (clear ? Math.min(game, CLEAR_EDGE_BLOCKS) : game);
	}

	/** @return how much of the game's weather fog is in a fog's end, from 0 (none) to 1 (full rain) */
	public static float weather(float placeEnd, float shownEnd) {
		return Math.max(0f, Math.min(1f, (placeEnd - shownEnd) / RAIN_NEARER_BLOCKS));
	}

	/** @return a fog distance without the place's fog, with the weather's share of the game's value left in, so rain still draws in */
	public static float cleared(float game, float clear, float weather) {
		return clear + (game - clear) * weather;
	}
}
