package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The Cut-out preset's words: their sizes and tiles, the words kept and their fading, the symbols, and lines without word timings. */
class CutoutWordsTest {
	/** A line whose words each take {@code wordMs}, but for those named with their own length as "word:ms". */
	private static LyricLine line(long startMs, long wordMs, String... words) {
		StringBuilder text = new StringBuilder();
		List<Word> timed = new ArrayList<>();
		long at = startMs;
		for (String each : words) {
			String word = each.contains(":") ? each.substring(0, each.indexOf(':')) : each;
			long length = each.contains(":") ? Long.parseLong(each.substring(each.indexOf(':') + 1)) : wordMs;
			if (!text.isEmpty()) {
				text.append(' ');
			}
			timed.add(new Word(text.length(), text.length() + word.length(), at, at + length));
			text.append(word);
			at += length;
		}
		return new LyricLine(startMs, text.toString(), timed);
	}

	private static List<Piece> pieces(LyricLine line) {
		return CutoutWords.pieces(line, line.words(), line.words().getLast().endMs(), true, true);
	}

	private static String word(LyricLine line, Piece piece) {
		return line.text().substring(piece.from(), piece.to());
	}

	@Test
	void functionWordsAreSmallAndContentWordsBig() {
		LyricLine line = line(0, 300, "the", "night", "is", "young");
		List<Piece> pieces = pieces(line);
		assertTrue(pieces.get(0).small(), "the");
		assertFalse(pieces.get(1).small(), "night");
		assertTrue(pieces.get(2).small(), "is");
		assertFalse(pieces.get(3).small(), "young");
		assertEquals(CutoutWords.SMALL, pieces.get(0).size());
		assertTrue(pieces.get(1).size() == CutoutWords.REGULAR || pieces.get(1).size() == CutoutWords.LARGE, "a main word: " + pieces.get(1).size());
		// a small word belongs to the group of the big word after it
		assertEquals(pieces.get(1).group(), pieces.get(0).group());
		assertEquals(pieces.get(3).group(), pieces.get(2).group());
		assertEquals(2, CutoutWords.groups(pieces));
	}

	@Test
	void germanFunctionWordsAreSmallToo() {
		LyricLine line = line(0, 300, "Wir", "fahren", "durch", "die", "Nacht");
		List<Piece> pieces = pieces(line);
		assertTrue(pieces.get(0).small(), "Wir");
		assertFalse(pieces.get(1).small(), "fahren");
		assertTrue(pieces.get(2).small(), "durch");
		assertTrue(pieces.get(3).small(), "die");
		assertFalse(pieces.get(4).small(), "Nacht");
	}

	@Test
	void inAnotherLanguageShortWordsAreSmall() {
		LyricLine line = line(0, 300, "Je", "veux", "la", "lune");
		List<Piece> pieces = pieces(line);
		assertTrue(pieces.get(0).small(), "Je: two letters");
		assertFalse(pieces.get(1).small(), "veux");
		assertTrue(pieces.get(2).small(), "la");
		assertFalse(pieces.get(3).small(), "lune");
		// in English a short content word is not small: the lists decide there
		assertFalse(pieces(line(0, 300, "the", "sun", "is", "up", "high")).get(1).small(), "sun, in an English line");
	}

	@Test
	void aWordHeldLongIsBiggerWithinTheLimits() {
		List<Piece> pieces = pieces(line(0, 300, "running", "wild:1200", "forever:9000"));
		assertEquals(CutoutWords.REGULAR, pieces.get(0).size(), "sung briefly: a main word");
		assertEquals(CutoutWords.LARGE, pieces.get(1).size(), "held a while: one of the large ones");
		assertEquals(CutoutWords.LARGE, pieces.get(2).size(), "no larger, however long it is held");
		// a long word shrinks to the column, and nothing gets smaller than the least or larger than the most
		assertEquals(0.75f, CutoutWords.fitted(1f, 200f, 150f), 1e-6f);
		assertEquals(CutoutWords.LEAST, CutoutWords.fitted(1f, 3000f, 150f));
		assertEquals(1f, CutoutWords.fitted(1f, 100f, 150f), "a word that fits keeps its size");
		assertEquals(CutoutWords.MOST, CutoutWords.fitted(2f, 10f, 150f));
	}

	@Test
	void aRunOfSmallWordsGetsABigOne() {
		// nothing but function words: the one held longest is big, so the stack is never only small print
		LyricLine line = line(0, 200, "all", "of:500", "my", "cars");
		List<Piece> pieces = pieces(line);
		assertTrue(pieces.get(0).small(), "all");
		assertFalse(pieces.get(1).small(), "of, the longest of three small words in a row");
		assertTrue(pieces.get(2).small(), "my");
		assertFalse(pieces.get(3).small(), "cars");
		// small words at the line's end have no main word after them
		List<Piece> end = pieces(line(0, 200, "running", "to", "you:600"));
		assertTrue(end.get(1).small() && end.get(2).small(), "small words at the line's end stay small");
		assertEquals(end.get(0).group(), end.get(2).group(), "and join the row of the main word before them");
	}

