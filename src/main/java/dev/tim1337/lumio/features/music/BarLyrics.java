package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.features.music.MusicService.Background;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.ui.Ui;

import java.util.List;

/**
 * The song and its lyrics in the info bar's song segment, in one fixed width. While music plays and no line is sung it shows the title and the
 * artist; a sung line replaces them, lit word by word or as a whole as set, with its adlib small after it; paused, the song comes back with a pause
 * icon; and when no line was sung for about six seconds (an instrumental part, the end of the lyrics) the song comes back until the next line
 * starts. Changes cross-fade with a small slide: the old text rises out while the new one rises in, each with its own highlight box, which fades
 * with its line. A line wider than the room scrolls with the singing, keeping the sung word around the middle (see {@link BarScroll}), with soft
 * edges, and never jumps. Its adlibs stand in room kept after it when it is laid out, so they never move it, and stay with their own line.
 * Wherever there is room the next lines follow it, each after a small dot and dimmer the farther ahead (see {@link NextLinePreview}); when the next one
 * starts, the whole row moves up as one, laid out already, and it becomes the sung line. On a long word the box fills from left to right as the word
 * is sung instead of covering it at once, as the first info bar did; a quick word and a line without word timing stay as they are.
 * <p>
 * A line is laid out once, glyph by glyph, when it starts; drawing it allocates nothing.
 */
public final class BarLyrics {
	private static final float FADE_SECONDS = 0.25f;
	/** How far the old text rises out and the new one rises in during a change, in GUI pixels. */
	private static final float SLIDE = 3f;
	/** The song comes back when no line was sung for this long. */
	private static final long SONG_AFTER_MS = 6000L;
	/** How far ahead of the song the scroll looks, so it moves evenly through fast words. */
	private static final long AHEAD_MS = 450L;
	/** How fast the sung part follows the song, and only forward within a line. */
	private static final float SUNG_RATE = 18f;
	/** Width of the soft edges where a scrolling line is cut, in GUI pixels. */
	private static final float EDGE = 12f;
	/** The part of a line not sung yet, and the artist after the title, are dimmer. */
	private static final float DIM = 0.4f;
	/** A line of text here: its height, and where the middle of its small letters is, where the dots of a break stand. */
	private static final float LINE_HEIGHT = 9f;
	private static final float DOTS_MIDDLE = 4f;
	private static final float ARTIST_ALPHA = 0.6f;
	/** An adlib stands after its line, past the end of the line's highlight box and a clear gap (see {@link AdlibLayout}), slightly raised. */
	private static final float ADLIB_RISE = 1.2f;
	/** The highlight box: its room around the word, how far it reaches above the text's top, its height and corners. */
	private static final float BOX_PAD = 3f;
	private static final float BOX_ABOVE = 2.5f;
	private static final float BOX_HEIGHT = 13f;
	private static final float BOX_RADIUS = 4f;
	/** Along a line without word timing the box reaches this share of an average word to each side of the sung point, at least this many characters. */
	private static final float GLIDE_WORD_SHARE = 0.75f;
	private static final float GLIDE_LEAST_CHARS = 2f;
	private static final int PAUSE_ICON = 7;
	private static final float PAUSE_GAP = 4f;
	private static final float ARTIST_GAP = 5f;
	/** The text's middle below its top, for the pause icon; the room above and below the text that a scrolling line may use. */
	private static final float TEXT_MIDDLE = 4f;
	private static final int CLIP_ABOVE = 6;
	private static final int CLIP_BELOW = 14;
	private static final int SHADOW = 0x8C000000;
	/** How fast a line ahead settles at its place and brightness when the row changed around it. */
	private static final float AHEAD_RATE = 12f;
	/** A song too long for its room scrolls at this speed in GUI pixels a second, resting at its start first, with this room between its end and its start. */
	private static final float MARQUEE_SPEED = 18f;
	private static final float MARQUEE_REST_SECONDS = 2f;
	private static final float MARQUEE_GAP = 24f;
	private static final float NANOS_PER_SECOND = 1e9f;
	/** What a view shows when it is not a line. */
	private static final int SONG = -1;
	private static final int PAUSED = -2;
	private static final int NONE = Integer.MIN_VALUE;

