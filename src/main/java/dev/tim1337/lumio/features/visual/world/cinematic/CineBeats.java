package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.features.music.AudioMeter;
import dev.tim1337.lumio.features.music.MusicService;

import java.util.Arrays;

/**
 * The beat of the song for cuts on the beat. The meter only notices a beat when it happens, so the beats it heard (in song time) are kept and a
 * steady grid is derived from them: the time between beats, and where the last one was. With it the next beats can be foreseen, so a cut planned
 * at a line start can land on a beat just before it. Only beats measured from the music count; without a steady beat there is no grid.
 */
final class CineBeats {
	private static final int KEPT = 16;
	/** Beats this far apart count, in milliseconds: 60 to 240 beats per minute. */
	private static final long SHORTEST_MS = 250L;
	private static final long LONGEST_MS = 1000L;
	/** A grid needs this many beats, and this share of the gaps close to the usual gap. */
	private static final int LEAST_BEATS = 6;
	private static final float STEADY_SHARE = 0.6f;
	private static final float CLOSE_SHARE = 0.12f;

	private final long[] times = new long[KEPT];
	private final long[] gaps = new long[KEPT];
	private int count;
	private int heard = -1;
	private int track = -1;
	private long period;
	private long last;
	private boolean steady;
	/** Beats since the last cut, for songs without lyrics. */
	int sinceCut;

	/** Takes the beat the meter heard this frame, if any. */
	void update() {
		AudioMeter.update();
		if (MusicService.trackNumber() != track) {
			track = MusicService.trackNumber();
			count = 0;
			steady = false;
		}
		int beats = AudioMeter.beats();
		if (beats == heard) {
			return;
		}
		heard = beats;
		if (!AudioMeter.fromMixer() || !MusicService.isPlaying()) {
			return;
		}
		sinceCut++;
		long at = MusicService.position();
		System.arraycopy(times, 0, times, 1, KEPT - 1);
		times[0] = at;
		count = Math.min(KEPT, count + 1);
		measure();
	}

	/** Works out the usual gap between beats and whether the beat is steady enough to foresee. */
	private void measure() {
		int n = 0;
		for (int i = 0; i + 1 < count; i++) {
			long gap = times[i] - times[i + 1];
			if (gap >= SHORTEST_MS && gap <= LONGEST_MS) {
				gaps[n++] = gap;
			}
		}
		if (n + 1 < LEAST_BEATS) {
			steady = false;
			return;
		}
		Arrays.sort(gaps, 0, n);
		long usual = gaps[n / 2];
		int close = 0;
		for (int i = 0; i < n; i++) {
			if (Math.abs(gaps[i] - usual) <= usual * CLOSE_SHARE) {
				close++;
			}
		}
		steady = close >= n * STEADY_SHARE;
		period = usual;
		last = times[0];
	}

	/** @return whether a steady beat is known */
	boolean steady() {
		return steady;
	}

	/** @return the foreseen beat closest to a song time and no further than the window from it, or -1 if there is none */
	long beatNear(long songMs, long window) {
		if (!steady || period <= 0) {
			return -1L;
		}
		long steps = Math.round((songMs - last) / (double) period);
		long beat = last + steps * period;
		return Math.abs(beat - songMs) <= window ? beat : -1L;
	}
}