	@Test
	void withoutSizeByImportanceAllWordsAreTheSame() {
		LyricLine line = line(0, 300, "the", "night", "is", "young:2000");
		List<Piece> pieces = CutoutWords.pieces(line, line.words(), 3000, false, true);
		for (Piece piece : pieces) {
			assertEquals(CutoutWords.EVEN, piece.size());
		}
		// one size for all; a word of three letters or fewer still never stands alone in a row
		assertEquals(2, CutoutWords.groups(pieces), "the night / is young");
	}

	@Test
	void swearWordsAndSlursStayPlainWords() {
		for (String text : new String[] {"this motherfucking night is so beautiful", "all this bullshit forever", "where is my f*cking money",
			"du Hurensohn verschwinde", "you are a beautiful bitch"}) {
			LyricLine line = new LyricLine(0, text);
			List<Piece> pieces = CutoutWords.pieces(line, line.words(), 6000, true, true);
			for (Piece piece : pieces) {
				String word = text.substring(piece.from(), piece.to());
				if (CutoutLexicon.rude(word)) {
					assertFalse(piece.key(), text + ": " + word + " is the key word");
					assertFalse(piece.tile(), text + ": " + word + " is on the tile");
					assertEquals(CutoutLexicon.NO_SYMBOL, piece.symbol(), text + ": " + word + " has a symbol");
					assertTrue(piece.size() <= CutoutWords.REGULAR, text + ": " + word + " is big");
				}
			}
		}
		assertTrue(CutoutLexicon.rude("Fuck!"));
		assertTrue(CutoutLexicon.rude("s**t"), "censored with asterisks");
		assertFalse(CutoutLexicon.rude("beautiful"));
		assertEquals(CutoutLexicon.NO_SYMBOL, CutoutLexicon.symbol("bitch"));
	}

	@Test
	void sizesAndTilesAreTheSameEveryTime() {
		LyricLine line = line(0, 300, "we", "were", "running", "through", "the", "city", "lights", "tonight");
		List<Piece> once = pieces(line);
		List<Piece> again = pieces(line);
		assertEquals(once, again, "the same line looks the same");
		int tiles = 0;
		for (Piece piece : once) {
			assertFalse(piece.small() && piece.tile(), "small words never have a tile");
			tiles += piece.tile() ? 1 : 0;
		}
		assertTrue(tiles <= 1, "one tile in a line at most: " + tiles);
	}

	/** A column placed anew in the middle of its line shows the words from then on, the first of them at the top of a page; those before never show. */
	@Test
	void aColumnPlacedAnewStartsWithTheNextWordAtTheTop() {
		LyricLine line = line(0, 1000, "running", "wild", "tonight", "forever", "home");
		List<Piece> pieces = pieces(line);
		int groups = CutoutWords.groups(pieces);
		long from = 1500L;
		int first = firstFrom(pieces, from);
		assertTrue(first > 0 && first < groups);
		assertTrue(CutoutStack.groupStart(pieces, first) >= from && CutoutStack.groupStart(pieces, first - 1) < from);
		float[] heights = new float[groups];
		java.util.Arrays.fill(heights, 40f);
		float[] tops = CutoutStack.tops(heights, 3, first);
		assertEquals(0f, tops[first], "the next word is the first of a page");
		CutoutStack.Timing anew = CutoutStack.timing(pieces, 3, first);
		for (int i = 0; i < pieces.size(); i++) {
			for (long at = 0; at < 6000; at += 50) {
				float strength = CutoutStack.strength(pieces, i, at, anew);
				if (pieces.get(i).group() < first) {
					assertEquals(0f, strength, "a word from before never shows at the new spot");
				}
			}
			if (pieces.get(i).group() >= first) {
				assertEquals(0f, CutoutStack.in(i, pieces.get(i).startMs(), anew), 1e-4f, "it fades in, from nothing");
				assertTrue(CutoutStack.strength(pieces, i, pieces.get(i).startMs() + CutoutStack.IN_MS, anew) > 0.9f);
			}
		}
		assertEquals(groups, firstFrom(pieces, 60_000L), "after the last word nothing more shows");
	}