	/** One thing shown: the song, or a line with its glyphs laid out and how far it is sung and scrolled. */
	private static final class View {
		int key = NONE;
		LyricLine line;
		String[] glyphs = new String[0];
		float[] glyphX = new float[0];
		int count;
		float width;
		float sung;
		float scroll;
		/** The room kept after the line for its adlibs: its widest adlib and the gap before it, 0 without adlibs. */
		float adlibRoom;
		/** Its own highlight box, which stays with it while it fades out after the next line started. */
		final HighlightBox box = new HighlightBox();
		/** The word the box is on, by where it starts, and how much of a long word it is, worked out once per word; the box then fills it up. */
		int fillWord = -1;
		float longWord;
		// as a line ahead: where it starts from the sung line's start and where it is going, how bright and how visible it is
		float offset;
		float target;
		float ahead;
		float visible;
		/** How many of its characters show (whole words only), and from which one they are still fading in, by {@link #grown}. */
		int limit;
		int solid;
		float grown = 1f;
	}

	private final MusicFeature music;
	private final AnimClock clock = new AnimClock();
	private final BarScroll scroller = new BarScroll();
	/** The word the box moves to this frame, in line units. */
	private float boxFrom;
	private float boxTo;
	private View current = new View();
	private View previous = new View();
	/** The lines ahead, in order, and how many of them should show now; the rest fade out. */
	private final View[] ahead = new View[NextLinePreview.MOST_LINES];
	private int aheadShown;
	/** What the lines ahead were laid out for: the sung line, the room, the lyrics and the room kept for its adlibs. */
	private int aheadKey = NONE;
	private float aheadRoom = -1f;
	private float aheadAdlib = -1f;
	private List<LyricLine> aheadLyrics;
	/** Where the sung line started its move when it came from the lines ahead, from the segment's start; 0 when it did not. */
	private float slideFrom;
	/** How far the change of the sung line is, for the words of a new sung line that were left out while it was ahead. */
	private float slideIn = 1f;
	private List<LyricLine> shownLyrics;
	private float fade = 1f;
	private float adlib;
	private Background adlibPart;
	/** When the adlib shown first showed, by the lyrics' clock: it stays at least a moment, however short its timing. */
	private long adlibSince = AdlibTimes.NOT_SHOWN;
	// the adlib glyph by glyph, laid out once per adlib, so it fades at a cut edge like the line
	private Background adlibLaid;
	private String[] adlibGlyphs = new String[0];
	private float[] adlibX = new float[1];
	private int adlibCount;
	// the song's title and artist, measured once per song and size
	private MusicService.Track fittedTrack;
	private float fittedZoom = -1f;
	/** The title and the artist after it, as wide as they are drawn whole. */
	private float songWidth;
	private String title = "";
	private String artist = "";
	private float titleWidth;

	BarLyrics(MusicFeature music) {
		this.music = music;
		for (int i = 0; i < ahead.length; i++) {
			ahead[i] = new View();
		}
	}

