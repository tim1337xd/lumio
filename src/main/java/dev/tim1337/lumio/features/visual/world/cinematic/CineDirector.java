package dev.tim1337.lumio.features.visual.world.cinematic;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.music.LyricChorus;
import dev.tim1337.lumio.features.music.LyricCutFade;
import dev.tim1337.lumio.features.music.LyricTimes;
import dev.tim1337.lumio.features.music.MusicService.Background;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService;

import java.util.List;
import java.util.Random;

/**
 * Decides when the film changes shot, following the song: a cut only exactly where a lyric line starts, or in a gap without lyrics, never while a
 * line or an adlib is sung. A shot lasts several lines by the pace (choruses at most one line less), at least 12 and at most 40 seconds. A cut can land on a
 * foreseen beat just before its line, if that is still in the gap. Long instrumental parts get a slow move instead of a cut; songs without lyrics
 * cut on the beat or every 8 to 14 seconds; without music, and in the calm mode, one slow shot blends into the next.
 */
final class CineDirector {
	enum Action {
		NONE, CUT, GLIDE
	}

	/** Kinds of changes in the history, for the preview and the test log. */
	static final int KIND_LINE = 0;
	static final int KIND_BEAT = 1;
	static final int KIND_GAP = 2;
	static final int KIND_SONG = 3;
	static final int KIND_TIME = 4;

	static final float SHORTEST_SHOT = 12f;
	static final float LONGEST_SHOT = 40f;
	/** A cut moves to a beat at most this far before its line. */
	private static final long BEAT_WINDOW_MS = 150L;
	/** No line for this long is an instrumental part, which gets a slow move a moment after it starts. */
	private static final long LONG_GAP_MS = 6000L;
	private static final long GAP_MOVE_AFTER_MS = 1000L;
	/** A move in a gap is not started when the next line is this close. */
	private static final long GAP_MOVE_ROOM_MS = 3000L;
	/** The last seconds of a song: no more cuts, the last shot fades out. */
	private static final long SONG_END_MS = 5000L;
	/** Without lyrics and beat: a cut every 12 to 20 seconds; in the calm mode, a blend every 12 to 18. */
	private static final float NO_BEAT_SHORTEST = 12f;
	private static final float NO_BEAT_LONGEST = 20f;
	private static final float CALM_SHORTEST = 12f;
	private static final float CALM_LONGEST = 18f;
	/** A line of this few words is a quiet line, for a close shot. */
	private static final int QUIET_WORDS = 3;
	private static final int HISTORY = 64;

	private final CinematicCameraFeature feature;
	private final CineBeats beats = new CineBeats();
	private final Random random = new Random();
	private int track = -1;
	private int lastIndex = -1;
	private int linesInShot;
	private int targetLines = 2;
	/** The line whose start was already cut on a beat just before it. */
	private int cutBefore = -1;
	private boolean gapMoved;
	private float timeLimit = NO_BEAT_SHORTEST;
	private boolean longBeats;
	/** What the next shot should be, for the scout. */
	ShotType preferred;
	boolean wantWide;
	boolean songEnding;
	/** How far into the song's last seconds the song is, from 0 to 1, for the fade out. */
	float endProgress;
	boolean paused;
	/** How long a glide takes when the action is one. */
	float glideSeconds;
	/** The recent changes: their song time, kind and line, oldest first from {@link #historyStart}. */
	final long[] historyMs = new long[HISTORY];
	final int[] historyKind = new int[HISTORY];
	final int[] historyLine = new int[HISTORY];
	int historyCount;
	int historyStart;

	CineDirector(CinematicCameraFeature feature) {
		this.feature = feature;
	}

	/** Starts following the song from the current position, as when the film starts. */
	void reset() {
		track = MusicService.trackNumber();
		lastIndex = LyricTimes.mainIndexAt(MusicService.lyrics(), ModFeatures.MUSIC.lyricClockMs());
		linesInShot = 0;
		cutBefore = -1;
		gapMoved = false;
		pickTarget(lastIndex);
		timeLimit = pickTime(NO_BEAT_SHORTEST, NO_BEAT_LONGEST);
		preferred = ShotType.WIDE_CRANE;
		wantWide = false;
	}