	/** @return the first group that starts at a time or later; the number of groups when none does */
	private static int firstFrom(List<Piece> pieces, long from) {
		int groups = CutoutWords.groups(pieces);
		for (int group = 0; group < groups; group++) {
			if (CutoutStack.groupStart(pieces, group) >= from) {
				return group;
			}
		}
		return groups;
	}

	private static CutoutStack.Timing timing(List<Piece> pieces, int kept) {
		return CutoutStack.timing(pieces, kept);
	}

	@Test
	void theWordsOfAPageStayAndGoTogetherBeforeTheNextPage() {
		// four big words, each too long to share a row, one every second; two kept, so two pages of two
		LyricLine line = line(0, 1000, "everlasting", "thunderstorm", "neighbourhood", "constellation");
		List<Piece> pieces = pieces(line);
		CutoutStack.Timing two = timing(pieces, 2);
		assertEquals(0f, CutoutStack.strength(pieces, 0, -1, two), "nothing before its start");
		assertEquals(1f, CutoutStack.strength(pieces, 0, 500, two), "the word being sung is the strongest");
		assertEquals(0f, CutoutStack.strength(pieces, 1, 500, two), "the next has not come");
		// the second word has come: the first dims, both stay
		assertEquals(1f, CutoutStack.strength(pieces, 1, 1500, two));
		assertEquals(CutoutStack.OLDER, CutoutStack.strength(pieces, 0, 1500, two), 1e-4f);
		// the page is full: both go together, and are gone when the third word comes
		float going = CutoutStack.strength(pieces, 0, 1900, two);
		assertTrue(going > 0f && going < CutoutStack.OLDER, "fading out: " + going);
		assertTrue(CutoutStack.strength(pieces, 1, 1900, two) < 1f, "the newest of the page goes with it");
		assertEquals(0f, CutoutStack.strength(pieces, 0, 2000, two), "gone when the next page starts");
		assertEquals(0f, CutoutStack.strength(pieces, 1, 2000, two));
		assertEquals(0f, CutoutStack.strength(pieces, 0, 3500, two), "and stays gone");
		// so no word ever shows together with one that stands in its place
		for (long at = 0; at <= 4000; at += 10) {
			boolean first = CutoutStack.strength(pieces, 0, at, two) > 0f || CutoutStack.strength(pieces, 1, at, two) > 0f;
			boolean second = CutoutStack.strength(pieces, 2, at, two) > 0f || CutoutStack.strength(pieces, 3, at, two) > 0f;
			assertFalse(first && second, "two pages at once at " + at);
		}
		// with more kept, more stay, the older the dimmer
		assertEquals(CutoutStack.OLDEST, CutoutStack.strength(pieces, 0, 2700, timing(pieces, 3)), 1e-4f);
		assertTrue(CutoutStack.strength(pieces, 0, 3900, timing(pieces, 5)) > 0f, "five kept: all four words of the line stay");
	}

	@Test
	void aWordIsPlacedOnceAndNeverMoves() {
		// seven groups with three kept: three stand one under the other, the fourth starts a new page at the top
		float[] heights = {40f, 62f, 40f, 40f, 40f, 62f, 40f};
		float[] tops = CutoutStack.tops(heights, 3);
		assertEquals(0f, tops[0]);
		assertEquals(40f, tops[1]);
		assertEquals(102f, tops[2]);
		assertEquals(0f, tops[3], "the fourth group starts a new page at the top");
		assertEquals(40f, tops[4]);
		assertEquals(80f, tops[5]);
		assertEquals(0f, tops[6]);
		// the places depend on nothing but the line: asked again, at any time of the song, they are the same
		assertEquals(java.util.Arrays.toString(tops), java.util.Arrays.toString(CutoutStack.tops(heights, 3)));
		// on a page the groups stand one under the other and never share a place
		for (int group = 1; group < tops.length; group++) {
			if (group % 3 != 0) {
				assertEquals(tops[group - 1] + heights[group - 1], tops[group], 1e-4f, "group " + group + " stands right under the one before");
			}
		}
	}

	@Test
	void aWordComesInCalmlyAndGoesOnce() {
		LyricLine line = line(0, 1000, "everlasting", "thunderstorm", "neighbourhood", "constellation");
		List<Piece> pieces = pieces(line);
		CutoutStack.Timing two = timing(pieces, 2);
		// in: from nothing to its place, rising all the way and never past it
		float before = 0f;
		for (long at = 0; at <= CutoutStack.IN_MS + 50; at += 5) {
			float in = CutoutStack.in(0, at, two);
			assertTrue(in >= before && in <= 1f, "no overshoot at " + at + ": " + in);
			before = in;
		}
		assertEquals(1f, CutoutStack.in(0, CutoutStack.IN_MS, two));
		// out: only as the next page is about to come, and then once, all the way
		assertEquals(0f, CutoutStack.gone(0, 2000 - CutoutStack.OUT_MS, two));
		before = 0f;
		for (long at = 2000 - CutoutStack.OUT_MS; at <= 2100; at += 5) {
			float gone = CutoutStack.gone(0, at, two);
			assertTrue(gone >= before, "it does not come back at " + at);
			before = gone;
		}
		assertEquals(1f, CutoutStack.gone(0, 2000, two), "gone when the next page's first word comes");
		assertEquals(0f, CutoutStack.gone(3, 99_000, two), "the last words of a line stay: nothing pops when the line ends");
		assertTrue(CutoutStack.strength(pieces, 3, 99_000, two) > 0.99f);
	}