	/**
	 * Draws the segment's text: the song or the sung line, cross-fading between them, clipped to the room.
	 *
	 * @param top   the text's top
	 * @param room  the fixed width of the text
	 * @param color the text's color; the parts not sung yet and the artist are dimmer
	 */
	public void draw(Ui ui, float x, float top, float room, float baseAlpha, int color, boolean shadow) {
		// on the screen too the lyrics fade around a cut of the film
		float alpha = baseAlpha * ModFeatures.CINEMATIC.lyricFade();
		float dt = clock.tick();
		boolean animated = ModFeatures.MENU.animations();
		MusicService.Track track = MusicService.track();
		if (track == null) {
			return;
		}
		long at = music.lyricClockMs();
		List<LyricLine> lines = MusicService.lyrics();
		int key = wantedKey(lines, at);
		if (key != current.key || lines != shownLyrics) {
			// the first line ahead becomes the sung line where it stands, laid out already: the whole row moves up in one motion
			View first = ahead[0];
			boolean moveUp = animated && first.visible > 0.5f && key >= 0 && lines == shownLyrics && first.key == key && first.line == lines.get(key);
			slideFrom = moveUp ? first.offset : 0f;
			shownLyrics = lines;
			View old = previous;
			previous = current;
			if (moveUp) {
				current = first;
				moveAheadUp(old, slideFrom);
			} else {
				current = old;
				set(ui, current, key, key >= 0 ? lines.get(key) : null);
				for (View view : ahead) {
					view.key = NONE;
					view.visible = 0f;
				}
			}
			// the lines that stay ahead keep showing while the row moves up
			aheadShown = moveUp ? Math.max(0, aheadShown - 1) : 0;
			aheadKey = NONE;
			scroller.reset(0f);
			fade = animated && previous.key != NONE ? 0f : 1f;
		}
		fade = animated ? Math.min(1f, fade + dt / FADE_SECONDS) : 1f;
		advance(ui, current, at, room, dt, animated);
		updateAdlib(at, dt, animated);
		updateAhead(ui, lines, room, dt, animated);
		float in = fade * fade * (3f - 2f * fade);
		slideIn = in;
		// the room ends where the margin before the music bars begins: nothing goes into it. On the left the box of the first word stands out by its padding.
		ui.graphics.enableScissor(Math.round(x - BOX_PAD) - 1, Math.round(top) - CLIP_ABOVE, Math.round(x + room), Math.round(top) + CLIP_BELOW);
		try {
			if (slideFrom > 0f) {
				// the whole row moves up: the old line moves out to the left as the new one moves from its place to the start, brightening, on one line
				float moved = slideFrom * in;
				if (in < 1f) {
					drawView(ui, previous, track, x - moved, top, room, alpha * (1f - in), color, shadow);
					drawDot(ui, x + slideFrom - moved, top, alpha * (1f - in) * NextLinePreview.ALPHA, color);
				}
				drawView(ui, current, track, x + slideFrom - moved, top, room, alpha * (NextLinePreview.ALPHA + (1f - NextLinePreview.ALPHA) * in), color,
					shadow);
				drawAhead(ui, x + slideFrom - moved, top, alpha, color, shadow);
				if (in >= 1f) {
					slideFrom = 0f;
					current.limit = current.count;
				}
			} else {
				// the old text rises out while the new one rises in from a little below; their fades overlap, so there is never an empty frame
				if (in < 1f) {
					drawView(ui, previous, track, x, top - SLIDE * in, room, alpha * (1f - in), color, shadow);
				}
				drawView(ui, current, track, x, top + SLIDE * (1f - in), room, alpha * in, color, shadow);
				drawAhead(ui, x, top, alpha, color, shadow);
			}
		} finally {
			ui.graphics.disableScissor();
		}
	}

	/**
	 * @param full the room with the lyrics in it, which is also the most the song may take
	 * @return the room the text wants now: all of it while a line is sung (short pauses between lines keep the line), otherwise only as much as the
	 *         title and artist need, so the bar has no empty part; the bar eases between the two
	 */
	public float wantedRoom(Ui ui, float full) {
		MusicService.Track track = MusicService.track();
		if (track == null) {
			return full;
		}
		int key = wantedKey(MusicService.lyrics(), music.lyricClockMs());
		if (key >= 0) {
			return full;
		}
		boolean paused = key == PAUSED;
		measureSong(ui, track);
		float song = songWidth + (paused ? PAUSE_ICON + PAUSE_GAP : 0f);
		return Math.min(full, (float) Math.ceil(song));
	}

	/** The row moves up: every line ahead takes the place before it, keeping where it stands on the screen; the old sung view becomes a free one. */
	private void moveAheadUp(View free, float shift) {
		for (int i = 0; i + 1 < ahead.length; i++) {
			ahead[i] = ahead[i + 1];
			ahead[i].offset -= shift;
			ahead[i].target -= shift;
		}
		free.key = NONE;
		free.line = null;
		free.visible = 0f;
		ahead[ahead.length - 1] = free;
	}

	/**
	 * Lays out the lines ahead when what they follow changed, and moves them to their places. A new line only joins once the sung line has settled, so
	 * during a change the row stays as it is and moves as one.
	 */
	private void updateAhead(Ui ui, List<LyricLine> lines, float room, float dt, boolean animated) {
		if (current.line == null) {
			aheadShown = 0;
			aheadKey = NONE;
		} else if (fade >= 1f && slideFrom <= 0f && (aheadKey != current.key || aheadRoom != room || aheadLyrics != lines || aheadAdlib != current.adlibRoom)) {
			aheadKey = current.key;
			aheadRoom = room;
			aheadLyrics = lines;
			aheadAdlib = current.adlibRoom;
			layOutAhead(ui, lines, room);
		}
		float settle = 1f - (float) Math.exp(-dt * AHEAD_RATE);
		float fadeStep = dt / FADE_SECONDS;
		for (int i = 0; i < ahead.length; i++) {
			View view = ahead[i];
			if (view.key == NONE) {
				continue;
			}
			boolean wanted = i < aheadShown;
			if (animated) {
				view.visible = wanted ? Math.min(1f, view.visible + fadeStep) : Math.max(0f, view.visible - fadeStep);
				view.offset += (view.target - view.offset) * settle;
				view.ahead += (NextLinePreview.alpha(i) - view.ahead) * settle;
				view.grown = Math.min(1f, view.grown + fadeStep);
			} else {
				view.visible = wanted ? 1f : 0f;
				view.offset = view.target;
				view.ahead = NextLinePreview.alpha(i);
				view.grown = 1f;
			}
		}
	}

