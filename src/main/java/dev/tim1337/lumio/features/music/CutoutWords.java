package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A lyric line as the Cut-out preset shows it: its words as pieces, one after the other, each with its own size and maybe a tile and a symbol,
 * stacked downward in groups. A group is one big word with the small words sung before it in a short row above it; the newest groups
 * stay, the older ones go (see {@link CutoutStack}). Everything follows from the line alone, so the same line always looks the same. Plain
 * logic, tested without the game.
 */
final class CutoutWords {
	/** Where the colors of the words and tiles come from. */
	enum Colors {
		COVER("Cover"), ACCENT("Accent");

		private final String label;

		Colors(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}
	}

	/** The sizes, as shares of a big word's: a small function word, an ad-lib in brackets, a word when all are the same, the least and most. */
	static final float SMALL = 0.68f;
	static final float ADLIB = 0.68f;
	static final float EVEN = 0.8f;
	static final float LEAST = SMALL;
	static final float MOST = 1.26f;
	/** A main word held at least this long plus half of this span is one of the large ones. */
	static final long HELD_FROM_MS = 600L;
	static final long HELD_SPAN_MS = 900L;
	/** With the key word emphasized: the key word's size. */
	static final float KEY_BIG = 1.26f;
	/** No word found yet. */
	private static final int NO_PIECE = -1;

	/** The line's own text, as the source of a piece: the only one, since ad-libs are a track of their own. */
	static final int LINE = 0;

	/**
	 * One word of the line or of one of its ad-libs.
	 *
	 * @param source  the text it is from: {@link #LINE}
	 * @param from    where it starts and ends in that text
	 * @param startMs when it appears, and {@code endMs} when it has been sung
	 * @param size    its size, as a share of a big word's
	 * @param small   whether it is a small word, which sits in the short row above its group's big word
	 * @param quiet   whether it is an ad-lib (background vocals, words in brackets), in a quieter tone
	 * @param tile    whether it lies on a tile; with {@code pale} on a pale one with dark letters, else on a dark one with light letters
	 * @param symbol  the symbol beside it, or {@link CutoutLexicon#NO_SYMBOL}
	 * @param group   the group it belongs to, counted from the line's first
	 * @param key     whether it is the line's key word: the one held longest of those that are no function words
	 */
	record Piece(int source, int from, int to, long startMs, long endMs, float size, boolean small, boolean quiet, boolean tile, boolean pale,
				 int symbol, int group, boolean key) {
	}

	/** A word on its way to being a piece: where it is in the line and when it is sung. */
	private record Sung(int from, int to, long startMs, long endMs, String text) {
	}

	/** @return where a word ends as it is drawn: without a comma, period, semicolon or colon at its end; a question or exclamation mark stays */
	static int drawnEnd(String text, int from, int to) {
		int end = to;
		while (end > from + 1 && ",.;:".indexOf(text.charAt(end - 1)) >= 0) {
			end--;
		}
		return end;
	}

	private CutoutWords() {
	}

	/**
	 * @param line         the line
	 * @param words        its words with their times; empty for a line without any (line-synced lyrics), whose words are then given
	 *                     estimated times ({@link LyricEstimate}) and shown exactly as timed ones are
	 * @param endMs        when the line ends
	 * @param byImportance whether words get their size by importance; all the same without
	 * @param symbols      whether words get symbols
	 * @return the pieces of the line, in the order they appear. The line's ad-libs (background vocals) are no part of them: they are a track of
	 *         their own ({@link CutoutAdlibs}), so the line's words stand and come exactly the same with ad-libs and without
	 */
	static List<Piece> pieces(LyricLine line, List<Word> words, long endMs, boolean byImportance, boolean symbols) {
		return pieces(line, words, endMs, byImportance, symbols, CutoutTiles.alone(line, endMs));
	}