	@Test
	void theClockNeverRunsBackByALittle() {
		assertEquals(5_000, CutoutStack.steady(4_990, 5_000), "forward as the song goes");
		assertEquals(5_000, CutoutStack.steady(5_000, 4_960), "a correction of the player's clock by 40 ms is not followed back");
		assertEquals(5_000, CutoutStack.steady(5_000, 5_000 - CutoutStack.SEEK_MS), "nor one of a second and a half");
		assertEquals(2_000, CutoutStack.steady(5_000, 2_000), "but a seek back is");
		// a word that has come stays: its strength does not flicker while the clock stutters around its start
		LyricLine line = line(0, 1000, "running", "through");
		List<Piece> pieces = pieces(line);
		CutoutStack.Timing two = timing(pieces, 2);
		long shown = 990;
		float last = 0f;
		for (long song : new long[] {1_000, 1_030, 995, 1_040, 990, 1_060, 1_010, 1_090}) {
			shown = CutoutStack.steady(shown, song);
			float strength = CutoutStack.strength(pieces, 1, shown, two);
			assertTrue(strength >= last, "the second word only comes, at song time " + song + ": " + strength + " after " + last);
			last = strength;
		}
	}

	@Test
	void aFastWordStaysAtFullStrengthForAMoment() {
		// three words, three rows: a line is a block of three rows at most
		LyricLine line = line(0, 80, "everlasting", "thunderstorm", "neighbourhood");
		List<Piece> pieces = CutoutWords.pieces(line, line.words(), 320, false, false);
		CutoutStack.Timing four = timing(pieces, 4);
		// 100 ms after "go" started, "run" has come: "go" is not dimmed yet
		assertEquals(1f, CutoutStack.strength(pieces, 0, 100 + CutoutStack.IN_MS, four), 1e-4f);
		assertTrue(CutoutStack.strength(pieces, 0, CutoutStack.LEAST_SHOWN_MS + CutoutStack.DIM_MS, four) < 1f, "then it dims");
		for (int i = 0; i < pieces.size(); i++) {
			assertTrue(CutoutStack.strength(pieces, i, pieces.get(i).startMs() + CutoutStack.IN_MS, four) > 0.99f, "every word shows: " + i);
		}
		// also the last word of a full page, though the next page's first word comes 80 ms after it: it is seen first and goes a moment later
		CutoutStack.Timing twoKept = timing(pieces, 2);
		assertTrue(CutoutStack.strength(pieces, 1, 80 + 60, twoKept) > 0.2f, "\"run\" is not gone before it was seen");
		assertEquals(0f, CutoutStack.strength(pieces, 1, 160 + CutoutStack.LEAST_OUT_MS, twoKept), "and is gone right after");
		// and the next page's first word waits that moment, so it never comes in over the page that is going
		assertEquals(0f, CutoutStack.strength(pieces, 2, 160 + CutoutStack.LEAST_OUT_MS - 1, twoKept), "\"now\" waits for the page before it");
		assertTrue(CutoutStack.strength(pieces, 2, 160 + CutoutStack.LEAST_OUT_MS + CutoutStack.IN_MS, twoKept) > 0.99f, "and is there right after");
		for (long at = 0; at <= 1000; at += 5) {
			boolean first = CutoutStack.strength(pieces, 0, at, twoKept) > 0f || CutoutStack.strength(pieces, 1, at, twoKept) > 0f;
			boolean second = CutoutStack.strength(pieces, 2, at, twoKept) > 0f;
			assertFalse(first && second, "two pages at once at " + at + " in the fast part");
		}
	}