	/** Fits the next lines after the sung one: whole lines, then the whole words of the first that does not fit, and nothing more after it. */
	private void layOutAhead(Ui ui, List<LyricLine> lines, float room) {
		aheadShown = 0;
		// a line that does not fit scrolls, and has the row for itself
		if (totalWidth(current) > room) {
			return;
		}
		float cursor = current.width + current.adlibRoom;
		long after = current.line.timeMs();
		while (aheadShown < ahead.length) {
			int index = LyricTimes.nextMainIndex(lines, after);
			float start = cursor + NextLinePreview.GAP;
			if (index < 0 || start >= room) {
				return;
			}
			LyricLine line = lines.get(index);
			if (LyricBreaks.is(line)) {
				// the lines ahead end at a break: what comes after it is shown once the dots are on
				return;
			}
			View view = ahead[aheadShown];
			if (view.key != index || view.line != line) {
				set(ui, view, index, line);
				view.visible = 0f;
				view.offset = start;
				view.ahead = NextLinePreview.alpha(aheadShown);
				view.grown = 1f;
			}
			// also for a line that does not fit yet: it stays laid out here and moves up with the row
			view.target = start;
			int fits = NextLinePreview.fit(line.text(), view.glyphX, view.count, room - start);
			if (fits == 0) {
				return;
			}
			if (fits > view.limit) {
				view.solid = view.limit;
				view.grown = 0f;
			}
			view.limit = fits;
			view.solid = Math.min(view.solid, fits);
			aheadShown++;
			if (fits < view.count) {
				return;
			}
			cursor = start + view.width;
			after = line.timeMs();
		}
	}

	/** Draws the lines ahead, each after its dot, plain and dim (never lit or boxed), from the sung line's start. */
	private void drawAhead(Ui ui, float origin, float top, float alpha, int color, boolean shadow) {
		for (View view : ahead) {
			if (view.key == NONE || view.line == null || view.visible <= 0f) {
				continue;
			}
			float bright = alpha * view.ahead * view.visible;
			if (bright <= 0.005f) {
				continue;
			}
			float start = origin + view.offset;
			drawDot(ui, start, top, bright, color);
			int solid = Ui.withOpacity(color, bright);
			for (int i = 0; i < view.limit; i++) {
				String glyph = view.glyphs[i];
				if (glyph.isEmpty() || glyph.equals(" ")) {
					continue;
				}
				int glyphColor = i < view.solid ? solid : Ui.withOpacity(color, bright * view.grown);
				float gx = start + view.glyphX[i];
				if (shadow) {
					ui.textAtScaledBold(glyph, gx + 0.5f, top + 0.7f, 1f, Ui.withOpacity(SHADOW, (glyphColor >>> 24) / 255f));
				}
				ui.textAtScaledBold(glyph, gx, top, 1f, glyphColor);
			}
		}
	}

	/** Draws the small dot before a line ahead that starts at a place. */
	private static void drawDot(Ui ui, float lineStart, float top, float bright, int color) {
		float size = NextLinePreview.DOT;
		ui.roundedSmooth(lineStart - NextLinePreview.GAP / 2f - size / 2f, top + TEXT_MIDDLE - size / 2f, size, size, size / 2f,
			Ui.withOpacity(color, bright * NextLinePreview.DOT_SHARE));
	}