	/**
	 * @param shotAge seconds the current shot has run
	 * @param hold    whether the player holds the shot
	 * @param next    whether the player asked for the next shot, taken at the next allowed cut
	 * @return what the film does now
	 */
	Action update(float shotAge, boolean hold, boolean next) {
		MusicService.Track song = ModFeatures.MUSIC.isEnabled() ? MusicService.track() : null;
		beats.update();
		boolean music = song != null && MusicService.isPlaying();
		paused = song != null && !MusicService.isPlaying();
		if (feature.calmMode() || song == null) {
			return calm(shotAge);
		}
		long at = ModFeatures.MUSIC.lyricClockMs();
		List<LyricLine> lines = MusicService.lyrics();
		if (MusicService.trackNumber() != track) {
			// a new song: a wide establishing shot, with the title card
			track = MusicService.trackNumber();
			lastIndex = LyricTimes.mainIndexAt(lines, at);
			linesInShot = 0;
			cutBefore = -1;
			gapMoved = false;
			pickTarget(lastIndex);
			preferred = ShotType.WIDE_CRANE;
			wantWide = true;
			return change(Action.CUT, KIND_SONG, at, lastIndex);
		}
		if (!music) {
			return Action.NONE;
		}
		long length = song.durationMs();
		songEnding = length > 0 && MusicService.position() > length - SONG_END_MS;
		endProgress = songEnding ? Math.min(1f, (MusicService.position() - (length - SONG_END_MS)) / (float) SONG_END_MS) : 0f;
		if (songEnding || hold) {
			return Action.NONE;
		}
		if (!hasMainLines(lines)) {
			return withoutLyrics(shotAge, at);
		}
		int index = LyricTimes.mainIndexAt(lines, at);
		boolean adlib = adlibAt(lines, at);
		if (index >= 0 && index != lastIndex) {
			// a line starts: the one moment besides the gaps where the film may cut
			lastIndex = index;
			gapMoved = false;
			if (cutBefore == index) {
				linesInShot = 1;
				return Action.NONE;
			}
			if (due(shotAge, next) && !adlib) {
				linesInShot = 1;
				pickTarget(index);
				advise(lines, index);
				return change(Action.CUT, KIND_LINE, at, index);
			}
			linesInShot++;
			return Action.NONE;
		}
		boolean sung = index >= 0 && at < LyricTimes.lineEnd(lines, index);
		int coming = LyricTimes.nextMainIndex(lines, at);
		long comingAt = coming >= 0 ? lines.get(coming).timeMs() : Long.MAX_VALUE;
		if (!sung && !adlib && coming >= 0 && cutBefore != coming && feature.cutOnBeat() && comingAt - at <= BEAT_WINDOW_MS) {
			// the cut for the coming line lands on the beat just before it, while the gap lasts
			float ageAtLine = shotAge + (comingAt - at) / 1000f;
			long beat = beats.beatNear(comingAt, BEAT_WINDOW_MS);
			if (beat >= 0 && beat <= comingAt && at >= beat && due(ageAtLine, next) && !adlibAt(lines, comingAt)) {
				cutBefore = coming;
				linesInShot = 0;
				pickTarget(coming);
				advise(lines, coming);
				return change(Action.CUT, KIND_BEAT, at, coming);
			}
		}
		if (!sung && !adlib) {
			long gapStart = index >= 0 ? LyricTimes.lineEnd(lines, index) : 0L;
			boolean longGap = comingAt - gapStart >= LONG_GAP_MS;
			if (longGap && !gapMoved && at - gapStart >= GAP_MOVE_AFTER_MS && comingAt - at > GAP_MOVE_ROOM_MS && shotAge >= SHORTEST_SHOT) {
				// an instrumental part: a slow move into the next shot instead of a hard cut
				gapMoved = true;
				linesInShot = 0;
				preferred = null;
				wantWide = true;
				glideSeconds = 3f;
				return change(feature.smoothMoves() ? Action.GLIDE : Action.CUT, KIND_GAP, at, -1);
			}
			if ((shotAge >= LONGEST_SHOT || next && shotAge >= 1f) && comingAt - at > BEAT_WINDOW_MS) {
				linesInShot = 0;
				advise(lines, coming);
				return change(Action.CUT, KIND_GAP, at, -1);
			}
		}
		return Action.NONE;
	}

