package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.features.music.MusicService.Background;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * The shared timeline of the lyric views (the lyrics in the world and in the cinematic camera): which line is sung and where every line is on
 * its way through, how much of the sung line is sung, how visible the lyrics are, and the adlibs with their own fade and fill.
 * <p>
 * Lines are placed by their rank: 0 is the sung line, 1 the next, -1 the one before. On a line change the ranks move by one over
 * {@link #LINE_SECONDS} with an ease-out, so the old line leaves while the next arrives and there is never an empty moment. On a song change or a
 * pause the lyrics fade out, and on play they fade in at the line being sung. Everything moves on real elapsed time with a clamped step, and
 * nothing is allocated per frame.
 */
final class LyricMotion {
	static final float LINE_SECONDS = 0.35f;
	static final float LIT_SECONDS = 0.2f;
	static final float FADE_SECONDS = 0.3f;
	/** A jump of more lines than this (a seek) is not scrolled through, the lyrics fade in at the new line instead. */
	private static final int LONGEST_SCROLL = 2;
	/** The sung part follows the song quickly but smoothly, and never moves back within a line. */
	private static final float SUNG_RATE = 18f;
	private static final long SAME_FRAME_NANOS = 1_500_000L;
	/** After this long without a view asking for the lyrics, they start afresh. */
	private static final long IDLE_NANOS = 1_000_000_000L;
	/** Adlibs on screen at once: a few quick ones in a row each keep a place of their own. */
	static final int MAX_ADLIBS = 4;

	/**
	 * An adlib on screen: its part, the singer of its line, the line it belongs to (its index, by the lyrics, not the newest line) and its place among
	 * that line's adlibs, how visible it is, how much is sung, and whether it stands on its own in a gap.
	 */
	static final class Adlib {
		Background part;
		int singer;
		int line = -1;
		int order;
		float alpha;
		float sung;
		boolean gap;
		/** When it first showed, by the lyrics' clock: it stays at least a moment, however short its timing (see {@link AdlibTimes}). */
		long firstShown = AdlibTimes.NOT_SHOWN;
		/** When it ends, by its own timing (see {@link AdlibTimes#ownEnd}). */
		long endMs;
		/** Whether it goes before its time, a little faster, as there is really no room left beside it for what comes now. */
		boolean forcedOut;
		private boolean active;
	}

	private final MusicFeature music;
	private final AnimClock clock = new AnimClock();
	private long lastFrame;

	/** The lines shown: kept while they fade out after the song changed. */
	List<LyricLine> lines = List.of();
	/** Index of the sung line in {@link #lines}, or -1; it stays while the lyrics fade out. */
	int current = -1;
	/** The sung line's position as a fractional line index, moving to {@link #current} on a line change. */
	float scroll;
	/** How visible the lyrics are, from 0 to 1. */
	float visible;
	/** Sung characters of the sung line, fractional, only moving forward within the line. */
	float sung;
	/** How lit the sung line is as it starts, from 0 to 1. */
	float lit;
	final Adlib[] adlibs = new Adlib[MAX_ADLIBS];

	private float scrollFrom;
	private float scrollTo;
	private float scrollProgress = 1f;
	private int track = -1;
	/** Whether the next line starts in place instead of scrolling: after a fade out, a seek, or a song change. */
	private boolean jump = true;
	private LyricLine indexedLine;
	private int indexedAt = -1;
	private List<LyricLine> indexedIn;

	LyricMotion(MusicFeature music) {
		this.music = music;
		for (int i = 0; i < adlibs.length; i++) {
			adlibs[i] = new Adlib();
		}
	}

	/** @return the line at an index of the shown lines, or null */
	LyricLine line(int index) {
		return index >= 0 && index < lines.size() ? lines.get(index) : null;
	}

	/** @return how far a line is from the sung one, fractional while the lines move */
	float rank(int index) {
		return index - scroll;
	}

	/** @return whether any lyrics are on screen now: a line or an adlib */
	boolean anything() {
		if (visible > 0.001f && current >= 0) {
			return true;
		}
		for (Adlib adlib : adlibs) {
			if (adlib.alpha > 0.001f) {
				return true;
			}
		}
		return false;
	}

	/** Advances the timeline to now; only the first call per frame does the work. */
	void update() {
		long now = System.nanoTime();
		if (now - lastFrame < SAME_FRAME_NANOS && !cutJustNow) {
			return;
		}
		cutJustNow = false;
		// no view showed the lyrics for a while: they start afresh at the line sung now, never from what was left from before
		boolean idle = now - lastFrame > IDLE_NANOS;
		lastFrame = now;
		music.update(Minecraft.getInstance());
		float dt = clock.tick();
		boolean animated = ModFeatures.MENU.animations();

		List<LyricLine> all = MusicService.lyrics();
		int trackNow = MusicService.trackNumber();
		boolean wanted = music.lyricsPlaying();
		if (idle) {
			lines = all;
			track = trackNow;
			current = -1;
			visible = 0f;
			jump = true;
		}
		if (all != lines || trackNow != track) {
			// another song or other lyrics: the old ones fade out first, then the new ones come in at the sung line
			if (visible <= 0.001f || !animated || lines.isEmpty() || current < 0) {
				lines = all;
				track = trackNow;
				current = -1;
				jump = true;
			} else {
				wanted = false;
			}
		}
		int index = wanted ? LyricTimes.steadyIndex(lines, music.lyricClockMs(), indexOf(music.lyricLine), current) : -1;
		if (index >= 0 && index != current) {
			if (jump || current < 0 || Math.abs(index - current) > LONGEST_SCROLL || !animated) {
				scroll = index;
				scrollProgress = 1f;
			} else {
				scrollFrom = scroll;
				scrollTo = index;
				scrollProgress = 0f;
			}
			current = index;
			sung = 0f;
			lit = 0f;
			jump = false;
		}
		if (scrollProgress < 1f) {
			scrollProgress = Math.min(1f, scrollProgress + dt / LINE_SECONDS);
			float u = 1f - scrollProgress;
			scroll = scrollFrom + (scrollTo - scrollFrom) * (1f - u * u * u);
		}
		float target = wanted && current >= 0 ? 1f : 0f;
		visible = animated ? approach(visible, target, dt / FADE_SECONDS) : target;
		if (visible <= 0f && target <= 0f) {
			// faded out: the lyrics start in place when they come back
			jump = true;
		}
		LyricLine line = line(current);
		if (line != null && wanted) {
			float wantedSung = MusicFeature.sungChars(line, music.lyricAt);
			sung = animated ? Math.max(sung, sung + (wantedSung - sung) * (1f - (float) Math.exp(-dt * SUNG_RATE))) : Math.max(sung, wantedSung);
			if (wantedSung - sung < 0.01f) {
				sung = Math.max(sung, wantedSung);
			}
		}
		lit = animated ? Math.min(1f, lit + dt / LIT_SECONDS) : 1f;
		updateAdlibs(all, dt, animated);
	}

	/** The next line starts in place instead of moving up into it: the cinematic camera cut to a new shot, which starts with a fresh line. */
	void freshStart() {
		jump = true;
		// the frame of a cut asks again, whatever was asked just before: the new shot starts with the line sung now
		cutJustNow = true;
	}

	private boolean cutJustNow;

	/** @return the index of the sung line among the shown lines, looked up only when the line changes */
	private int indexOf(LyricLine line) {
		if (line != indexedLine || lines != indexedIn) {
			indexedLine = line;
			indexedIn = lines;
			indexedAt = line == null ? -1 : lines.indexOf(line);
		}
		return indexedAt;
	}

	private void updateAdlibs(List<LyricLine> all, float dt, boolean animated) {
		for (Adlib adlib : adlibs) {
			adlib.active = false;
		}
		boolean on = music.adlibsShown() && MusicService.isPlaying() && all == lines;
		long at = music.lyricClockMs();
		if (on && !all.isEmpty()) {
			int at0 = lastLineAt(all, at);
			for (int i = Math.max(0, at0 - 1); i <= Math.min(all.size() - 1, at0 + 1); i++) {
				LyricLine owner = all.get(i);
				List<Background> parts = owner.backgrounds();
				for (int k = 0; k < parts.size(); k++) {
					Background part = parts.get(k);
					if (AdlibTimes.due(part.startMs(), part.endMs(), at)) {
						claim(part, owner.singer(), i, k, at, AdlibTimes.ownEnd(part.startMs(), part.endMs(), nextAdlibStart(all, i, k), part.text().length()));
					}
				}
			}
		}
		// a short adlib (a quick "hey") stays a moment after its own time, so it is seen at all
		for (Adlib adlib : adlibs) {
			if (on && adlib.part != null && !adlib.active) {
				adlib.active = AdlibTimes.shown(adlib.part.startMs(), adlib.endMs, adlib.firstShown, at);
			}
			adlib.active &= !adlib.forcedOut;
		}
		// an adlib stands on its own while no line is shown
		boolean gap = !(visible > 0.5f && current >= 0);
		for (Adlib adlib : adlibs) {
			if (adlib.part == null) {
				continue;
			}
			float rate = adlib.active ? dt / AdlibTimes.IN_SECONDS : dt / (adlib.forcedOut ? AdlibTimes.FORCED_OUT_SECONDS : AdlibTimes.OUT_SECONDS);
			adlib.alpha = animated ? approach(adlib.alpha, adlib.active ? 1f : 0f, rate) : adlib.active ? 1f : 0f;
			if (adlib.active) {
				adlib.gap = gap;
				float wantedSung = MusicFeature.backgroundSungChars(adlib.part, at);
				adlib.sung = animated ? Math.max(adlib.sung, adlib.sung + (wantedSung - adlib.sung) * (1f - (float) Math.exp(-dt * SUNG_RATE)))
					: Math.max(adlib.sung, wantedSung);
			}
			// one that made way keeps its slot until its time is over, so it does not come back
			boolean waits = adlib.forcedOut && on && (AdlibTimes.due(adlib.part.startMs(), adlib.part.endMs(), at) || AdlibTimes.shown(adlib.part.startMs(), adlib.endMs, adlib.firstShown, at));
			if (adlib.alpha <= 0f && !adlib.active && !waits) {
				adlib.part = null;
			}
		}
	}

	/** Marks an adlib as sung now, with the line it belongs to: in its slot, or in a free one. */
	private void claim(Background part, int singer, int line, int order, long at, long endMs) {
		Adlib free = null;
		for (Adlib adlib : adlibs) {
			if (adlib.part == part) {
				adlib.active = true;
				return;
			}
			if (adlib.part == null && free == null) {
				free = adlib;
			}
		}
		if (free != null) {
			free.part = part;
			free.singer = singer;
			free.line = line;
			free.order = order;
			free.alpha = 0f;
			free.sung = 0f;
			free.firstShown = at;
			free.endMs = endMs;
			free.forcedOut = false;
			free.active = true;
		}
	}

	/** @return when the adlib after one starts: the next of its line, or the first of a later line; or {@link AdlibTimes#NO_NEXT} */
	private static long nextAdlibStart(List<LyricLine> all, int line, int part) {
		List<Background> parts = all.get(line).backgrounds();
		if (part + 1 < parts.size()) {
			return parts.get(part + 1).startMs();
		}
		for (int i = line + 1; i < all.size(); i++) {
			if (!all.get(i).backgrounds().isEmpty()) {
				return all.get(i).backgrounds().get(0).startMs();
			}
		}
		return AdlibTimes.NO_NEXT;
	}

	/** @return the index of the last line starting at or before the time, or -1 */
	private static int lastLineAt(List<LyricLine> all, long at) {
		int low = 0;
		int high = all.size() - 1;
		int found = -1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			if (all.get(middle).timeMs() <= at) {
				found = middle;
				low = middle + 1;
			} else {
				high = middle - 1;
			}
		}
		return found;
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}
}