	/** @return what should show now: the sung line's index, or the song, paused or playing */
	private int wantedKey(List<LyricLine> lines, long at) {
		if (!MusicService.isPlaying()) {
			return PAUSED;
		}
		if (!music.lyrics.get() || lines.isEmpty()) {
			return SONG;
		}
		// a small step back of the clock keeps the line shown, so it never flips back to the line before
		int index = LyricTimes.steadyIndex(lines, at, LyricTimes.mainIndexAt(lines, at), current.key);
		if (index < 0 || at >= LyricTimes.lineEnd(lines, index) + SONG_AFTER_MS) {
			return SONG;
		}
		return index;
	}

	/** Lays a view out for what it shows: a line glyph by glyph, starting unsung at its beginning. */
	private void set(Ui ui, View view, int key, LyricLine line) {
		view.key = key;
		view.line = line;
		view.sung = 0f;
		view.scroll = 0f;
		view.count = 0;
		view.width = 0f;
		view.adlibRoom = 0f;
		view.fillWord = -1;
		view.longWord = 0f;
		// the new line's box grows in on its first word
		view.box.reset();
		if (line == null) {
			return;
		}
		String text = line.text();
		if (view.glyphs.length < text.length()) {
			view.glyphs = new String[text.length()];
			view.glyphX = new float[text.length() + 1];
		}
		float cursor = 0f;
		for (int i = 0; i < text.length(); ) {
			int end = i + Character.charCount(text.codePointAt(i));
			String glyph = text.substring(i, end);
			// one glyph per character index, so the sung count lines up with the glyphs; the second half of a pair draws nothing
			for (int c = i; c < end; c++) {
				view.glyphs[c] = c == i ? glyph : "";
				view.glyphX[c] = cursor;
			}
			cursor += ui.boldWidthScaled(glyph, 1f);
			i = end;
		}
		view.count = text.length();
		view.limit = view.count;
		view.solid = view.count;
		view.glyphX[view.count] = cursor;
		view.width = cursor;
		// room for the line's adlibs, kept from the start: an adlib appearing or leaving never moves the line
		if (music.adlibsShown()) {
			for (Background part : line.backgrounds()) {
				view.adlibRoom = Math.max(view.adlibRoom, adlibOffset() + ui.widthScaled(part.text(), AdlibLayout.SCALE));
			}
		}
	}

	/** Moves the sung part forward with the song and the scroll along with it. */
	private void advance(Ui ui, View view, long at, float room, float dt, boolean animated) {
		if (view.line == null) {
			return;
		}
		float wanted = MusicFeature.sungChars(view.line, at);
		view.sung = animated ? Math.max(view.sung, view.sung + (wanted - view.sung) * (1f - (float) Math.exp(-dt * SUNG_RATE))) : Math.max(view.sung, wanted);
		float total = totalWidth(view);
		float sungX = sungX(view, view.sung);
		if (view == current && music.highlightBox.get()) {
			followBox(view, dt, animated);
		}
		// the scroll keeps the word's box in the middle; without the box it follows the sung point
		boolean boxed = view == current && music.highlightBox.get();
		float center = boxed ? (boxFrom + boxTo) / 2f : sungX;
		float ahead = sungX(view, sungAt(view, at + AHEAD_MS));
		// the box as drawn, which moves continuously, so keeping it inside the room never makes the scroll jump
		float left = boxed ? view.box.left() : sungX;
		float right = boxed ? view.box.right() : sungX;
		view.scroll = scroller.update(center, Math.max(center, ahead), left, right, total, room, dt, animated);
	}

	/** @return the sung characters of a view's line at a time, for looking ahead: word by word, or evenly through the line's time */
	private float sungAt(View view, long at) {
		List<LyricLine> lines = MusicService.lyrics();
		long end = view.key < lines.size() ? LyricTimes.lineEnd(lines, view.key) : view.line.timeMs() + 1L;
		if (at >= end) {
			return view.count;
		}
		if (!view.line.words().isEmpty()) {
			return MusicFeature.sungChars(view.line, at);
		}
		return view.count * Math.min(1f, Math.max(0f, (at - view.line.timeMs()) / (float) Math.max(1L, end - view.line.timeMs())));
	}