	@Test
	void everyWordHasRoomAroundItInItsSprite() {
		List<String> texts = List.of("all", "of", "my", "cars", "are", "gone", "tonight", "and this");
		List<List<String>> sheets = new ArrayList<>();
		// a row holds 16 letters, a sprite two rows
		CutoutWords.Cut[] cuts = CutoutWords.pack(texts, String::length, 16f, 2, sheets);
		for (int i = 0; i < cuts.length; i++) {
			String row = sheets.get(cuts[i].sheet()).get(cuts[i].row());
			assertEquals(texts.get(i), row.substring(cuts[i].from(), cuts[i].to()), "the cut is the word");
			if (cuts[i].from() > 0) {
				assertEquals(CutoutWords.GAP, row.substring(cuts[i].from() - CutoutWords.GAP.length(), cuts[i].from()), "with the gap before it");
			}
			if (cuts[i].to() < row.length()) {
				assertEquals(CutoutWords.GAP, row.substring(cuts[i].to(), cuts[i].to() + CutoutWords.GAP.length()), "and after it");
			}
		}
		for (List<String> sheet : sheets) {
			assertTrue(sheet.size() <= 2, "no more rows than a sprite has");
			for (String row : sheet) {
				assertTrue(row.length() <= 16, "no row wider than the sprite: " + row);
				assertFalse(row.startsWith(" ") || row.endsWith(" "), "no gap at a row's end");
			}
		}
		assertTrue(sheets.size() >= 2, "what does not fit one sprite goes on to the next: " + sheets);
		assertEquals("and this", sheets.getLast().getLast().substring(cuts[7].from(), cuts[7].to()), "a row of words without timings stays whole");
		// a word wider than a row still gets a row of its own
		List<List<String>> wide = new ArrayList<>();
		CutoutWords.Cut[] alone = CutoutWords.pack(List.of("a", "pneumonoultramicroscopicsilicovolcanoconiosis", "b"), String::length, 30f, 4, wide);
		assertEquals(3, wide.getFirst().size());
		assertEquals(1, alone[1].row());
	}

	@Test
	void theDrawnLettersLieInsideTheirTileWithTheSameRoomOnAllSides() {
		float pad = CutoutLyrics.TILE_PAD;
		// short and long words, wide letters, letters that reach past their room, marks above, what hangs below, all capitals
		for (String word : new String[] {"a", "knew", "www", "mmm", "kick", "jump", "WAVE", "ÜBER", "Größe", "tonight", "fly", "Äpfel", "everything", "MIDNIGHT",
			"gypsy", "it's", "Kopf", "j", "TV"}) {
			float[] ink = dev.tim1337.lumio.render.CutoutInk.bounds(word);
			assertTrue(ink != null, "the font is there and measures " + word);
			assertTrue(ink[2] > ink[0] && ink[3] > ink[1], word + " has ink");
			// as the preset sets it: the ink at the size of a big word, the tile around it
			float size = 40f;
			float[] drawn = {ink[0] * size, ink[1] * size, ink[2] * size, ink[3] * size};
			float[] tile = CutoutWords.tile(drawn, pad);
			assertEquals(pad, drawn[0] - tile[0], 1e-4f, word + ": left");
			assertEquals(pad, drawn[1] - tile[1], 1e-4f, word + ": top");
			assertEquals(pad, tile[2] - drawn[2], 1e-4f, word + ": right");
			assertEquals(pad, tile[3] - drawn[3], 1e-4f, word + ": bottom");
			// and the letters lie inside the stretch of the row the word is cut from, with the room of the gap beside it: nothing is cut off.
			// A letter may start a little before the word's own room (a "j") or end after it; never by more than a fifth of the font's size.
			assertTrue(ink[0] > -0.2f && ink[2] < ink[4] + 0.2f, word + " stays within its cut: " + ink[0] + " to " + ink[2] + " of " + ink[4]);
		}
		// letters above and below the line are measured, not guessed
		assertTrue(dev.tim1337.lumio.render.CutoutInk.bounds("ÜBER")[1] < dev.tim1337.lumio.render.CutoutInk.bounds("xxxx")[1], "an umlaut's dots reach higher");
		assertTrue(dev.tim1337.lumio.render.CutoutInk.bounds("gypsy")[3] > dev.tim1337.lumio.render.CutoutInk.bounds("xxxx")[3], "a g hangs lower");
	}

	@Test
	void everySymbolHasAtLeastEightWordsAndNoWordIsMappedTwice() {
		java.util.Map<String, Integer> seen = new java.util.HashMap<>();
		for (int symbol = 0; symbol < CutoutLexicon.SYMBOLS_COUNT; symbol++) {
			String[] words = CutoutLexicon.symbolWords(symbol);
			assertTrue(words.length >= 8, CutoutLexicon.symbolName(symbol) + " has " + words.length + " words");
			for (String word : words) {
				assertEquals(word, CutoutLexicon.plain(word), "the lists hold words as they are looked up: lower case, no marks");
				Integer before = seen.put(word, symbol);
				assertTrue(before == null, word + " is mapped to " + CutoutLexicon.symbolName(symbol) + (before == null ? "" : " and to " + CutoutLexicon.symbolName(before)));
				assertEquals(symbol, CutoutLexicon.symbol(word), word);
			}
		}
		assertTrue(seen.size() > 1500, "many words for few symbols: " + seen.size());
	}

