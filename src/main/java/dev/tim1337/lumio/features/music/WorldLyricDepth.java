package dev.tim1337.lumio.features.music;

/**
 * How near the camera a line of the lyrics in the world is drawn. The lyrics are an overlay on all of the world, except what stands right before the
 * camera: a wall in front of the player still covers them. So their geometry is moved toward the camera to {@link #COVER_BLOCKS}, which keeps its place
 * and size on the screen, and is tested against the world's depth there. A block placed before a wall that stands nearer than that is drawn nearer
 * still, so it may lie over that wall. Plain logic, tested without the game.
 */
final class WorldLyricDepth {
	/** Only what stands nearer the camera than this covers the lyrics, in blocks. */
	static final double COVER_BLOCKS = 4.0;

	private WorldLyricDepth() {
	}

	/**
	 * @param distance the line's own distance, in blocks
	 * @param cover    what stands nearer the camera than this covers the line, in blocks
	 * @return the share of its distance the line is drawn at: 1 for a line standing that near already
	 */
	static float share(double distance, double cover) {
		return distance <= cover ? 1f : (float) (cover / distance);
	}
}