	/**
	 * Moves the highlight box to the word being sung; along a line without word timing it glides evenly around the sung point instead, only
	 * forward, as the sung part only moves forward.
	 */
	private void followBox(View view, float dt, boolean animated) {
		float from;
		float to;
		boolean glide = view.line.words().isEmpty() && view.count > 0;
		if (glide) {
			float slack = Math.max(GLIDE_LEAST_CHARS, view.count / (float) words(view.line.text()) * GLIDE_WORD_SHARE);
			from = sungX(view, view.sung - slack) - BOX_PAD;
			to = sungX(view, view.sung + slack) + BOX_PAD;
		} else if (view.count > 0) {
			int at = Math.max(0, Math.min(view.count - 1, (int) view.sung));
			// between two words the box stays on the one sung last
			if (at > 0 && Character.isWhitespace(view.line.text().charAt(at))) {
				at--;
			}
			int wordStart = HighlightBox.wordStart(view.line.text(), at);
			int wordEnd = HighlightBox.wordEnd(view.line.text(), at);
			if (wordStart != view.fillWord) {
				view.fillWord = wordStart;
				view.longWord = HighlightBox.longWord(wordMillis(view.line, wordStart, wordEnd));
			}
			from = view.glyphX[wordStart] - BOX_PAD;
			to = view.glyphX[wordEnd] + BOX_PAD;
			if (view.longWord > 0f) {
				// a long word fills up: the box's end stands where the singing is in the word, so it grows over the word's sung time
				float filled = Math.max(view.glyphX[wordStart], sungX(view, Math.min(view.sung, wordEnd))) + BOX_PAD;
				to += (filled - to) * view.longWord;
			}
		} else {
			from = -BOX_PAD;
			to = view.width + BOX_PAD;
		}
		boxFrom = from;
		boxTo = to;
		view.box.follow(view.key, from, to, dt, animated, glide);
	}

	/** @return how long a word of a line is sung, from the first timed part in it to the end of the last one: one word can be timed in several parts */
	private static long wordMillis(LyricLine line, int from, int to) {
		long first = Long.MAX_VALUE;
		long last = Long.MIN_VALUE;
		List<MusicService.Word> words = line.words();
		for (int i = 0; i < words.size(); i++) {
			MusicService.Word word = words.get(i);
			if (word.from() < to && word.to() > from) {
				first = Math.min(first, word.startMs());
				last = Math.max(last, word.endMs());
			}
		}
		return last > first ? last - first : 0L;
	}

	/** @return the room a view's line takes: its text with what stands after it, its adlibs' room, or at the end of the last word its box's padding */
	private float totalWidth(View view) {
		return view.width + Math.max(view.adlibRoom, music.highlightBox.get() ? BOX_PAD : 0f);
	}

	/** @return how many words a text has, at least one */
	private static int words(String text) {
		int count = 1;
		for (int i = 1; i < text.length(); i++) {
			if (text.charAt(i) == ' ' && text.charAt(i - 1) != ' ') {
				count++;
			}
		}
		return count;
	}

	/** @return the x in the line after a number of sung characters, fractional within a glyph */
	private static float sungX(View view, float chars) {
		float sung = Math.max(0f, Math.min(view.count, chars));
		int whole = (int) sung;
		float x = view.glyphX[whole];
		return whole < view.count ? x + (view.glyphX[whole + 1] - x) * (sung - whole) : x;
	}

	/** @return how far after the line's end an adlib starts: past the highlight box's end and the gap kept from the line */
	private static float adlibOffset() {
		return BOX_PAD + AdlibLayout.gap(BOX_HEIGHT);
	}

	private void updateAdlib(long at, float dt, boolean animated) {
		boolean on = music.adlibsShown() && (current.line != null || previous.line != null);
		Background part = on ? MusicService.backgroundAt(at) : null;
		if (part != null && part != adlibPart) {
			adlibPart = part;
			adlibSince = at;
		}
		// a short adlib (a quick "hey") stays a moment after its own time, so it is seen at all
		boolean showing = part != null || on && adlibPart != null && AdlibTimes.shown(adlibPart.startMs(), adlibPart.endMs(), adlibSince, at);
		float target = showing ? 1f : 0f;
		adlib = animated ? (adlib < target ? Math.min(target, adlib + dt / AdlibTimes.IN_SECONDS) : Math.max(target, adlib - dt / AdlibTimes.OUT_SECONDS)) : target;
		if (adlib <= 0f) {
			adlibPart = null;
			adlibSince = AdlibTimes.NOT_SHOWN;
		}
	}