	@Test
	void aLineHasOneSymbolAtMost() {
		LyricLine line = line(0, 300, "the", "moon", "and", "the", "stars", "above", "my", "home");
		List<Piece> pieces = pieces(line);
		int symbols = 0;
		for (Piece piece : pieces) {
			symbols += piece.symbol() != CutoutLexicon.NO_SYMBOL ? 1 : 0;
		}
		assertEquals(1, symbols);
		assertEquals(CutoutLexicon.symbol("moon"), pieces.get(1).symbol(), "the first big word that has one keeps it");
	}

	@Test
	void symbolsForEnglishAndGermanWordsWithPlurals() {
		int car = CutoutLexicon.symbol("car");
		assertEquals("car", CutoutLexicon.symbolName(car));
		assertEquals(car, CutoutLexicon.symbol("cars"));
		assertEquals(car, CutoutLexicon.symbol("Cars,"), "capitals and marks around a word do not matter");
		assertEquals(car, CutoutLexicon.symbol("Auto"));
		assertEquals(car, CutoutLexicon.symbol("Autos"));
		assertEquals(CutoutLexicon.symbol("heart"), CutoutLexicon.symbol("Herzen"));
		assertEquals(CutoutLexicon.symbol("night"), CutoutLexicon.symbol("Nächte"));
		assertEquals(CutoutLexicon.symbol("star"), CutoutLexicon.symbol("Sterne"));
		assertEquals(CutoutLexicon.symbol("money"), CutoutLexicon.symbol("Geld"));
		assertEquals(CutoutLexicon.NO_SYMBOL, CutoutLexicon.symbol("because"));
		assertEquals(CutoutLexicon.NO_SYMBOL, CutoutLexicon.symbol(""));
		assertEquals(dev.tim1337.lumio.render.CutoutIcons.COUNT, CutoutLexicon.SYMBOLS_COUNT, "a drawing for every symbol");
		// said around the thing, not only its name: verb forms, slang, German
		assertEquals(CutoutLexicon.symbol("heart"), CutoutLexicon.symbol("loving"));
		assertEquals(CutoutLexicon.symbol("heart"), CutoutLexicon.symbol("verliebt"));
		assertEquals(CutoutLexicon.symbol("heart"), CutoutLexicon.symbol("Baby"));
		assertEquals(CutoutLexicon.symbol("eye"), CutoutLexicon.symbol("looking"));
		assertEquals(CutoutLexicon.symbol("hourglass"), CutoutLexicon.symbol("Zeit"));
		// no weapons, no drugs
		for (String word : new String[] {"gun", "guns", "knife", "pistole", "weed", "cocaine", "drugs", "drogen"}) {
			assertEquals(CutoutLexicon.NO_SYMBOL, CutoutLexicon.symbol(word), word);
		}
		// in a line: the word with a symbol carries it, and with symbols off none does
		LyricLine line = line(0, 300, "all", "of", "my", "cars");
		assertEquals(car, pieces(line).get(3).symbol());
		assertEquals(CutoutLexicon.NO_SYMBOL, CutoutWords.pieces(line, line.words(), 1200, true, false).get(3).symbol());
	}

	/** A line without word timings is shown word by word like any other: single words, the function words small, never three equal chunks. */
	@Test
	void aLineWithoutWordTimingsIsShownWordByWord() {
		LyricLine line = new LyricLine(1000, "I believe in the children, listen");
		List<Piece> pieces = CutoutWords.pieces(line, List.of(), 6000, true, true);
		assertEquals(6, pieces.size(), "every word on its own");
		assertEquals("I", word(line, pieces.get(0)));
		assertEquals("children", word(line, pieces.get(4)), "the comma at its end is not drawn");
		assertEquals("listen", word(line, pieces.get(5)));
		for (int i = 0; i < pieces.size(); i++) {
			Piece piece = pieces.get(i);
			assertFalse(word(line, piece).contains(" "), "a single word, never a phrase");
			assertTrue(piece.startMs() >= 1000 && piece.endMs() <= 6000);
			assertTrue(i == 0 || piece.startMs() >= pieces.get(i - 1).startMs());
		}
		for (int small : new int[] {0, 2, 3}) {
			assertTrue(pieces.get(small).small(), word(line, pieces.get(small)) + " is tucked small beside its neighbour");
			assertFalse(pieces.get(small).tile(), "and never on a tile");
		}
		int tiles = 0;
		for (Piece piece : pieces) {
			tiles += piece.tile() ? 1 : 0;
			assertFalse(piece.tile() && word(line, piece).length() < CutoutTiles.LEAST_LETTERS, "never on a short word");
		}
		assertTrue(tiles <= 1, "one tile at most");
		float largest = 0f;
		for (Piece piece : pieces) {
			largest = Math.max(largest, piece.size());
		}
		for (Piece piece : pieces) {
			assertEquals(piece.key(), piece.size() == largest && largest > CutoutWords.REGULAR, "the key word alone is the largest: " + word(line, piece));
		}
		assertTrue(pieces.get(1).size() != pieces.get(5).size(), "and the sizes are graded, never all the same");
	}