	/**
	 * @return how long until a cut that can be foreseen now, in ms: one on a coming line's start, or on the beat just before it, as {@link #update} will take it; or {@link LyricCutFade#NONE}. A wrong guess costs a short dip of the lyrics, nothing
	 *         else.
	 */
	long untilCut(float shotAge, boolean hold, boolean next) {
		MusicService.Track song = ModFeatures.MUSIC.isEnabled() ? MusicService.track() : null;
		if (feature.calmMode() || song == null || !MusicService.isPlaying() || songEnding || hold) {
			return LyricCutFade.NONE;
		}
		List<LyricLine> lines = MusicService.lyrics();
		long at = ModFeatures.MUSIC.lyricClockMs();
		if (!hasMainLines(lines)) {
			return LyricCutFade.NONE;
		}
		int started = LyricTimes.mainIndexAt(lines, at);
		if (started >= 0 && started != lastIndex) {
			// a line has just started and the film sees it at its next update: if it cuts there, that is now
			return cutBefore != started && due(shotAge, next) && !adlibAt(lines, at) ? 0L : LyricCutFade.NONE;
		}
		int coming = LyricTimes.nextMainIndex(lines, at);
		if (coming < 0) {
			return LyricCutFade.NONE;
		}
		long comingAt = lines.get(coming).timeMs();
		if (comingAt - at > BEAT_WINDOW_MS || cutBefore == coming || adlibAt(lines, comingAt)) {
			return LyricCutFade.NONE;
		}
		if (!due(shotAge + (comingAt - at) / 1000f, next)) {
			return LyricCutFade.NONE;
		}
		if (feature.cutOnBeat()) {
			boolean sung = started >= 0 && at < LyricTimes.lineEnd(lines, started);
			long beat = beats.beatNear(comingAt, BEAT_WINDOW_MS);
			if (!sung && !adlibAt(lines, at) && beat >= 0 && beat <= comingAt) {
				return Math.max(0L, beat - at);
			}
		}
		return comingAt - at;
	}

	/** @return whether a cut is due at a line start: the pace's lines are done (or the player asked), and the shortest shot length is reached */
	private boolean due(float shotAge, boolean next) {
		if (next) {
			return shotAge >= 1f;
		}
		return shotAge >= SHORTEST_SHOT && (linesInShot >= targetLines || shotAge >= LONGEST_SHOT);
	}

	/** Without lyrics: on every 8th or 16th beat, or every 8 to 14 seconds without a steady beat. */
	private Action withoutLyrics(float shotAge, long at) {
		if (shotAge < SHORTEST_SHOT) {
			return Action.NONE;
		}
		if (beats.steady()) {
			int wanted = switch (feature.pace()) {
				case ENERGETIC -> 8;
				case RELAXED -> 16;
				default -> longBeats ? 16 : 8;
			};
			if (beats.sinceCut >= wanted) {
				longBeats = !longBeats;
				return change(Action.CUT, KIND_BEAT, at, -1);
			}
			return Action.NONE;
		}
		if (shotAge >= timeLimit) {
			timeLimit = pickTime(NO_BEAT_SHORTEST, NO_BEAT_LONGEST);
			return change(Action.CUT, KIND_TIME, at, -1);
		}
		return Action.NONE;
	}

	/** The calm mode, and the film without music: one slow shot after another, blending softly. */
	private Action calm(float shotAge) {
		if (shotAge < timeLimit) {
			return Action.NONE;
		}
		timeLimit = pickTime(CALM_SHORTEST, CALM_LONGEST);
		preferred = null;
		wantWide = true;
		glideSeconds = 3.5f;
		return change(Action.GLIDE, KIND_TIME, MusicService.track() != null ? MusicService.position() : 0L, -1);
	}