	/**
	 * @return whether the adlib sung now stands with a view: with the line it belongs to, by the lyrics; one sung in a gap stays with the line still
	 *         shown
	 */
	private boolean adlibWith(View view) {
		if (adlibPart == null || view.line == null) {
			return false;
		}
		if (view.line.backgrounds().contains(adlibPart)) {
			return true;
		}
		boolean ownedElsewhere = previous.line != null && previous.line.backgrounds().contains(adlibPart)
			|| current.line != null && current.line.backgrounds().contains(adlibPart);
		return !ownedElsewhere && view == current;
	}

	private void layAdlib(Ui ui, Background part) {
		adlibLaid = part;
		String text = part.text();
		if (adlibGlyphs.length < text.length()) {
			adlibGlyphs = new String[text.length()];
			adlibX = new float[text.length() + 1];
		}
		adlibCount = 0;
		float cursor = 0f;
		for (int i = 0; i < text.length(); ) {
			int end = i + Character.charCount(text.codePointAt(i));
			adlibGlyphs[adlibCount] = text.substring(i, end);
			adlibX[adlibCount++] = cursor;
			cursor += ui.widthScaled(adlibGlyphs[adlibCount - 1], AdlibLayout.SCALE);
			i = end;
		}
		adlibX[adlibCount] = cursor;
	}

	private void drawView(Ui ui, View view, MusicService.Track track, float x, float top, float room, float alpha, int color, boolean shadow) {
		if (alpha <= 0.005f || view.key == NONE) {
			return;
		}
		if (view.line == null) {
			drawSong(ui, track, view.key == PAUSED, x, top, room, alpha, color, shadow);
			return;
		}
		float total = totalWidth(view);
		boolean cutLeft = view.scroll > 0.5f;
		boolean cutRight = total - view.scroll > room + 0.5f;
		boolean boxed = music.highlightBox.get();
		int lyricColor = music.lyricsArgb();
		// a break has no box: only its dots show, in the lyrics' color
		if (boxed && !LyricBreaks.is(view.line)) {
			// each line has its own box: the old one fades with its line where it stood, never over the new one
			view.box.drawWithin(ui, x - view.scroll, top - BOX_ABOVE, BOX_HEIGHT, BOX_RADIUS, lyricColor, alpha, view.scroll - BOX_PAD, view.scroll + room);
		}
		// without the box the sung words take the lyrics color; on the box they are white or dark, whichever reads on it
		int lit = Ui.withOpacity(boxed ? color : lyricColor, alpha);
		int dim = Ui.withOpacity(color, alpha * DIM);
		int onBox = Ui.withOpacity(HighlightBox.textOn(lyricColor), alpha);
		if (LyricBreaks.is(view.line)) {
			// a break: its dots in place of the glyphs
			LyricDots.draw(ui, view.line, music.lyricClockMs(), x - view.scroll, top + DOTS_MIDDLE, LINE_HEIGHT, Ui.withOpacity(lyricColor, alpha));
			return;
		}
		for (int i = 0; i < view.count; i++) {
			String glyph = view.glyphs[i];
			if (glyph.isEmpty() || glyph.equals(" ")) {
				continue;
			}
			float gx = x + view.glyphX[i] - view.scroll;
			float gw = view.glyphX[i + 1] - view.glyphX[i];
			if (gx + gw < x || gx > x + room) {
				continue;
			}
			float edge = edgeAlpha(gx + gw / 2f - x, room, cutLeft, cutRight);
			float sung = Math.max(0f, Math.min(1f, view.sung - i));
			boolean onIt = boxed && view.box.covers(view.glyphX[i] + gw / 2f);
			int before = Ui.mix(dim, lit, sung);
			int shownColor = onIt ? onBox : before;
			if (boxed && view.longWord > 0f && view.box.left() <= view.glyphX[i] && view.box.right() < view.glyphX[i + 1] + BOX_PAD) {
				// at the end of a long word's box the color turns with the box's edge, not all at once per letter
				float under = Math.max(0f, Math.min(1f, (view.box.right() - view.glyphX[i]) / Math.max(1f, gw)));
				shownColor = Ui.mix(before, onBox, under);
			}
			if (i >= view.limit) {
				// the words left out while this line was ahead, coming in as it moves up
				shownColor = Ui.withOpacity(shownColor, slideIn);
			}
			int glyphColor = Ui.withOpacity(shownColor, edge);
			if (shadow) {
				ui.textAtScaledBold(glyph, gx + 0.5f, top + 0.7f, 1f, Ui.withOpacity(SHADOW, (glyphColor >>> 24) / 255f));
			}
			ui.textAtScaledBold(glyph, gx, top, 1f, glyphColor);
		}
		if (adlib > 0f && adlibWith(view)) {
			if (adlibPart != adlibLaid) {
				layAdlib(ui, adlibPart);
			}
			float ax = x + view.width + adlibOffset() - view.scroll;
			float ay = Ui.besideTop(top, 1f, AdlibLayout.SCALE) + ADLIB_RISE;
			for (int i = 0; i < adlibCount; i++) {
				float gx = ax + adlibX[i];
				float gw = adlibX[i + 1] - adlibX[i];
				if (gx + gw < x || gx > x + room) {
					continue;
				}
				float edge = edgeAlpha(gx + gw / 2f - x, room, cutLeft, cutRight);
				ui.textAtScaled(adlibGlyphs[i], gx, ay, AdlibLayout.SCALE, Ui.withOpacity(color, alpha * AdlibLayout.ALPHA * adlib * edge));
			}
		}
	}