	/**
	 * Same, with the line's key word chosen over the whole song ({@link CutoutTiles#plan}). The emphasis follows the lyrics: a line without
	 * word timings (line-synced, its times estimated) has its key word big and on the tile, the other main words graded smaller; a line with
	 * word timings keeps its words' sizes as they are. Either kind has one tile at most, on its key word when the plan finds it strong enough
	 * ({@link CutoutStamp}).
	 */
	static List<Piece> pieces(LyricLine line, List<Word> words, long endMs, boolean byImportance, boolean symbols, CutoutTiles.Choice choice) {
		String text = line.text();
		List<Sung> sung = new ArrayList<>();
		boolean estimated = !timed(words);
		for (Word word : estimated ? LyricEstimate.words(text, line.timeMs(), endMs == Long.MAX_VALUE ? Long.MIN_VALUE : endMs) : words) {
			int from = Math.clamp(word.from(), 0, text.length());
			int to = drawnEnd(text, from, Math.clamp(word.to(), from, text.length()));
			sung.add(new Sung(from, to, word.startMs(), word.endMs(), text.substring(from, to)));
		}
		List<String> own = new ArrayList<>(sung.size());
		for (Sung word : sung) {
			own.add(word.text());
		}
		boolean known = CutoutLexicon.known(own);
		int count = sung.size();
		boolean[] small = new boolean[count];
		boolean[] quiet = new boolean[count];
		boolean[] filler = new boolean[count];
		boolean inBrackets = false;
		for (int i = 0; i < count; i++) {
			Sung word = sung.get(i);
			// words in brackets that were left in the line (with their detection off) are sung in the background: small and quiet
			inBrackets |= word.text().indexOf('(') >= 0;
			quiet[i] = inBrackets;
			inBrackets &= word.text().indexOf(')') < 0;
			filler[i] = !quiet[i] && CutoutLexicon.small(word.text(), known);
			small[i] = quiet[i] || byImportance && filler[i];
		}
		// a censored word ("****") says nothing of itself: it has the size of the word beside it
		for (int i = 0; i < count; i++) {
			if (!quiet[i] && CutoutLexicon.censored(sung.get(i).text())) {
				filler[i] = i > 0 ? filler[i - 1] : count > 1 && filler[1] && !CutoutLexicon.censored(sung.get(1).text());
				small[i] = byImportance && filler[i];
			}
		}
		if (byImportance) {
			promote(sung, small, quiet);
		}
		// which words are set smaller: only fillers and ad-libs; a short word that joins a row below stays as large as it is
		boolean[] lesser = small.clone();
		// the key word and the tile: chosen by their score (CutoutTiles), over the song where it is known
		int key = keyOf(sung, small, choice);
		int[] group = rows(sung, small, quiet, key);
		// a sung line is a block of at most three rows: more are joined, between phrases first
		block(text, sung, small, group, MOST_ROWS);
		key = keyOf(sung, small, choice);
		// line-synced: the key word is the big one; word-synced: the words keep their sizes
		boolean keyWord = estimated && byImportance;
		// the tile goes to the line's key word only, when it is strong enough (the song's plan says so), for word-synced and line-synced lines alike
		int tiled = NO_PIECE;
		for (int i = 0; i < count && choice.tile() != CutoutTiles.NONE; i++) {
			tiled = sung.get(i).from() == choice.tile() && !small[i] && !filler[i] && !CutoutLexicon.rude(sung.get(i).text()) ? i : tiled;
		}
		// two words that stand for one thing ("private jet") give their symbol to the main word of the two
		int[] symbolOf = new int[count];
		for (int i = 0; i < count; i++) {
			symbolOf[i] = symbols ? CutoutLexicon.symbol(sung.get(i).text()) : CutoutLexicon.NO_SYMBOL;
		}
		for (int i = 0; symbols && i + 1 < count; i++) {
			int both = CutoutLexicon.phrase(sung.get(i).text(), sung.get(i + 1).text());
			if (both != CutoutLexicon.NO_SYMBOL) {
				symbolOf[i] = both;
				symbolOf[i + 1] = both;
			}
		}
		// a row's main word is its first full-sized word that has a symbol, else its last full-sized word: only it has a symbol beside it, which the
		// row keeps room for
		int[] mainOf = new int[count == 0 ? 0 : group[count - 1] + 1];
		Arrays.fill(mainOf, NO_PIECE);
		for (int i = 0; i < count; i++) {
			if (!small[i] && (mainOf[group[i]] == NO_PIECE || symbolOf[mainOf[group[i]]] == CutoutLexicon.NO_SYMBOL)) {
				mainOf[group[i]] = i;
			}
		}
		List<Piece> pieces = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Sung word = sung.get(i);
			long length = Math.max(0L, word.endMs() - word.startMs());
			// three sizes at most, so a page reads as one composition: small words, the main words, and the key word (or one held long) largest
			float size;
			if (!byImportance) {
				size = quiet[i] ? SMALL : EVEN;
			} else if (lesser[i]) {
				size = SMALL;
			} else if (keyWord) {
				size = i == key ? KEY_BIG : REGULAR;
			} else {
				boolean heldLong = !estimated && length >= HELD_FROM_MS + HELD_SPAN_MS / 2;
				size = i == key || heldLong && !filler[i] && !CutoutLexicon.rude(word.text()) ? LARGE : REGULAR;
			}
			// one tile in a line at most, on the word chosen for it, never on a short word or a function word
			boolean tile = i == tiled;
			pieces.add(new Piece(LINE, word.from(), word.to(), word.startMs(), word.endMs(), size, lesser[i], quiet[i], tile,
				tile && choice.pale(), mainOf[group[i]] == i ? symbolOf[i] : CutoutLexicon.NO_SYMBOL, group[i], i == key));
		}
		return oneSymbol(pieces);
	}

	/** @return whether a line has word timings of its own (word-synced lyrics); without, its words get estimated times */
	static boolean timed(List<Word> words) {
		return !(words.isEmpty() || words.size() == 1 && words.getFirst().endMs() <= words.getFirst().startMs());
	}

	/** The sizes of the main words: a main word, and the key word or one held long, made larger: a difference in size never comes from making a word small. */
	static final float REGULAR = 0.9f;
	static final float LARGE = 1.15f;
	/** A word of this many letters at most never stands alone in a row. */
	static final int LONE_LETTERS = 3;

	/** @return the line's key word among its main words, or {@link #NO_PIECE} */
	private static int keyOf(List<Sung> sung, boolean[] small, CutoutTiles.Choice choice) {
		int key = NO_PIECE;
		for (int i = 0; i < sung.size(); i++) {
			key = sung.get(i).from() == choice.key() && !small[i] ? i : key;
		}
		return key;
	}

	/**
	 * The rows of a line: each has one main word, with the small words sung before it on its left and those after the line's last main word on
	 * its right, all on one baseline. A main word of {@link #LONE_LETTERS} letters or fewer never stands alone: it joins the row after it (or,
	 * at the line's end, the one before) as a small word beside that row's main word; the key word stays a main word, and the word beside it
	 * joins its row instead. Small words are marked in {@code small} as they change.
	 *
	 * @return the row of each word, counted from the line's first, in the order they are sung
	 */
	static int[] rows(List<Sung> sung, boolean[] small, boolean[] quiet, int key) {
		int count = sung.size();
		int[] group = new int[count];
		// at most one pass per word: each merge takes one row away
		for (int pass = 0; pass <= count; pass++) {
			number(small, group);
			int rows = count == 0 ? 0 : group[count - 1] + 1;
			int lone = NO_PIECE;
			for (int i = 0; i < count && lone == NO_PIECE && rows > 1; i++) {
				if (!small[i] && !quiet[i] && alone(group, i) && CutoutLexicon.plain(sung.get(i).text()).length() <= LONE_LETTERS) {
					lone = i;
				}
			}
			if (lone == NO_PIECE) {
				return group;
			}
			if (lone != key) {
				small[lone] = true;
				continue;
			}
			// the key word keeps its row: a lone word next to it joins it instead
			// the word before it (a small word joins the main word after it), or the line's last word after it
			int beside = lone > 0 && alone(group, lone - 1) && !quiet[lone - 1] ? lone - 1
				: lone + 1 == count - 1 && !quiet[lone + 1] ? lone + 1 : NO_PIECE;
			if (beside == NO_PIECE) {
				return group;
			}
			small[beside] = true;
		}
		number(small, group);
		return group;
	}

	/** The rows a sung line is joined down to: one or two, more only where a row would not fit the column. */
	static final int MOST_ROWS = 2;
	/** How wide a row may be joined, in letters of a main word, with one for each gap. */
	static final float MOST_ROW_WIDTH = 16f;
	/** What joining two rows across a phrase costs: more than any width. */
	private static final float ACROSS_PHRASE = 1000f;
	/** Words that open a new phrase: a row may end before them, and rows are joined across them last. */
	private static final Set<String> PHRASE_OPENERS = Set.of("and", "but", "or", "so", "because", "cause", "'cause", "when", "while",
		"if", "then", "i", "you", "we", "they", "he", "she", "und", "aber", "oder", "denn", "weil", "wenn", "dann", "ich", "du", "wir", "sie", "er");

	/**
	 * Joins the rows of a line until it has at most {@code most}: of two rows next to each other, those not parted by a phrase (a comma or the
	 * like at the end of the row, or a word that opens a new phrase at the start of the next) are joined first, the narrowest such pair first,
	 * so a line breaks only between phrases where it can. The words keep their sung order; small words stay with their word.
	 */
	static void block(String text, List<Sung> sung, boolean[] small, int[] group, int most) {
		int count = sung.size();
		while (count > 0 && group[count - 1] + 1 > most) {
			int rows = group[count - 1] + 1;
			float[] widths = new float[rows];
			int[] firsts = new int[rows];
			Arrays.fill(firsts, -1);
			for (int i = 0; i < count; i++) {
				widths[group[i]] += CutoutLexicon.plain(sung.get(i).text()).length() * (small[i] ? SMALL : 1f) + 1f;
				if (firsts[group[i]] < 0) {
					firsts[group[i]] = i;
				}
			}
			int join = -1;
			float best = Float.MAX_VALUE;
			for (int row = 0; row + 1 < rows; row++) {
				int last = firsts[row + 1] - 1;
				boolean parted = phraseEnds(text, sung.get(last).to())
					|| PHRASE_OPENERS.contains(CutoutLexicon.plain(sung.get(firsts[row + 1]).text()).toLowerCase(Locale.ROOT));
				// a row that would not fit the column is not made: its words would have to be set smaller, and a line rather takes another row
				if (widths[row] + widths[row + 1] > MOST_ROW_WIDTH) {
					continue;
				}
				// a phrase between them costs more than any width
				float cost = widths[row] + widths[row + 1] + (parted ? ACROSS_PHRASE : 0f);
				if (cost < best) {
					best = cost;
					join = row;
				}
			}
			// across a phrase only where that brings the line down to its rows: a long line rather keeps its break at the comma
			if (join < 0 || best >= ACROSS_PHRASE && rows - 1 > most) {
				return;
			}
			for (int i = 0; i < count; i++) {
				if (group[i] > join) {
					group[i]--;
				}
			}
		}
	}

	/**
	 * @param to where the word's drawn letters end: a comma or a stop after it is left out of them, so it is the first character after them
	 * @return whether a mark that ends a phrase (a comma, a stop, a dash) follows the word in the line's text
	 */
	private static boolean phraseEnds(String text, int to) {
		int at = to;
		// a dash may stand after a space ("night - and")
		while (at < text.length() && text.charAt(at) == ' ' && at < to + 1) {
			at++;
		}
		if (at >= text.length()) {
			return false;
		}
		char end = text.charAt(at);
		return end == ',' || end == ';' || end == ':' || end == '-' || end == '–' || end == '.' || end == '!' || end == '?';
	}

	/** Numbers the rows: a row ends with its main word, and the small words after the line's last main word stay in its row. */
	private static void number(boolean[] small, int[] group) {
		int count = small.length;
		int last = NO_PIECE;
		for (int i = 0; i < count; i++) {
			last = small[i] ? last : i;
		}
		int row = 0;
		for (int i = 0; i < count; i++) {
			group[i] = row;
			if (!small[i] && i != last) {
				row++;
			}
		}
	}

	/** @return whether a word is the only one in its row */
	private static boolean alone(int[] group, int i) {
		return (i == 0 || group[i - 1] != group[i]) && (i == group.length - 1 || group[i + 1] != group[i]);
	}

	/**
	 * A line has one symbol at most, so symbols decorate and never crowd: the first big word that has one keeps it, the others lose theirs; a
	 * word on a tile is set off already, so it keeps its symbol only when no other word has one. (Small words have none: a symbol stands beside
	 * a big word.)
	 */
	private static List<Piece> oneSymbol(List<Piece> pieces) {
		int keeps = NO_PIECE;
		for (int i = 0; i < pieces.size() && keeps == NO_PIECE; i++) {
			keeps = pieces.get(i).symbol() != CutoutLexicon.NO_SYMBOL && !pieces.get(i).tile() ? i : NO_PIECE;
		}
		for (int i = 0; i < pieces.size() && keeps == NO_PIECE; i++) {
			keeps = pieces.get(i).symbol() != CutoutLexicon.NO_SYMBOL ? i : NO_PIECE;
		}
		for (int i = 0; i < pieces.size(); i++) {
			Piece piece = pieces.get(i);
			if (i != keeps && piece.symbol() != CutoutLexicon.NO_SYMBOL) {
				pieces.set(i, new Piece(piece.source(), piece.from(), piece.to(), piece.startMs(), piece.endMs(), piece.size(), piece.small(), piece.quiet(),
					piece.tile(), piece.pale(), CutoutLexicon.NO_SYMBOL, piece.group(), piece.key()));
			}
		}
		return pieces;
	}

	/** What stands between two words in a row of a sprite: room enough that nothing of one word (its glow least of all) reaches into the cut of the next. */
	static final String GAP = "   ";

	/**
	 * Where a piece lies in the sprites of its line.
	 *
	 * @param sheet the sprite, counted from the line's first
	 * @param row   its row there
	 * @param from  the glyphs of the row it runs from and to
	 */
	record Cut(int sheet, int row, int from, int to) {
	}

	/**
	 * Packs the texts of a line's pieces into the rows of sprites, each text whole and on its own, with {@link #GAP} between two in a row: so
	 * every word has room around it in the picture it is cut from.
	 *
	 * @param texts  the texts of the pieces
	 * @param width  how wide a row is
	 * @param most   the widest a row may be
	 * @param rows   how many rows a sprite has
	 * @param sheets receives the rows of each sprite
	 * @return where each piece lies
	 */
	static Cut[] pack(List<String> texts, java.util.function.ToDoubleFunction<String> width, float most, int rows, List<List<String>> sheets) {
		Cut[] cuts = new Cut[texts.size()];
		StringBuilder row = new StringBuilder();
		List<String> sheet = new ArrayList<>();
		for (int i = 0; i < cuts.length; i++) {
			String text = texts.get(i);
			if (!row.isEmpty() && width.applyAsDouble(row + GAP + text) > most) {
				sheet.add(row.toString());
				row.setLength(0);
				if (sheet.size() == rows) {
					sheets.add(sheet);
					sheet = new ArrayList<>();
				}
			}
			if (!row.isEmpty()) {
				row.append(GAP);
			}
			cuts[i] = new Cut(sheets.size(), sheet.size(), row.length(), row.length() + text.length());
			row.append(text);
		}
		if (!row.isEmpty()) {
			sheet.add(row.toString());
		}
		if (!sheet.isEmpty()) {
			sheets.add(sheet);
		}
		return cuts;
	}

	/**
	 * @param ink the edges of a word's drawn letters: left, top, right, bottom
	 * @param pad the room around them
	 * @return the tile behind the word: a rectangle around its drawn letters with the same room on all four sides
	 */
	static float[] tile(float[] ink, float pad) {
		return new float[] {ink[0] - pad, ink[1] - pad, ink[2] + pad, ink[3] + pad};
	}

	/**
	 * A run of small words that no big word follows, or three and more of them in a row, would be a stack of nothing but small print: the one
	 * held longest in such a run (the last of them, if several are) is a big word. Ad-libs are never made big, and do not end a run.
	 */
	private static void promote(List<Sung> words, boolean[] small, boolean[] quiet) {
		int count = small.length;
		int from = 0;
		while (from < count) {
			if (!small[from] || quiet[from]) {
				from++;
				continue;
			}
			int to = from;
			int own = 0;
			while (to < count && small[to]) {
				own += quiet[to] ? 0 : 1;
				to++;
			}
			boolean bigFollows = to < count;
			// small words at the end of a line after a big word join its row, beside it: a lone filler at a line's end is never a big word
			boolean bigBefore = false;
			for (int i = 0; i < from; i++) {
				bigBefore |= !small[i] && !quiet[i];
			}
			if (own >= 3 || !bigFollows && !bigBefore) {
				int longest = NO_PIECE;
				for (int i = from; i < to; i++) {
					if (!quiet[i] && (longest == NO_PIECE
						|| words.get(i).endMs() - words.get(i).startMs() >= words.get(longest).endMs() - words.get(longest).startMs())) {
						longest = i;
					}
				}
				small[longest] = false;
			}
			from = to;
		}
	}

	/** What the tones are for: the free words, the tiles and their letters, the symbol, the glow, the word being sung and the shadow. */
	enum Tone {
		WORD, SMALL, ON_DARK, DARK_TILE, PALE_TILE, ON_PALE, SYMBOL, GLOW, SUNG, SHADOW
	}

	/** The tones of a line of large words before an even background, as light as this all over (see the method below). */
	static void tones(int first, int second, float behind, float busy, int[] out, int[] dimmed) {
		tones(first, second, behind, behind, busy, LyricTones.LARGE_CONTRAST, LyricTones.LARGE_CONTRAST, out, dimmed);
	}

	/**
	 * The tones of a line before what lies behind it ({@link LyricTones}). Every tone keeps the hue of its color. On a dark background the free
	 * words are a light tint, the word being sung white, and they glow; on a bright one they are deep and saturated, without a glow; where
	 * neither reads well enough the tint gets a thin shadow ({@link Tone#SHADOW}, its strength in its alpha). Both kinds of tile carry white
	 * letters; a tile is told from what lies behind by its tone alone, which has no edge.
	 *
	 * @param first  the main color (the cover's first color, or the accent)
	 * @param second a second color beside it
	 * @param darkest   how light the darkest of what lies behind the line is (relative luminance, 0 to 1)
	 * @param brightest how light the brightest of it is: the words must read against all of it
	 * @param busy      how much world surfaces there vary (their standard deviation); the sky never counts as busy
	 * @param wordContrast  the contrast the words and the letters on tiles need at their size on the screen ({@link LyricTones#contrastFor})
	 * @param smallContrast the same for the small words
	 * @param out    receives the tones
	 * @param dimmed receives them dimmed for a line already sung: moved toward what lies behind, never to less than
	 *               {@link LyricTones#SUNG_CONTRAST} against it
	 */
	static void tones(int first, int second, float darkest, float brightest, float busy, float wordContrast, float smallContrast, int[] out, int[] dimmed) {
		int opaque = 0xFF000000 | first;
		int other = 0xFF000000 | second;
		// deep words only where they read against the darkest part; light ones then answer to the brightest part (with a shadow where needed)
		boolean light = LyricTones.light(opaque, darkest, wordContrast);
		float behind = light ? brightest : darkest;
		int word;
		int small;
		int sung;
		float shadow = 0f;
		if (light) {
			word = LyricTones.tint(opaque, behind, wordContrast);
			small = LyricTones.tint(other, behind, smallContrast);
			sung = LyricTones.WHITE;
			shadow = Math.max(LyricTones.shadow(LyricTones.luminance(word), behind, busy, wordContrast),
				LyricTones.shadow(LyricTones.luminance(small), behind, busy, smallContrast));
		} else {
			word = LyricTones.deep(opaque, behind, wordContrast);
			small = LyricTones.deep(other, behind, smallContrast);
			// the word being sung is the deepest
			sung = LyricTones.deep(opaque, behind, Math.max(wordContrast, LyricTones.SUNG_DEEP_CONTRAST));
		}
		int symbol = small;
		int letters = LyricTones.WHITE;
		int dark = LyricTones.tile(opaque, behind, LyricTones.DARK_TILE_SHARE, wordContrast);
		int pale = LyricTones.tile(opaque, behind, LyricTones.PALE_TILE_SHARE, wordContrast);
		out[Tone.WORD.ordinal()] = word;
		out[Tone.SMALL.ordinal()] = small;
		out[Tone.SYMBOL.ordinal()] = symbol;
		out[Tone.SUNG.ordinal()] = sung;
		out[Tone.DARK_TILE.ordinal()] = dark;
		out[Tone.PALE_TILE.ordinal()] = pale;
		out[Tone.ON_DARK.ordinal()] = letters;
		out[Tone.ON_PALE.ordinal()] = letters;
		// a glow is light: on a bright background it would only wash the deep words out
		out[Tone.GLOW.ordinal()] = light ? opaque : opaque & 0xFFFFFF;
		out[Tone.SHADOW.ordinal()] = alpha(LyricTones.SHADOW_COLOR, shadow);
		// a free word is read against what lies behind with its shadow under it
		float shaded = LyricTones.shaded(behind, shadow);
		dimmed[Tone.WORD.ordinal()] = LyricTones.dim(word, shaded, LyricTones.SUNG_CONTRAST);
		dimmed[Tone.SMALL.ordinal()] = LyricTones.dim(small, shaded, LyricTones.SUNG_CONTRAST);
		dimmed[Tone.SYMBOL.ordinal()] = dimmed[Tone.SMALL.ordinal()];
		dimmed[Tone.SUNG.ordinal()] = dimmed[Tone.WORD.ordinal()];
		dimmed[Tone.DARK_TILE.ordinal()] = LyricTones.dimTile(dark, behind, letters);
		dimmed[Tone.PALE_TILE.ordinal()] = LyricTones.dimTile(pale, behind, letters);
		dimmed[Tone.ON_DARK.ordinal()] = letters;
		dimmed[Tone.ON_PALE.ordinal()] = letters;
		dimmed[Tone.GLOW.ordinal()] = alpha(out[Tone.GLOW.ordinal()], (out[Tone.GLOW.ordinal()] >>> 24) / 255f * LyricTones.DIM_KEEPS);
		dimmed[Tone.SHADOW.ordinal()] = out[Tone.SHADOW.ordinal()];
	}

	/** @return the color with this alpha, from 0 to 1 */
	private static int alpha(int rgb, float alpha) {
		return Math.clamp(Math.round(alpha * 255f), 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	/** @return a number from 0 to 1 that looks random and is the same every time */
	static float hash(int n) {
		n = (n ^ 61) ^ (n >>> 16);
		n *= 9;
		n ^= n >>> 4;
		n *= 0x27d4eb2d;
		n ^= n >>> 15;
		return (n & 0xFFFFFF) / (float) 0x1000000;
	}

	/** @return how many groups the pieces are in */
	static int groups(List<Piece> pieces) {
		return pieces.isEmpty() ? 0 : pieces.getLast().group() + 1;
	}

	/**
	 * @param size  the size a word has by its importance
	 * @param width how wide the word is at the size of a big word
	 * @param room  the column's width
	 * @return the word's size: smaller where it would run out of the column, and within the least and the most
	 */
	static float fitted(float size, float width, float room) {
		float fits = width <= 0f ? size : Math.min(size, room / width);
		return Math.clamp(fits, LEAST, MOST);
	}
}