	private Action change(Action action, int kind, long songMs, int line) {
		beats.sinceCut = 0;
		int slot = (historyStart + historyCount) % HISTORY;
		if (historyCount == HISTORY) {
			historyStart = (historyStart + 1) % HISTORY;
		} else {
			historyCount++;
		}
		historyMs[slot] = songMs;
		historyKind[slot] = kind;
		historyLine[slot] = line;
		return action;
	}

	/** Picks how many lines the next shot lasts by the pace, one less if it starts in the chorus. */
	private void pickTarget(int index) {
		CinematicCameraFeature.Pace pace = feature.pace();
		targetLines = pace.leastLines + random.nextInt(pace.mostLines - pace.leastLines + 1);
		// a chorus shot is at most one line shorter; the shortest shot length still holds
		if (index >= 0 && LyricChorus.at(MusicService.lyrics(), index)) {
			targetLines = Math.max(1, targetLines - 1);
		}
		timeLimit = pickTime(NO_BEAT_SHORTEST, NO_BEAT_LONGEST);
	}

	private float pickTime(float shortest, float longest) {
		return shortest + random.nextFloat() * (longest - shortest);
	}

	/** Tells the scout what the shot starting at a line should be: wide in the chorus, close for a quiet line. */
	private void advise(List<LyricLine> lines, int index) {
		preferred = null;
		wantWide = false;
		if (index < 0 || index >= lines.size()) {
			preferred = ShotType.WIDE_CRANE;
			return;
		}
		if (LyricChorus.at(lines, index)) {
			wantWide = true;
			preferred = ShotType.LOW_HERO;
		} else if (LyricTimes.words(lines.get(index).text()) <= QUIET_WORDS) {
			preferred = ShotType.CLOSE_PUSH_IN;
		}
	}

	/** @return the line the next shot is likely to start at, for planning it ahead */
	int upcomingCutLine() {
		List<LyricLine> lines = MusicService.lyrics();
		int index = lastIndex;
		int left = Math.max(1, targetLines - linesInShot + 1);
		for (int i = index + 1; i < lines.size() && left > 0; i++) {
			if (LyricTimes.main(lines.get(i))) {
				left--;
				if (left == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/** Fills in what the next shot should be for planning: ahead of the cut, from the line it will start at. */
	void brief(CineScout.Brief brief) {
		if (preferred == null && !wantWide) {
			advise(MusicService.lyrics(), upcomingCutLine());
		}
		brief.preferred = preferred;
		brief.wantWide = wantWide;
	}

	/** @return seconds until the next moment the film may cut, as far as known; large when unknown */
	float secondsToNextCut() {
		List<LyricLine> lines = MusicService.lyrics();
		long at = ModFeatures.MUSIC.lyricClockMs();
		int index = LyricTimes.mainIndexAt(lines, at);
		if (index < 0 || at >= LyricTimes.lineEnd(lines, index)) {
			return 0f;
		}
		int coming = LyricTimes.nextMainIndex(lines, at);
		return coming < 0 ? Float.MAX_VALUE : (lines.get(coming).timeMs() - at) / 1000f;
	}

	// ----------------------------------------------------------------------------------------------------- lines

	private static boolean hasMainLines(List<LyricLine> lines) {
		for (LyricLine line : lines) {
			if (LyricTimes.main(line)) {
				return true;
			}
		}
		return false;
	}

	/** @return whether an adlib is sung at a time */
	static boolean adlibAt(List<LyricLine> lines, long at) {
		for (int i = 0; i < lines.size(); i++) {
			LyricLine line = lines.get(i);
			if (line.timeMs() > at + LONG_GAP_MS) {
				break;
			}
			for (Background part : line.backgrounds()) {
				if (at >= part.startMs() && at <= part.endMs()) {
					return true;
				}
			}
		}
		return false;
	}
}
