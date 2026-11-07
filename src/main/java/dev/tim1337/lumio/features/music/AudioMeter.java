package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.util.Logs;

import java.util.List;
import java.util.Locale;

/**
 * Music loudness and beats, for effects that react to the music (the sky). On Windows the music player's level is read from the system mixer (the
 * same level the volume mixer meters show) about 30 times per second by {@link WindowsAudioMeter}, directly from Java, without writing or
 * starting anything. The level is normalized to the song's loudness, so quiet and loud players behave the same, and a beat is a level that jumps
 * above its recent value.
 * <p>
 * Where that is unavailable (another OS, or no player known to the mixer), beats are derived from the lyric lines, if any.
 * <p>
 * The meter only runs while something requests the level, and stops a while after the requests end.
 */
public final class AudioMeter {
	private static final String PLAYERS = "AppleMusic|Apple Music|iTunes|Cider|Spotify";

	private static Thread thread;
	private static volatile float peak = -1f;
	private static volatile long peakAt;
	private static volatile long lastRequest;
	private static long startFailedAt;

	// per-frame derived state
	private static long lastFrame;
	private static float ceiling = 0.2f;
	private static float average;
	private static float level;
	private static float flash;
	private static int beats;
	private static long lastBeat;
	private static int lastLine = -2;
	private static boolean live;

	private AudioMeter() {
	}

	/** @return the current loudness from 0 to 1, relative to the song's overall loudness */
	public static float level() {
		return level;
	}

	/** @return the beat flash: 1 or more at the moment of a beat, decaying quickly */
	public static float flash() {
		return flash;
	}

	/** @return a counter incremented on every beat, so beat effects can vary each time */
	public static int beats() {
		return beats;
	}

	/** @return whether the level comes from the mixer rather than from the lyrics */
	public static boolean fromMixer() {
		return live;
	}

	/** Called every frame by consumers of the level: starts the meter if needed and computes the level for the frame. */
	public static void update() {
		long now = System.nanoTime();
		if (now - lastFrame < 2_000_000L) {
			return;
		}
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		lastRequest = now;
		ensureRunning(now);

		boolean playing = MusicService.isPlaying();
		float raw = peak;
		live = raw >= 0f && (System.currentTimeMillis() - peakAt) < 1500;
		if (!playing) {
			raw = 0f;
		}
		if (live && playing) {
		// normalize to the song's loudness: the observed peak decays slowly, so a song that gets quieter is still tracked
			ceiling = Math.max(0.12f, Math.max(raw, ceiling - dt * 0.03f));
			float n = Math.min(1.2f, raw / ceiling);
			average += (n - average) * (1f - (float) Math.exp(-dt / 0.7f));
			level = Math.max(n, level - dt * 1.4f);
			boolean enough = n > 0.3f && n > average * 1.28f + 0.07f;
			if (enough && now - lastBeat > 190_000_000L) {
				hit(now, 0.55f + (n - average) * 1.6f);
			}
		} else {
			level = Math.max(0f, level - dt * 1.4f);
			average = Math.max(0f, average - dt);
			if (playing) {
				// no level available: a beat on every lyric line
				List<MusicService.LyricLine> lines = MusicService.lyrics();
				int index = -1;
				long position = MusicService.position();
				for (int i = 0; i < lines.size() && lines.get(i).timeMs() <= position; i++) {
					index = i;
				}
				if (index != lastLine) {
					if (lastLine != -2 && index >= 0 && !lines.get(index).text().isBlank()) {
						hit(now, 1f);
						level = 0.8f;
					}
					lastLine = index;
				}
			} else {
				lastLine = -2;
			}
		}
		flash = Math.max(0f, flash * (float) Math.exp(-dt * 5.5f));
	}

	private static void hit(long now, float strength) {
		lastBeat = now;
		beats++;
		flash = Math.max(flash, Math.min(1.4f, Math.max(0.5f, strength)));
	}

	// ----------------------------------------------------------------------------------------------------- meter thread

	private static synchronized void ensureRunning(long now) {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			return;
		}
		if (thread != null && thread.isAlive()) {
			return;
		}
		// do not retry over and over if it cannot be started
		if (startFailedAt != 0 && now - startFailedAt < 30_000_000_000L) {
			return;
		}
		peak = -1f;
		thread = new Thread(AudioMeter::run, "Lumio meter");
		thread.setDaemon(true);
		thread.start();
	}

	/** Meter thread: samples the level 30 times per second, rescans for the player periodically, and exits when nothing requests the level anymore. */
	private static void run() {
		WindowsAudioMeter meter;
		try {
			meter = new WindowsAudioMeter(PLAYERS);
		} catch (Throwable error) {
			startFailedAt = System.nanoTime();
			peak = -1f;
			Logs.warn("The meter of the music could not be started", error);
			return;
		}
		try {
			meter.open();
			meter.refresh();
			int count = 0;
			while (System.nanoTime() - lastRequest < 20_000_000_000L) {
				// the output device can change and players can start, so rescan both periodically
				if (count % 150 == 149) {
					meter.open();
				}
				if (count % 30 == 29) {
					meter.refresh();
				}
				count++;
				peak = meter.peak();
				peakAt = System.currentTimeMillis();
				Thread.sleep(33);
			}
		} catch (InterruptedException ignored) {
			// the game is shutting down
		} catch (Throwable error) {
			startFailedAt = System.nanoTime();
			Logs.warn("The meter of the music stopped", error);
		} finally {
			peak = -1f;
			try {
				meter.close();
			} catch (Throwable ignored) {
				// nothing more to do
			}
		}
	}
}