	/** @return how visible a glyph is at a place in the room: fading out towards an edge where the line is cut */
	private static float edgeAlpha(float at, float room, boolean cutLeft, boolean cutRight) {
		float alpha = 1f;
		if (cutLeft) {
			alpha *= Math.max(0f, Math.min(1f, at / EDGE));
		}
		if (cutRight) {
			alpha *= Math.max(0f, Math.min(1f, (room - at) / EDGE));
		}
		return alpha;
	}

	/** Draws the song: a pause icon when paused, the title, and the artist dimmer after it as far as there is room. */
	private void drawSong(Ui ui, MusicService.Track track, boolean paused, float x, float top, float room, float alpha, int color, boolean shadow) {
		float cursor = x;
		if (paused) {
			ui.icon("pause", Math.round(x), Math.round(top + TEXT_MIDDLE - PAUSE_ICON / 2f), PAUSE_ICON, Ui.withOpacity(color, alpha));
			cursor += PAUSE_ICON + PAUSE_GAP;
		}
		measureSong(ui, track);
		float room2 = room - (cursor - x);
		if (songWidth <= room2 + 0.5f) {
			drawSongText(ui, cursor, top, alpha, color, shadow);
			return;
		}
		// too long for the room: it moves slowly to the left and comes round again, after a rest at the start, never shortened
		float loop = songWidth + MARQUEE_GAP;
		float cycle = MARQUEE_REST_SECONDS + loop / MARQUEE_SPEED;
		long cycleNanos = Math.max(1L, (long) (cycle * NANOS_PER_SECOND));
		float at = (System.nanoTime() % cycleNanos) / NANOS_PER_SECOND;
		float offset = at < MARQUEE_REST_SECONDS ? 0f : (at - MARQUEE_REST_SECONDS) * MARQUEE_SPEED;
		ui.graphics.enableScissor(Math.round(cursor), Math.round(top) - CLIP_ABOVE, Math.round(cursor + room2), Math.round(top) + CLIP_BELOW);
		try {
			drawSongText(ui, cursor - offset, top, alpha, color, shadow);
			drawSongText(ui, cursor - offset + loop, top, alpha, color, shadow);
		} finally {
			ui.graphics.disableScissor();
		}
	}

	private void drawSongText(Ui ui, float x, float top, float alpha, int color, boolean shadow) {
		if (shadow) {
			ui.textAtScaledBold(title, x + 0.5f, top + 0.7f, 1f, Ui.withOpacity(SHADOW, alpha));
		}
		ui.textAtScaledBold(title, x, top, 1f, Ui.withOpacity(color, alpha));
		if (!artist.isEmpty()) {
			ui.textAtScaled(artist, x + titleWidth + ARTIST_GAP, top, 1f, Ui.withOpacity(color, alpha * ARTIST_ALPHA));
		}
	}

	/** Measures the title and the artist after it, whole, once per song and size. */
	private void measureSong(Ui ui, MusicService.Track track) {
		float zoom = Ui.zoom();
		if (track == fittedTrack && zoom == fittedZoom) {
			return;
		}
		fittedTrack = track;
		fittedZoom = zoom;
		title = track.title();
		titleWidth = ui.boldWidthScaled(title, 1f);
		artist = track.artist();
		songWidth = titleWidth + (artist.isEmpty() ? 0f : ARTIST_GAP + ui.widthScaled(artist, 1f));
	}
}