	@Test
	void aTileNeverLiesOnAFunctionWord() {
		String[] lines = {"I believe in the children, listen", "and this one only knows when it starts", "it is in the", "to be or not to be",
			"Wir fahren durch die Nacht und mein Herz brennt", "der die das und in", "the moon and the stars above my home"};
		for (String text : lines) {
			for (boolean timed : new boolean[] {false, true}) {
				LyricLine line = timed ? line(0, 400, text.split(" ")) : new LyricLine(0, text);
				List<Piece> pieces = CutoutWords.pieces(line, line.words(), 6000, true, true);
				List<String> all = new ArrayList<>();
				for (Piece piece : pieces) {
					all.add(word(line, piece));
				}
				boolean known = CutoutLexicon.known(all);
				for (Piece piece : pieces) {
					assertFalse(piece.tile() && CutoutLexicon.small(word(line, piece), known), text + ": a tile on " + word(line, piece));
				}
			}
		}
	}

	@Test
	void theEndOfAWordIsDrawnWithoutCommaOrPeriod() {
		assertEquals(8, CutoutWords.drawnEnd("children, listen.", 0, 9));
		assertEquals(16, CutoutWords.drawnEnd("children, listen.", 10, 17));
		assertEquals(4, CutoutWords.drawnEnd("why? no!", 0, 4), "a question mark stays");
		assertEquals(8, CutoutWords.drawnEnd("why? no!", 5, 8), "and an exclamation mark");
		assertEquals(1, CutoutWords.drawnEnd(", and", 0, 1), "a word of nothing but a mark keeps it");
		LyricLine line = line(0, 400, "running,", "wild.", "tonight?");
		List<Piece> pieces = pieces(line);
		assertEquals("running", word(line, pieces.get(0)));
		assertEquals("wild", word(line, pieces.get(1)));
		assertEquals("tonight?", word(line, pieces.get(2)));
	}

	/**
	 * The same line with real and with estimated timings: the same words small and big, in the same groups; the tile differs on purpose: on the
	 * key word without word timings, none of its own with them (it goes with the word being sung).
	 */
	@Test
	void realAndEstimatedTimingsGiveTheSameLayout() {
		for (String text : new String[] {"the moon and the stars above my home", "I believe in the children listen", "Wir fahren durch die Nacht und mein Herz brennt"}) {
			LyricLine untimed = new LyricLine(0, text);
			// real timings as a singer at an even pace per syllable would give them, slow and fast: what the estimate assumes
			LyricLine real = new LyricLine(0, text, LyricEstimate.words(text, 0, 20_000));
			LyricLine even = new LyricLine(0, text, LyricEstimate.words(text, 0, 5_000));
			List<Piece> estimated = CutoutWords.pieces(untimed, List.of(), 20_000, true, true);
			for (LyricLine timed : new LyricLine[] {real, even}) {
				List<Piece> pieces = CutoutWords.pieces(timed, timed.words(), 20_000, true, true);
				assertEquals(estimated.size(), pieces.size());
				for (int i = 0; i < pieces.size(); i++) {
					Piece a = estimated.get(i);
					Piece b = pieces.get(i);
					String name = text + ": " + word(untimed, a);
					assertEquals(a.from(), b.from(), name);
					assertEquals(a.to(), b.to(), name);
					assertEquals(a.small(), b.small(), name);
					assertEquals(a.group(), b.group(), name);
					assertTrue(!b.tile() || b.key(), name + ": word-synced too, a tile only on the key word");
					assertEquals(a.key(), b.key(), name);
					assertTrue(!a.tile() || a.key(), name + ": line-synced, the tile on the key word");
				}
			}
		}
	}

