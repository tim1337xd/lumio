package dev.tim1337.lumio.features.music;

import java.util.List;

/**
 * Ad-libs (background vocals) in the info bar, which has one line: what shows there at a moment, by the player's choice. Never two lyric
 * texts in the same place: an ad-lib is left out, stands after its line in room kept for it, or takes the line's place while only it is
 * sung. A main line that is being sung always keeps its place and its highlight. Plain data, tested without the game.
 */
final class BarAdlibs {
	/** The player's choice for the info bar. */
	enum Mode {
		HIDE("Hide"), AFTER("After the line"), INSTEAD("Instead of the line");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}
	}

	/** What the bar shows at a moment. */
	enum Shows {
		/** The main line alone. */
		LINE,
		/** The main line, and the ad-lib small after it. */
		LINE_AND_ADLIB,
		/** The ad-lib alone, where the line stands. */
		ADLIB_ALONE
	}

	private BarAdlibs() {
	}

	/**
	 * @param adlibsOn   whether ad-libs are shown at all (the lyrics' own switch)
	 * @param adlibNow   whether an ad-lib is sung now
	 * @param mainActive whether words of the main line are sung now
	 */
	static Shows shows(Mode mode, boolean adlibsOn, boolean adlibNow, boolean mainActive) {
		if (!adlibsOn || !adlibNow || mode == Mode.HIDE) {
			return Shows.LINE;
		}
		if (mode == Mode.AFTER) {
			return Shows.LINE_AND_ADLIB;
		}
		// an ad-lib that overlaps the main line in time waits: the line keeps its place and highlight
		return mainActive ? Shows.LINE : Shows.ADLIB_ALONE;
	}

	/** @return whether a line keeps room after it for its ad-libs when it is laid out, so one appearing never moves it */
	static boolean keepsRoom(Mode mode, boolean adlibsOn) {
		return adlibsOn && mode == Mode.AFTER;
	}

	/**
	 * @return whether words of a line are sung at a time: between its first word's start and its last word's end; a line without word
	 *         timings counts as sung throughout, so it is never replaced
	 */
	static boolean mainActive(long atMs, List<MusicService.Word> words) {
		if (words == null || words.isEmpty()) {
			return true;
		}
		return atMs >= words.get(0).startMs() && atMs < words.get(words.size() - 1).endMs();
	}
}
