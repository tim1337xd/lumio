package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.features.music.CutoutWords.Piece;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;
import dev.tim1337.lumio.render.CutoutInk;
import dev.tim1337.lumio.render.CutoutSheets;
import dev.tim1337.lumio.render.LateSubmits;
import dev.tim1337.lumio.render.LyricOverlay;
import dev.tim1337.lumio.render.LyricSprites;
import dev.tim1337.lumio.render.SdfText;
import dev.tim1337.lumio.ui.Ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a line in the Cut-out preset of the lyrics in the world: its words one at a time, stacked downward in a small centered column like
 * paper cut-outs, each at its own size, straight and level, some on a flat tile, one with a symbol beside it.
 * <p>
 * The words of a line are packed into sprites of their own, each word alone with room around it ({@link CutoutWords#pack}), at a text size
 * larger than the lyrics' normal one ({@link #TEXT_SCALE}): a big word is drawn from letters about as large as it is on the screen, so its
 * edges stay crisp, and nothing of a neighbor reaches into its cut. Where a word stands is worked out once, when its sprites are ready, and
 * never again: a word has its place for as long as it shows ({@link CutoutStack}). Each frame only its strength and its way in and out are.
 */
final class CutoutLyrics {
	/** The column's width, in text units. */
	static final float COLUMN = 300f;
	/** A big word is this many times the size of the lyrics' normal text. */
	static final float BIG = 1.5f;
	/**
	 * The size of the text in the sprites of a Cut-out line, against the lyrics' normal size: as large as a row of a sprite has room for (with
	 * capitals, marks above them and what hangs below the baseline). A big word is then drawn about one to one at 4K and smaller at 1080p,
	 * never much enlarged.
	 */
	static final float TEXT_SCALE = 1.3f;
	/** The widest a row of such a sprite is, in text units at the lyrics' normal size. */
	private static final float ROW_MOST = (LyricSprites.MAX_WIDTH - 4f) / TEXT_SCALE;
	/** A word is cut from its row with this much of the gap beside it, in glyphs: its glow and shadow lie in there. */
	private static final float CUT_ROOM = 1.5f;
	/** A word's row is this tall at the normal text size, and its letters' middle lies this far above the baseline. */
	static final float ROW = 27f;
	static final float MIDDLE_ABOVE_BASELINE = 7f;
	/** The room between a word's drawn letters and the edge of its tile, on all four sides, for a word of full size; the soft edge of a tile. */
	static final float TILE_PAD = 6f;
	private static final float TILE_FEATHER = 0.7f;
	/** A tile's corners are rounded by this share of its height: clearly a box, never a pill. */
	static final float TILE_ROUND = 0.13f;
	/** How strong a tile and the glow of the word being sung are. */
	private static final float TILE_OPACITY = 1f;
	private static final float GLOW_OPACITY = 0.6f;
	/**
	 * Where the words need it to read ({@link LyricTones#shadow}), a word without a tile has a tight dark shadow a little below it, the word's
	 * own shape and never a wide blur: this far off in text units, and this far behind the word as the layers of a line count it.
	 */
	private static final float SHADOW_X = 0.5f;
	private static final float SHADOW_Y = 0.9f;
	private static final float SHADOW_DEPTH = -0.06f;
	/** A word comes in from this size and from this far below its place, and goes this far up, in text units: calm, never past its place. */
	private static final float IN_FROM = 0.94f;
	private static final float IN_BELOW = 3f;
	private static final float OUT_ABOVE = 4f;
	/** How much of the line's own words' strength an ad-lib keeps: it is dimmed that far toward what lies behind it. */
	private static final float QUIET = 0.62f;
	/** The draw order of letters on a tile: after everything of the default order, the tiles among it. */
	private static final int ON_TILE_ORDER = 1;
	/** How far behind the words the tiles lie, as the layers of a line count it. */
	private static final float TILE_DEPTH = -0.08f;
	private static final float LAYER_NEARER = 0.15f;
	/** A line whose sprites could not be made is tried again after this many frames; one not drawn for this many gives its sprites back. */
	private static final long RETRY_FRAMES = 20L;
	private static final long KEPT_FRAMES = 2L;

	/** @return the height of so many groups of usual size, in text units: the room a column is placed with where no free area sets it */
	static float height(int groups) {
		return groups * (ROW * BIG * (1f + CutoutWords.SMALL) * CutoutArrange.ROW_SPACING + CutoutArrange.GROUP_GAP);
	}

	/** A column's room holds at most this many groups of usual size, whatever the area. */
	static final int MOST_GROUPS = 12;
	/** The most lines a column shows at once, and those of a column still leaving beside them. */
	static final int MOST_LINES = 2 * MOST_GROUPS;

	/**
	 * Where the rows (groups) of a line stand in its column and when they come and go, as the column's plan has it: each row's top in text units
	 * from the top of the column's room (a page's rows from the page's top, a scrolling stack's from the stack's top), the height planned for it
	 * (a row that turns out taller is set smaller to fit), and the times it may come from, begins to go, and takes to go.
	 */
	record Part(float[] tops, float[] fit, long[] appear, long[] goneFrom, long[] goneFor) {
	}

	/** What the column plans a line by: how tall each of its rows is, and when each starts. */
	record Measure(float[] heights, long[] starts) {
	}

	/** One piece of a line: where it is cut from its sprite and where it stands in the column. */
	private static final class Placed extends CutoutArrange.Box {
		LyricSprites.Sprite sprite;
		int row;
		float from;
		float to;
		/** Drawn from distances: its text and how large an em is in its sprite units, its pen starting at 0 on the baseline at 0. */
		String text;
		float em;

		/** A word set smaller (its row of small words squeezed, its page fitted) is drawn smaller as well. */
		@Override
		void shrink(float by) {
			super.shrink(by);
			scale *= by;
		}
		/** The middle of its drawn letters in its sprite, and how much larger than in the sprite it is drawn. */
		float spriteX;
		float spriteY;
		float scale;
		/** For an ad-lib: whether it found a slot. */
		boolean shown;
	}

	/** A line as the preset shows it. */
	private static final class Layout {
		LyricLine line;
		int settings;

		/** Whether its letters and tiles are drawn from distances ({@link SdfText}), decided when it is made; else from sprites. */
		boolean sdf;
		/** What its rows are nudged by: its own words, or those of the line sung before that it is laid out as ({@link CutoutRepeats}). */
		int seed;
		List<Piece> pieces = List.of();
		CutoutWords.Cut[] cuts = new CutoutWords.Cut[0];
		List<String> texts = List.of();
		List<List<String>> sheets = List.of();
		LyricSprites.Sprite[] sprites = new LyricSprites.Sprite[0];
		/** Whether the words have their places: once, when all sprites are ready. */
		boolean placed;
		Placed[] words = new Placed[0];
		/** The line's ad-libs, a track of their own: when each shows, and where (in the column's own measure, from its top). */
		List<CutoutAdlibs.Shown> adlibs = List.of();
		Placed[] sides = new Placed[0];
		float[] groupTops = new float[0];
		CutoutStack.Timing timing;
		/** Where its rows stand and when they come and go in its column; null for a line on its own. */
		Part part;
		/** The time its words were last shown at, which never runs back by a little. */
		long shownAt = Long.MIN_VALUE;
		long frame;
		long triedAt = Long.MIN_VALUE / 2;
	}

	private final MusicFeature music;
	/** The layouts of the song's lines, so a line sung again looks as it did. */
	private final CutoutRepeats repeats = new CutoutRepeats();
	/** The lines that can show at once: those of the page or the stack, and those of a page going. */
	private final Layout[] layouts = new Layout[MOST_LINES];
	/** A layout only measured, never drawn: for the heights of the lines to come. */
	private final Layout measured = new Layout();
	/** The tones of the line being drawn, and those of it dimmed, as its background has them now. */
	private final int[] tones = new int[CutoutWords.Tone.values().length];
	private final int[] dimmed = new int[CutoutWords.Tone.values().length];

	CutoutLyrics(MusicFeature music) {
		this.music = music;
		for (int i = 0; i < layouts.length; i++) {
			layouts[i] = new Layout();
		}
	}

	/** Forgets the lines laid out and gives their sprites back, when the lyrics are off. */
	void clear() {
		for (Layout layout : layouts) {
			release(layout);
		}
	}

	/** Gives back the sprites of the lines not drawn lately, so their textures are free for the next lines. */
	void endFrame(long frame) {
		for (Layout layout : layouts) {
			if (layout.line != null && frame - layout.frame > KEPT_FRAMES) {
				release(layout);
			}
		}
	}

	private static void release(Layout layout) {
		for (LyricSprites.Sprite sprite : layout.sprites) {
			if (sprite != null) {
				sprite.release();
			}
		}
		layout.sprites = new LyricSprites.Sprite[0];
		layout.line = null;
		layout.placed = false;
	}

	private int settings() {
		return (music.cutoutSymbols.get() ? 2 : 0)
			| (music.adlibsShown() ? 1 << 10 : 0);
	}

	/** @return the line's layout, made when it is first asked for: its pieces and the rows of its sprites */
	private Layout layout(LyricLine line, long endMs, long frame) {
		int settings = settings();
		Layout free = layouts[0];
		for (Layout layout : layouts) {
			if (layout.line == line && layout.settings == settings) {
				return layout;
			}
			if (layout.line == null || layout.frame < free.frame && free.line != null) {
				free = layout;
			}
		}
		release(free);
		free.line = line;
		free.settings = settings;
		free.frame = frame;
		free.triedAt = Long.MIN_VALUE / 2;
		fill(free, line, endMs);
		if (free.sdf) {
			// drawn from distances: no sprites to make, every piece is its own text
			free.cuts = new CutoutWords.Cut[free.texts.size()];
			free.sprites = new LyricSprites.Sprite[0];
			return free;
		}
		free.cuts = CutoutWords.pack(free.texts, row -> LyricSprites.drawnWidth(row, true), ROW_MOST, LyricSprites.MAX_ROWS, free.sheets);
		free.sprites = new LyricSprites.Sprite[free.sheets.size()];
		return free;
	}

	/** Makes a line's pieces, ad-libs and texts, and decides whether it is drawn from distances. */
	private void fill(Layout free, LyricLine line, long endMs) {
		free.shownAt = Long.MIN_VALUE;
		free.part = null;
		// a line without word timings gets estimated ones, and is then shown as any other
		free.pieces = CutoutWords.pieces(line, line.words(), endMs, true, music.cutoutSymbols.get(),
			CutoutTiles.of(line, MusicService.lyrics(), endMs));
		// a line sung before (the same, or with a word or two changed) is laid out as it was then; the times stay its own
		List<String> plainWords = new ArrayList<>(free.pieces.size());
		for (Piece piece : free.pieces) {
			plainWords.add(CutoutLexicon.plain(text(line, piece)).toLowerCase(java.util.Locale.ROOT));
		}
		CutoutRepeats.Laid laid = repeats.lay(MusicService.lyrics(), line.text(), plainWords, free.pieces);
		free.pieces = laid.pieces();
		free.seed = laid.seed();
		free.adlibs = music.adlibsShown() ? CutoutAdlibs.track(line.backgrounds(), line.timeMs(), endMs) : List.of();
		List<String> texts = new ArrayList<>(free.pieces.size() + free.adlibs.size());
		for (Piece piece : free.pieces) {
			texts.add(text(line, piece));
		}
		// the ad-libs are cut from the same sprites, after the line's words
		for (CutoutAdlibs.Shown adlib : free.adlibs) {
			texts.add(CutoutAdlibs.text(line.backgrounds().get(adlib.source()).text()));
		}
		free.sdf = SdfText.ready();
		for (String each : texts) {
			free.sdf &= SdfText.covers(each);
		}
		free.sheets = new ArrayList<>();
		free.texts = texts;
	}

	/**
	 * @return how tall each row of a line stands in a column and when it starts, measured by the font's own shapes as the letters drawn from
	 *         distances are: what a column plans with before the line's sprites exist. A row drawn from sprites is set to this height where it is
	 *         taller.
	 */
	Measure measure(LyricLine line, long endMs) {
		Layout layout = measured;
		layout.line = line;
		fill(layout, line, endMs);
		layout.sdf = true;
		layout.cuts = new CutoutWords.Cut[layout.texts.size()];
		float[] heights = layout.pieces.isEmpty() ? new float[0] : arrange(layout, new Placed[layout.pieces.size()]);
		long[] starts = new long[heights.length];
		for (int group = 0; group < starts.length; group++) {
			starts[group] = CutoutStack.groupStart(layout.pieces, group);
		}
		layout.line = null;
		return new Measure(heights, starts);
	}

	/** @return the text of a piece: a stretch of the line's text */
	private static String text(LyricLine line, Piece piece) {
		String source = line.text();
		return source.substring(Math.clamp(piece.from(), 0, source.length()), Math.clamp(piece.to(), 0, source.length()));
	}

	/** @return whether the line's sprites are all ready; they are begun here, and begun again after a while when one could not be made */
	private boolean ready(Layout layout, long frame) {
		if (layout.sdf) {
			return SdfText.ready();
		}
		boolean ready = true;
		for (int i = 0; i < layout.sprites.length; i++) {
			LyricSprites.Sprite sprite = layout.sprites[i];
			if (sprite != null && sprite.state() == LyricSprites.State.FAILED) {
				sprite.release();
				sprite = null;
				layout.sprites[i] = null;
			}
			if (sprite == null && frame - layout.triedAt >= RETRY_FRAMES) {
				sprite = LyricSprites.begin(layout.sheets.get(i), false, false, TEXT_SCALE);
				layout.sprites[i] = sprite;
				if (sprite == null) {
					layout.triedAt = frame;
				}
			}
			ready &= sprite != null && sprite.state() == LyricSprites.State.READY;
		}
		return ready;
	}

	/**
	 * Gives every piece its place: once, from the sprites as they are. In a column the rows stand where its plan puts them; a line on its own is
	 * one page, as tall as its rows.
	 */
	private void place(Layout layout) {
		List<Piece> pieces = layout.pieces;
		Placed[] words = new Placed[pieces.size()];
		float[] heights = arrange(layout, words);
		Part part = layout.part;
		if (part != null && part.tops().length == heights.length) {
			for (int group = 0; group < heights.length; group++) {
				if (part.fit()[group] > 0f && heights[group] > part.fit()[group]) {
					fitRow(pieces, words, group, part.fit()[group] / heights[group]);
				}
			}
			layout.groupTops = part.tops();
			layout.timing = timing(pieces, part);
		} else {
			int kept = Math.max(1, heights.length);
			layout.groupTops = CutoutStack.tops(heights, kept);
			layout.timing = CutoutStack.timing(pieces, kept);
		}
		layout.words = words;
		placeAdlibs(layout, LyricSprites.cleanSize());
		layout.placed = true;
	}

	/** Measures and places the words of a line in its groups. @return the height of each group, with the room under it */
	private float[] arrange(Layout layout, Placed[] words) {
		List<Piece> pieces = layout.pieces;
		float font = LyricSprites.cleanSize();
		int seed = layout.seed;
		for (int i = 0; i < words.length; i++) {
			Piece piece = pieces.get(i);
			Placed word = new Placed();
			float[] drawn = cut(layout, layout.cuts[i], text(layout.line, piece), word, font);
			float left = drawn[0];
			float top = drawn[1];
			float right = drawn[2];
			float bottom = drawn[3];
			float middle = drawn[4];
			boolean symbol = piece.symbol() != CutoutLexicon.NO_SYMBOL;
			// only the word chosen for it has a tile: a small word stays a small free word
			// the key word keeps its tile's room from the start, so nothing moves when the tile stamps in
			word.onTile = piece.tile();
			word.pale = piece.pale();
			float natural = (right - left) / TEXT_SCALE * BIG;
			float room = COLUMN - (symbol ? ROW * BIG * CutoutArrange.SYMBOL_SIZE + CutoutArrange.SYMBOL_GAP : 0f) - (word.onTile ? 2f * TILE_PAD : 0f);
			word.size = CutoutWords.fitted(piece.size(), natural, room);
			word.scale = BIG * word.size / TEXT_SCALE;
			word.width = (right - left) * word.scale;
			word.tall = ROW * BIG * word.size;
			word.spriteX = (left + right) / 2f;
			word.spriteY = middle;
			// the tile, around the drawn letters with the same room on all four sides, from the word's own middle
			word.ink = new float[] {-word.width / 2f, (top - middle) * word.scale, word.width / 2f, (bottom - middle) * word.scale};
			word.tile = CutoutWords.tile(word.ink, TILE_PAD * word.size);
			words[i] = word;
		}
		return CutoutArrange.column(pieces, words, COLUMN, TILE_PAD, seed);
	}

	/** A row taller than its column planned for it (its letters from sprites measure a little otherwise) is set smaller as a whole, around its top. */
	private static void fitRow(List<Piece> pieces, Placed[] words, int group, float by) {
		for (int i = 0; i < words.length; i++) {
			if (pieces.get(i).group() == group) {
				Placed word = words[i];
				word.shrink(by);
				word.x *= by;
				word.y *= by;
				word.symbolX *= by;
				word.symbolHalf *= by;
				word.pivot *= by;
			}
		}
	}

	/** @return when each piece comes and goes, by the times its row has in the column's plan */
	private static CutoutStack.Timing timing(List<Piece> pieces, Part part) {
		int count = pieces.size();
		long[] appear = new long[count];
		long[] goneFrom = new long[count];
		long[] goneFor = new long[count];
		for (int i = 0; i < count; i++) {
			Piece piece = pieces.get(i);
			int group = piece.group();
			appear[i] = part.appear()[group] == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(piece.startMs(), part.appear()[group]);
			goneFrom[i] = part.goneFrom()[group];
			goneFor[i] = part.goneFor()[group];
		}
		return new CutoutStack.Timing(appear, goneFrom, goneFor);
	}

	/**
	 * Says where a text is cut from its sprite, and measures its drawn letters there.
	 *
	 * @return the edges of the drawn letters in the sprite (left, top, right, bottom) and the height of their middle
	 */
	private static float[] cut(Layout layout, CutoutWords.Cut cut, String text, Placed word, float font) {
		if (layout.sdf) {
			// drawn from distances: the same measures as in a sprite, with the pen at 0 on the baseline at 0
			float em = font * TEXT_SCALE;
			float[] ink = CutoutInk.bounds(text);
			word.text = text;
			word.em = em;
			float[] drawn = ink != null ? new float[] {ink[0] * em, ink[1] * em, ink[2] * em, ink[3] * em}
				: new float[] {0f, -0.73f * em, 0.5f * em * text.length(), 0f};
			return new float[] {drawn[0], drawn[1], drawn[2], drawn[3], -MIDDLE_ABOVE_BASELINE * TEXT_SCALE};
		}
		word.sprite = layout.sprites[cut.sheet()];
		word.row = cut.row();
		word.from = cut.from();
		word.to = cut.to();
		float penLeft = word.sprite.charX(cut.row(), cut.from());
		float penRight = word.sprite.charX(cut.row(), cut.to());
		float baseline = word.sprite.rowBaseline(cut.row());
		// the drawn letters, in the sprite: from the font's own measure, at the size the sprite set this row in
		float[] ink = CutoutInk.bounds(text);
		float left = penLeft;
		float right = penRight;
		float top = baseline - font * 0.73f * TEXT_SCALE;
		float bottom = baseline;
		if (ink != null && ink[4] > 0f) {
			float set = (penRight - penLeft) / ink[4];
			left = penLeft + ink[0] * set;
			right = penLeft + ink[2] * set;
			top = baseline + ink[1] * set;
			bottom = baseline + ink[3] * set;
		}
		return new float[] {left, top, right, bottom, baseline - MIDDLE_ABOVE_BASELINE * TEXT_SCALE};
	}

	/** A block is this many times its words' width where ad-libs show: the room they have beside the words. */
	static final float ADLIB_ROOM = 1.25f;
	/** How far from the middle of the column an ad-lib may reach, to either side: half the block's width, never out of the block. */
	static final float ADLIB_REACH = COLUMN * ADLIB_ROOM / 2f;

	/**
	 * Gives every ad-lib its place, once: beside the row that is being sung when it starts or on a row of its own under it, clear of every word of
	 * the line and of every ad-lib that shows while it does. The line's words are placed before and know nothing of the ad-libs.
	 */
	private void placeAdlibs(Layout layout, float font) {
		List<Piece> pieces = layout.pieces;
		Placed[] sides = new Placed[layout.adlibs.size()];
		List<float[]> boxes = new ArrayList<>();
		for (int k = 0; k < sides.length; k++) {
			CutoutAdlibs.Shown adlib = layout.adlibs.get(k);
			Placed side = new Placed();
			float[] drawn = cut(layout, layout.cuts[pieces.size() + k], CutoutAdlibs.text(layout.line.backgrounds().get(adlib.source()).text()), side, font);
			side.size = CutoutWords.ADLIB;
			side.scale = BIG * side.size / TEXT_SCALE;
			side.width = (drawn[2] - drawn[0]) * side.scale;
			side.tall = ROW * BIG * side.size;
			side.spriteX = (drawn[0] + drawn[2]) / 2f;
			side.spriteY = drawn[4];
			// what it must keep clear of: every word of the line with its tile and symbol, also those that only appear later (the ad-lib may
			// still show then), and the ad-libs placed before it that show while it does
			boxes.clear();
			int sungGroup = -1;
			float lineBottom = 0f;
			for (int i = 0; i < pieces.size(); i++) {
				if (pieces.get(i).startMs() <= adlib.startMs()) {
					sungGroup = Math.max(sungGroup, pieces.get(i).group());
				}
				float[] word = box(layout, i);
				lineBottom = Math.max(lineBottom, word[3]);
				boxes.add(word);
			}
			sungGroup = Math.max(0, sungGroup);
			// the row being sung: its height and where it ends; before the first word, the first row
			float middleY = ROW * BIG / 2f;
			float rowBottom = pieces.isEmpty() ? 0f : -Float.MAX_VALUE;
			for (int i = 0; i < pieces.size(); i++) {
				if (pieces.get(i).group() == sungGroup) {
					middleY = layout.groupTops[sungGroup] + layout.words[i].y;
					rowBottom = Math.max(rowBottom, boxes.get(i)[3]);
				}
			}
			for (int before = 0; before < k; before++) {
				if (sides[before].shown && layout.adlibs.get(before).goneMs() > adlib.startMs()) {
					Placed other = sides[before];
					float halfTall = other.tall / 2f;
					boxes.add(new float[] {other.x - other.width / 2f, other.y - halfTall, other.x + other.width / 2f, other.y + halfTall});
				}
			}
			String said = layout.line.backgrounds().get(adlib.source()).text();
			float[] slot = CutoutAdlibs.slot(boxes, middleY, Math.max(rowBottom, 0f), lineBottom, side.width / 2f, side.tall / 2f, ADLIB_REACH, CutoutAdlibs.words(said));
			if (slot != null) {
				side.shown = true;
				side.x = slot[0];
				side.y = slot[1];
				side.shrink(slot[2]);
			}
			sides[k] = side;
		}
		layout.sides = sides;
	}

	/** @return the box a word of the line takes in the column, from the column's top: its letters or its tile and its symbol */
	private static float[] box(Layout layout, int index) {
		Piece piece = layout.pieces.get(index);
		float[] box = CutoutArrange.box(layout.words[index], piece);
		box[1] += layout.groupTops[piece.group()];
		box[3] += layout.groupTops[piece.group()];
		return box;
	}

	/**
	 * Submits a line. The pose is the line's own: its middle at the origin, in text units, down positive.
	 *
	 * @param endMs    when the line ends
	 * @param at       the time of the lyrics
	 * @param alpha    how visible the line is as a whole: its way in and out
	 * @param frame    the frame's number
	 * @param part     where its rows stand and when they come and go in its column, or null for a line on its own
	 * @param top      where the tops of its rows are counted from, in text units down from the pose's origin
	 * @param fadeTop  where the column's room begins, in the same measure: a word above it is gone, one less than {@code fadeZone} below it
	 *                 fades (a scrolling column); {@code fadeZone} 0 for none
	 * @param previous how far the line has been sung, from 0 (being sung) to 1: it then dims toward what lies behind it, all its words alike,
	 *                 where it stands (it never moves or shrinks)
	 * @param backdrop what lies behind the line, which its tones are chosen by
	 * @return whether the line was drawn: not while its sprites are being made
	 */
	boolean draw(SubmitNodeCollector collector, PoseStack pose, LyricLine line, long endMs, long at, float alpha, boolean animated, long frame, Part part,
				 float top, float fadeTop, float fadeZone, float previous, LyricBackdrop backdrop) {
		if (!CutoutSheets.available()) {
			return false;
		}
		Layout layout = layout(line, endMs, frame);
		layout.frame = frame;
		if (layout.pieces.isEmpty() && layout.adlibs.isEmpty() || !ready(layout, frame)) {
			return false;
		}
		if (!layout.placed || layout.part != part) {
			layout.part = part;
			place(layout);
		}
		if (alpha <= 0.004f) {
			return true;
		}
		List<Piece> pieces = layout.pieces;
		layout.shownAt = layout.shownAt == Long.MIN_VALUE ? at : CutoutStack.steady(layout.shownAt, at);
		long now = layout.shownAt;
		backdrop.cutout(music.cutoutBase(), music.cutoutSecond(), tones, dimmed);
		boolean pulsing = animated;
		MusicFeature.WorldGlow glowMode = music.worldGlow.get();
		boolean keyBox = music.cutoutKeyBox.get();
		for (int i = 0; i < layout.words.length; i++) {
			Placed word = layout.words[i];
			Piece piece = pieces.get(i);
			float scroll = scrolled(top + layout.groupTops[piece.group()] + word.y, fadeTop, fadeZone);
			// how far the word is there (its way in and out), and how far it is dimmed: an older word of the line, a quiet one, a line already sung
			float shown = CutoutStack.shown(i, now, layout.timing) * alpha * scroll;
			float dim = dimShare(CutoutStack.rest(pieces, i, now), piece.quiet());
			if (previous > 0.004f) {
				float whole = (1f - CutoutStack.gone(i, now, layout.timing)) * alpha * scroll;
				shown += (whole - shown) * previous;
				dim += (1f - dim) * previous;
			}
			if (shown <= 0.004f) {
				continue;
			}
			boolean sung = CutoutStack.sung(pieces, i, now) && !piece.quiet() && previous <= 0.004f;
			float beat = sung && pulsing ? LyricPulse.at(now - piece.startMs(), piece.endMs() - piece.startMs()) : 0f;
			// its way in and out, the only times it moves: in from a little below and a little smaller, out a little upward
			float in = animated ? CutoutStack.in(i, now, layout.timing) : 1f;
			float gone = animated ? CutoutStack.gone(i, now, layout.timing) : 0f;
			float grow = (IN_FROM + (1f - IN_FROM) * in) * (1f + beat * LyricPulse.GROW);
			pose.pushPose();
			pose.translate(word.x, top + layout.groupTops[piece.group()] + word.y + (1f - in) * IN_BELOW - gone * OUT_ABOVE, 0f);
			// the word and its tile are one piece: one size
			pose.scale(grow, grow, 1f);
			// only the key word has a tile: it stamps in when the word is sung (line-synced: at its estimated time), then stays with its line,
			// dimming with it once sung, and goes only with its way out; the word being sung shows by its brightness and pulse, never by a box
			float tiled = CutoutStamp.boxed(keyBox, word.onTile) ? CutoutStamp.tile(CutoutStamp.strength(now, piece.startMs()), CutoutStack.shown(i, now, layout.timing)) : 0f;
			float stamp = animated ? CutoutStamp.scale(CutoutStamp.progress(now, piece.startMs())) : 1f;
			float lettersOn = CutoutStamp.letters(tiled);
			boolean onTile = lettersOn > 0.5f;
			CutoutWords.Tone tileTone = word.pale ? CutoutWords.Tone.PALE_TILE : CutoutWords.Tone.DARK_TILE;
			if (tiled > 0.004f) {
				int color = Ui.withOpacity(tone(tileTone, dim), TILE_OPACITY * alpha * scroll * tiled);
				float[] tile = word.tile;
				// a plain filled rectangle, with no edge around it: its tone alone tells it from what lies behind
				pose.pushPose();
				pose.last().pose().scaleLocal(1f - TILE_DEPTH * LAYER_NEARER);
				float radius = (tile[3] - tile[1]) * TILE_ROUND;
				// the stamp: a little larger at first, settling on the word
				float middleX = (tile[0] + tile[2]) / 2f;
				float middleY = (tile[1] + tile[3]) / 2f;
				pose.translate(middleX, middleY, 0f);
				pose.scale(stamp, stamp, 1f);
				pose.translate(-middleX, -middleY, 0f);
				if (layout.sdf) {
					submit(collector, pose, SdfText.shapes(), true, (p, consumer) -> SdfText.roundedRect(consumer, p, tile[0], tile[1], tile[2], tile[3], radius, color));
				} else {
					submit(collector, pose, CutoutSheets.tiles(), true,
						(p, consumer) -> CutoutSheets.roundedTile(consumer, p, tile[0], tile[1], tile[2], tile[3], radius, TILE_FEATHER, color));
				}
				pose.popPose();
			}
			// the word being sung is the strongest: white on a dark background, the full deep tone on a bright one
			// without the key word box the key word, once sung, keeps the sung word's brightness instead (its tile's room stays, nothing moves)
			boolean bright = sung || !keyBox && word.onTile && CutoutStamp.placed(now, piece.startMs());
			int free = bright ? tone(CutoutWords.Tone.SUNG, dim) : tone(piece.small() ? CutoutWords.Tone.SMALL : CutoutWords.Tone.WORD, dim);
			int letters = tone(word.pale ? CutoutWords.Tone.ON_PALE : CutoutWords.Tone.ON_DARK, dim);
			int text = Ui.withOpacity(lettersOn <= 0f ? free : Ui.mix(free, letters, lettersOn),
				// on a tile a word's letters keep more than the tile does as it comes and goes: both see-through, they would sink into it
				Math.min(1f, onTile ? (float) Math.sqrt(shown) : shown));
			// a glow only on a dark background, where light stands out (its tone is see-through on a bright one)
			// the sung word glows; with Whole text every word does, more softly, and a line already sung more softly still
			float glowShare = LyricGlow.of(glowMode, sung ? 1f : 0f, dim);
			int glow = glowShare > 0f ? Ui.withOpacity(tones[CutoutWords.Tone.GLOW.ordinal()], Math.min(1f, GLOW_OPACITY * shown * glowShare * (1f + beat * LyricPulse.LIGHT))) : 0;
			// a tight shadow only where the words need it to read against what lies behind them
			int shadow = onTile ? 0 : Ui.withOpacity(tone(CutoutWords.Tone.SHADOW, dim), shown);
			pose.pushPose();
			pose.scale(word.scale, word.scale, 1f);
			pose.translate(-word.spriteX, -word.spriteY, 0f);
			if ((shadow >>> 24) != 0) {
				pose.pushPose();
				pose.translate(SHADOW_X / word.scale, SHADOW_Y / word.scale, 0f);
				// the word's own letters in the dark, behind it: crisp, where the glow's picture would be a smear
				pose.last().pose().scaleLocal(1f - SHADOW_DEPTH * LAYER_NEARER);
				letters(collector, pose, word, LyricSprites.Sprite.Layer.TEXT, false, shadow, 0);
				pose.popPose();
			}
			if ((glow >>> 24) != 0) {
				letters(collector, pose, word, LyricSprites.Sprite.Layer.SUNG_GLOW, false, glow, 0);
			}
			// on a tile the letters are flat: writing no depth, and drawn after the tile in an order of their own. Letters that write depth
			// would keep the tile out with their soft edges (a thin rim of sky around every letter); and in the same order as the tile the
			// game may sort a letter before it where the word is seen at an angle (in the cinematic camera), and the tile covers it.
			letters(collector, pose, word, LyricSprites.Sprite.Layer.TEXT, onTile, text, onTile ? ON_TILE_ORDER : 0);
			pose.popPose();
			if (piece.symbol() != CutoutLexicon.NO_SYMBOL) {
				int icon = piece.symbol();
				int color = Ui.withOpacity(tone(CutoutWords.Tone.SYMBOL, dim), shown);
				float half = word.symbolHalf;
				float iconX = word.symbolX - word.x;
				submit(collector, pose, layout.sdf ? SdfText.icons() : CutoutSheets.icons(), false,
					(p, consumer) -> CutoutSheets.icon(consumer, p, icon, iconX, 0f, half, color));
			}
			pose.popPose();
		}
		// the ad-libs: beside the words, each for its own time, in the color of a word that stands free, quieter
		float quiet = dimShare(1f, true);
		for (int k = 0; k < layout.sides.length; k++) {
			Placed side = layout.sides[k];
			float strength = side.shown ? CutoutAdlibs.strength(layout.adlibs.get(k), now) * alpha * scrolled(top + side.y, fadeTop, fadeZone) : 0f;
			if (strength <= 0.004f) {
				continue;
			}
			float dim = quiet + (1f - quiet) * previous;
			int text = Ui.withOpacity(tone(CutoutWords.Tone.WORD, dim), Math.min(1f, strength));
			int shadow = Ui.withOpacity(tone(CutoutWords.Tone.SHADOW, dim), strength);
			pose.pushPose();
			pose.translate(side.x, top + side.y, 0f);
			pose.scale(side.scale, side.scale, 1f);
			pose.translate(-side.spriteX, -side.spriteY, 0f);
			if ((shadow >>> 24) != 0) {
				pose.pushPose();
				pose.translate(SHADOW_X / side.scale, SHADOW_Y / side.scale, 0f);
				pose.last().pose().scaleLocal(1f - SHADOW_DEPTH * LAYER_NEARER);
				letters(collector, pose, side, LyricSprites.Sprite.Layer.TEXT, false, shadow, 0);
				pose.popPose();
			}
			letters(collector, pose, side, LyricSprites.Sprite.Layer.TEXT, false, text, 0);
			pose.popPose();
		}
		return true;
	}

	/**
	 * @param rest  how much of its strength a word keeps as it grows older in its line ({@link CutoutStack#rest})
	 * @param quiet whether it is an ad-lib, quieter than the line's own words
	 * @return how far the word's tones are moved toward their dimmed ones: 0 none, 1 as far as a line already sung
	 */
	static float dimShare(float rest, boolean quiet) {
		float kept = rest * (quiet ? QUIET : 1f);
		return Math.clamp((1f - kept) / (1f - LyricTones.DIM_KEEPS), 0f, 1f);
	}

	/** @return a tone of this frame, as far toward its dimmed one as asked */
	private int tone(CutoutWords.Tone tone, float dim) {
		int full = tones[tone.ordinal()];
		return dim <= 0f ? full : Ui.mix(full, dimmed[tone.ordinal()], dim);
	}

	/** @return how much of a word a scrolling column still shows at this height: gone above its room's top, fading in the band below it */
	static float scrolled(float y, float fadeTop, float fadeZone) {
		if (fadeZone <= 0f) {
			return 1f;
		}
		float t = Math.clamp((y - fadeTop) / fadeZone, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/**
	 * Submits a word's letters, in the pose of its sprite units: its letters, their glow ({@link LyricSprites.Sprite.Layer#SUNG_GLOW}), or flat
	 * letters on a tile, from distances or from its sprite.
	 *
	 * @param order the draw order: letters on a tile after the tile
	 */
	private static void letters(SubmitNodeCollector collector, PoseStack pose, Placed word, LyricSprites.Sprite.Layer layer, boolean flat, int color, int order) {
		pose.pushPose();
		pose.last().pose().scaleLocal(1f - layer.depth * LAYER_NEARER);
		RenderType type;
		SubmitNodeCollector.CustomGeometryRenderer renderer;
		if (word.sprite == null) {
			String text = word.text;
			float em = word.em;
			type = layer.isGlow() ? SdfText.glow() : flat ? SdfText.textFlat() : SdfText.text();
			renderer = (p, consumer) -> SdfText.emit(consumer, p, text, 0f, 0f, em, color);
		} else {
			LyricSprites.Sprite sprite = word.sprite;
			int row = word.row;
			float from = word.from - CUT_ROOM;
			float to = word.to + CUT_ROOM;
			type = flat ? sprite.flatType() : sprite.renderType(layer);
			renderer = (p, consumer) -> sprite.emitRange(p, consumer, row, from, to, layer, color);
		}
		if (order != 0) {
			if (LyricOverlay.add(pose, type, renderer)) {
				pose.popPose();
				return;
			}
			if (LateSubmits.wanted()) {
				LateSubmits.add(pose, type, renderer, order);
			} else {
				collector.order(order).submitCustomGeometry(pose, type, renderer);
			}
		} else {
			submit(collector, pose, type, layer.isGlow() || flat, renderer);
		}
		pose.popPose();
	}

	/** Submits a piece; what writes no depth (a glow, a tile) is drawn after the see-through blocks, so glass or water behind it does not cover it. */
	private static void submit(SubmitNodeCollector collector, PoseStack pose, RenderType type, boolean soft, SubmitNodeCollector.CustomGeometryRenderer renderer) {
		if (LyricOverlay.add(pose, type, renderer)) {
			return;
		}
		if (DebugHooks.ACTIVE) {
			// while the debug probes listen, the time the geometry takes is counted with that of the Classic look
			SubmitNodeCollector.CustomGeometryRenderer plain = renderer;
			renderer = (p, consumer) -> {
				long start = System.nanoTime();
				plain.render(p, consumer);
				LyricSprites.emitNanos += System.nanoTime() - start;
			};
		}
		if (soft && LateSubmits.wanted()) {
			LateSubmits.add(pose, type, renderer);
		} else {
			collector.submitCustomGeometry(pose, type, renderer);
		}
	}
}