	@Test
	void theBigWordsOfALineAreNeverAllTheSameSize() {
		for (String text : new String[] {"running wild tonight", "go go run now", "Nacht Herz brennt Feuer", "moon stars home"}) {
			for (boolean timed : new boolean[] {false, true}) {
				LyricLine line = timed ? line(0, 400, text.split(" ")) : new LyricLine(0, text);
				List<Piece> pieces = CutoutWords.pieces(line, line.words(), 6000, true, true);
				java.util.Set<Float> sizes = new java.util.HashSet<>();
				for (Piece piece : pieces) {
					sizes.add(piece.size());
				}
				assertTrue(sizes.size() > 1, text + (timed ? " timed" : " estimated") + ": " + sizes);
			}
		}
	}

	@Test
	void adLibsInBracketsAreSmallAndQuiet() {
		LyricLine line = line(0, 300, "running", "(oh", "yeah)", "tonight");
		List<Piece> pieces = pieces(line);
		assertFalse(pieces.get(0).quiet());
		assertTrue(pieces.get(1).quiet() && pieces.get(2).quiet(), "both words in the brackets");
		assertTrue(pieces.get(1).small());
		assertEquals(CutoutWords.SMALL, pieces.get(1).size());
		assertFalse(pieces.get(3).quiet(), "after the brackets the line goes on");
		assertFalse(pieces.get(1).tile());
	}

	/** Ad-libs are a track of their own: with them or without, every word of the line has the same place, size and time. */
	@Test
	void theLinesWordsAreTheSameWithAdLibsAndWithout() {
		LyricLine plain = line(0, 1000, "running:2000", "forever", "and", "ever");
		MusicService.Background early = new MusicService.Background("oh yeah", List.of(new Word(0, 2, 1200, 1500), new Word(3, 7, 1500, 1900)), 1200, 1900);
		MusicService.Background late = new MusicService.Background("ooh", List.of(), 3200, 3600);
		LyricLine with = new LyricLine(plain.timeMs(), plain.text(), plain.words(), List.of(early, late), MusicService.SINGER_ONE);
		for (int kept = 2; kept <= 5; kept++) {
			List<Piece> alone = CutoutWords.pieces(plain, plain.words(), 5000, true, true);
			List<Piece> beside = CutoutWords.pieces(with, with.words(), 5000, true, true);
			assertEquals(alone, beside, "the same pieces: place in the line, size, tile, symbol and group");
			CutoutStack.Timing a = CutoutStack.timing(alone, kept);
			CutoutStack.Timing b = CutoutStack.timing(beside, kept);
			assertEquals(java.util.Arrays.toString(a.appear()), java.util.Arrays.toString(b.appear()), "and the same times they appear at");
			assertEquals(java.util.Arrays.toString(a.goneFrom()), java.util.Arrays.toString(b.goneFrom()));
			for (int i = 0; i < alone.size(); i++) {
				assertEquals(alone.get(i).startMs(), a.appear()[i] == alone.get(i).startMs() ? alone.get(i).startMs() : a.appear()[i], "never before its own start");
				assertTrue(a.appear()[i] >= alone.get(i).startMs());
			}
		}
	}

	/** Everything stays readable: size differences come from larger key words, never from smaller other words. */
	@Test
	void noWordIsUnderThreeQuartersOfTheRegularSize() {
		String[] lines = {"they said you got 3 or 6 in the Zone", "all of my cars are gone tonight", "ich bin der slime und du bist nie da", "a b c d e f g", "you are the **** one"};
		for (String text : lines) {
			String[] words = text.split(" ");
			LyricLine line = line(0L, 300L, words);
			for (Piece piece : pieces(line)) {
				assertTrue(piece.size() >= 0.75f * CutoutWords.REGULAR, word(line, piece) + " in \"" + text + "\": " + piece.size());
				assertTrue(piece.size() <= 1.4f * CutoutWords.REGULAR + 1e-3f, word(line, piece) + " in \"" + text + "\": " + piece.size());
			}
		}
	}

	@Test
	void numbersAreNeverFillers() {
		assertFalse(CutoutLexicon.small("3", true));
		assertFalse(CutoutLexicon.small("6", false));
		assertFalse(CutoutLexicon.small("2Pac", true));
		LyricLine line = line(0L, 300L, "we", "got", "3", "of", "them");
		for (Piece piece : pieces(line)) {
			if (word(line, piece).equals("3")) {
				assertFalse(piece.small());
				assertTrue(piece.size() >= CutoutWords.REGULAR - 1e-3f);
			}
		}
	}

	@Test
	void aCensoredWordHasTheSizeOfItsNeighbour() {
		LyricLine line = line(0L, 300L, "burning", "****", "tonight");
		List<Piece> pieces = pieces(line);
		assertEquals(CutoutWords.REGULAR, pieces.get(1).size(), 1e-3f);
		LyricLine small = line(0L, 300L, "you", "****", "the", "night");
		assertEquals(pieces(small).get(0).size(), pieces(small).get(1).size(), 1e-3f);
	}
}
