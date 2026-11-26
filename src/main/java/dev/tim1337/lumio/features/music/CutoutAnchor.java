package dev.tim1337.lumio.features.music;

/**
 * The side of the view a song's lyric blocks keep to: one side per song. The first block placed sets it; every block after it in the same song
 * prefers a spot on that side ({@link LyricSpots}), and only moves to the other side when it must, which then is the song's side. Plain logic,
 * tested without the game.
 */
final class CutoutAnchor {
	private int track = Integer.MIN_VALUE;
	private int side;

	/** Starts a new song's anchor when the song changed. */
	void song(int trackNumber) {
		if (trackNumber != track) {
			track = trackNumber;
			side = 0;
		}
	}

	/** @return the song's side: 1 right, -1 left, 0 before its first block */
	int side() {
		return side;
	}

	/**
	 * A block was placed: its side is the song's from now on.
	 *
	 * @param across where its middle stands across the view from the crosshair, -1 to 1
	 */
	void placed(double across) {
		side = across >= 0.0 ? 1 : -1;
	}
}
