package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import java.util.List;

/**
 * The pace of the lyrics in the world, so no line is ever left out: which lines start now (also those the clock passed since the last look),
 * how quickly a line fades in and out for its length, and which standing line gives up its place when there are too many. Plain logic, tested
 * without the game.
 */
final class WorldLyricPace {
	/** How the lines fade. */
	enum Fade {
		SLOW("Slow"), FAST("Fast"), AUTO("Auto");

		private final String label;

		Fade(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}
	}

	/** A line passed without being shown is still started when it began at most this long ago; further back it was a seek. */
	static final long CATCH_UP_MS = 2500L;
	/** The most lines started in one look. */
	static final int MOST_AT_ONCE = 4;
	/** A fade takes at most this share of the line's own length. */
	static final float FADE_SHARE = 0.25f;
	/** A line shorter than this appears almost at once. */
	static final long VERY_SHORT_MS = 600L;
	static final float AT_ONCE_SECONDS = 0.04f;
	static final float FAST_SECONDS = 0.1f;
	/** A line that must give up its place fades out this quickly. */
	static final float EVICTED_SECONDS = 0.15f;
	/** A line stays readable at least this long, also when the next one has started. */
	static final float READABLE_SECONDS = 0.5f;

	private WorldLyricPace() {
	}

	/**
	 * @param spawned the index of the line started last in these lines, or -1
	 * @param current the index of the line sung now
	 * @return the first line to start now, at most {@code current}: the sung one, or an earlier one the clock passed without it being shown
	 */
	static int firstToStart(List<LyricLine> lines, int spawned, int current, long clockMs) {
		int first = current;
		for (int i = current - 1; i > spawned && i >= 0 && current - first < MOST_AT_ONCE - 1; i--) {
			LyricLine line = lines.get(i);
			if (line.adlibOnly()) {
				continue;
			}
			if (!LyricTimes.main(line) || LyricBreaks.is(line) || clockMs - line.timeMs() > CATCH_UP_MS) {
				break;
			}
			first = i;
		}
		return first;
	}

	/**
	 * @param lengthMs the line's own length
	 * @param usual    the fade's usual time, in seconds
	 * @return how long the line's fade takes, in seconds
	 */
	static float fadeSeconds(Fade fade, long lengthMs, float usual) {
		return switch (fade) {
			case SLOW -> usual;
			case FAST -> Math.min(usual, FAST_SECONDS);
			case AUTO -> lengthMs < VERY_SHORT_MS ? AT_ONCE_SECONDS : Math.min(usual, FADE_SHARE * lengthMs / 1000f);
		};
	}

	/**
	 * @param starts  when each standing older line started, by the lyrics' clock
	 * @param ages    how long each has stood, in seconds
	 * @param staying whether each still stays (an older line not yet on its way out)
	 * @param most    the most older lines that may stay
	 * @return the line that gives up its place now: the oldest, once it was readable for its least time; -1 when none must go yet
	 */
	static int toEvict(long[] starts, float[] ages, boolean[] staying, int most) {
		int count = 0;
		int oldest = -1;
		for (int i = 0; i < starts.length; i++) {
			if (!staying[i]) {
				continue;
			}
			count++;
			if (oldest < 0 || starts[i] < starts[oldest]) {
				oldest = i;
			}
		}
		return count > most && oldest >= 0 && ages[oldest] >= READABLE_SECONDS ? oldest : -1;
	}
}
