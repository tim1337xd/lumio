package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Cut-out page reads as one composition: no word of three letters or fewer alone in a row, small words on their main word's baseline, rows
 * centered on one axis, nudged a little without jumps or overlap, straight and level, three sizes at most, and the same line always laid out the same.
 */
class CutoutLayoutTest {
	private static final float COLUMN = CutoutLyrics.COLUMN;
	/** A row of words too long for the column reaches out of it at the least size rather than get smaller. */
	private static final float TOLERATED_OVER = COLUMN / 2f;
	private static final float PAD = 6f;
	/** Lines as songs have them, English and German, with short words in every place. */
	private static final String[] SONG = {
		"Oh I don't know what to do", "I am in love with you", "and we run to the city lights tonight", "Ich bin so müde du bist so weit",
		"Wir fahren durch die Nacht und mein Herz brennt", "cry for me", "Susanna, oh don't you cry for me", "go go go", "it is what it is",
		"Ich und du für immer", "I see the sun in your eyes", "Yeah yeah oh no", "Baby I need you now", "tell me why", "in the end"};

	/** A line with a word every 300 ms, a little longer for the last. */
	private static LyricLine timed(String text, long startMs) {
		List<Word> words = new ArrayList<>();
		int from = -1;
		long at = startMs;
		for (int i = 0; i <= text.length(); i++) {
			boolean letter = i < text.length() && text.charAt(i) != ' ';
			if (letter && from < 0) {
				from = i;
			} else if (!letter && from >= 0) {
				long length = i == text.length() ? 700L : 280L;
				words.add(new Word(from, i, at, at + length));
				at += 300L;
				from = -1;
			}
		}
		return new LyricLine(startMs, text, words);
	}

	private static List<Piece> pieces(LyricLine line) {
		return CutoutWords.pieces(line, line.words(), line.timeMs() + 4_000L, true, true);
	}

	/** Boxes as the drawing makes them: widths by their letters, rows by their sizes. */
	private static CutoutArrange.Box[] boxes(List<Piece> pieces) {
		CutoutArrange.Box[] boxes = new CutoutArrange.Box[pieces.size()];
		for (int i = 0; i < boxes.length; i++) {
			Piece piece = pieces.get(i);
			CutoutArrange.Box box = new CutoutArrange.Box();
			box.onTile = piece.tile();
			float natural = (piece.to() - piece.from()) * 11f;
			box.size = CutoutWords.fitted(piece.size(), natural, COLUMN);
			box.width = natural * box.size;
			box.tall = 40.5f * box.size;
			box.ink = new float[] {-box.width / 2f, -15f * box.size, box.width / 2f, 9f * box.size};
			box.tile = CutoutWords.tile(box.ink, PAD * box.size);
			boxes[i] = box;
		}
		return boxes;
	}

	@Test
	void noRowIsOnlyAWordOfThreeLettersOrFewer() {
		for (String text : SONG) {
			LyricLine line = timed(text, 0L);
			List<Piece> pieces = pieces(line);
			if (pieces.size() < 2) {
				continue;
			}
			for (int group = 0; group < CutoutWords.groups(pieces); group++) {
				List<String> row = new ArrayList<>();
				for (Piece piece : pieces) {
					if (piece.group() == group) {
						row.add(text.substring(piece.from(), piece.to()));
					}
				}
				assertTrue(row.size() > 1 || CutoutLexicon.plain(row.getFirst()).length() > CutoutWords.LONE_LETTERS,
					"\"" + text + "\": a row of only " + row);
			}
		}
	}

	@Test
	void eachRowHasOneMainWordAndItsSmallWordsOnItsBaseline() {
		for (String text : SONG) {
			List<Piece> pieces = pieces(timed(text, 0L));
			CutoutArrange.Box[] boxes = boxes(pieces);
			CutoutArrange.column(pieces, boxes, COLUMN, PAD, text.hashCode());
			for (int group = 0; group < CutoutWords.groups(pieces); group++) {
				int mains = 0;
				float baseline = Float.NaN;
				for (int i = 0; i < pieces.size(); i++) {
					if (pieces.get(i).group() != group) {
						continue;
					}
					mains += pieces.get(i).small() ? 0 : 1;
					float own = boxes[i].y + boxes[i].tall * CutoutArrange.BASELINE;
					if (Float.isNaN(baseline)) {
						baseline = own;
					}
					assertEquals(baseline, own, 1e-3f, "\"" + text + "\": every word of a row on one level baseline");
				}
				assertTrue(mains >= 1 || CutoutWords.groups(pieces) == 1, "\"" + text + "\": a row with no main word");
			}
		}
	}

	@Test
	void rowsAreCenteredNudgedALittleAndLevelWithoutOverlap() {
		float most = CutoutArrange.NUDGE * COLUMN;
		for (String text : SONG) {
			List<Piece> pieces = pieces(timed(text, 0L));
			CutoutArrange.Box[] boxes = boxes(pieces);
			float[] heights = CutoutArrange.column(pieces, boxes, COLUMN, PAD, text.hashCode());
			float before = Float.NaN;
			float above = -Float.MAX_VALUE;
			float top = 0f;
			for (int group = 0; group < heights.length; group++) {
				float left = Float.MAX_VALUE;
				float right = -Float.MAX_VALUE;
				float rowTop = Float.MAX_VALUE;
				float rowBottom = -Float.MAX_VALUE;
				float pivot = Float.NaN;
				for (int i = 0; i < pieces.size(); i++) {
					if (pieces.get(i).group() == group) {
						float[] box = CutoutArrange.box(boxes[i], pieces.get(i));
						left = Math.min(left, box[0]);
						right = Math.max(right, box[2]);
						rowTop = Math.min(rowTop, top + box[1]);
						rowBottom = Math.max(rowBottom, top + box[3]);
						pivot = boxes[i].pivot;
					}
				}
				assertTrue(Math.abs(pivot) <= most + 1e-3f, "\"" + text + "\": a row nudged by " + pivot);
				assertTrue(Float.isNaN(before) || Math.abs(pivot - before) <= most + 1e-3f, "\"" + text + "\": from " + before + " to " + pivot);
				assertTrue(left >= -COLUMN / 2f - TOLERATED_OVER && right <= COLUMN / 2f + TOLERATED_OVER, "\"" + text + "\": inside the column");
				assertTrue(rowTop >= above - 1e-3f, "\"" + text + "\": row " + group + " over the row above it");
				before = pivot;
				above = rowBottom;
				top += heights[group];
			}
		}
	}

	@Test
	void noGapIsUnderTheLeastForMixedSizesTilesAndOneLetterWords() {
		List<String> lines = new ArrayList<>(List.of(SONG));
		lines.addAll(List.of("you had it on", "every day in New York", "I saw a girl in a red car", "a I o u", "hold me tight, I won't let you go tonight"));
		for (String text : lines) {
			List<Piece> pieces = pieces(timed(text, 0L));
			CutoutArrange.Box[] boxes = boxes(pieces);
			CutoutArrange.column(pieces, boxes, COLUMN, PAD, text.hashCode());
			for (int i = 1; i < pieces.size(); i++) {
				if (pieces.get(i).group() != pieces.get(i - 1).group()) {
					continue;
				}
				// between the drawn letters, a tile by its edge (its padding counts), and a word's symbol with it
				float gap = CutoutArrange.box(boxes[i], pieces.get(i))[0] - CutoutArrange.box(boxes[i - 1], pieces.get(i - 1))[2];
				float larger = Math.max(CutoutArrange.em(boxes[i]), CutoutArrange.em(boxes[i - 1]));
				float smaller = Math.min(CutoutArrange.em(boxes[i]), CutoutArrange.em(boxes[i - 1]));
				float least = Math.max(CutoutArrange.GAP_EM * larger, CutoutArrange.SPACE_EM * smaller);
				assertTrue(gap >= least - 1e-3f, "\"" + text + "\": \"" + text.substring(pieces.get(i - 1).from(), pieces.get(i - 1).to()) + "\" and \""
					+ text.substring(pieces.get(i).from(), pieces.get(i).to()) + "\" only " + gap + " apart, least " + least);
			}
		}
	}

	@Test
	void aLineStandsInRowsInSungOrder() {
		for (String text : SONG) {
			List<Piece> pieces = pieces(timed(text, 0L));
			int rows = CutoutWords.groups(pieces);
			assertTrue(rows >= 1, "\"" + text + "\": " + rows + " rows");
			CutoutArrange.Box[] boxes = boxes(pieces);
			CutoutArrange.column(pieces, boxes, COLUMN, PAD, text.hashCode());
			for (int i = 1; i < pieces.size(); i++) {
				// down the rows, and left to right within a row: the order they are sung in
				assertTrue(pieces.get(i).group() >= pieces.get(i - 1).group(), "\"" + text + "\": rows out of order at " + i);
				if (pieces.get(i).group() == pieces.get(i - 1).group()) {
					assertTrue(boxes[i].x > boxes[i - 1].x, "\"" + text + "\": word " + i + " left of the one sung before it");
				}
			}
		}
	}

	@Test
	void aLongLineBreaksBetweenPhrases() {
		String text = "we were running through the streets tonight, and the stars were shining over every single roof";
		List<Piece> pieces = pieces(timed(text, 0L));
		assertTrue(CutoutWords.groups(pieces) >= CutoutWords.MOST_ROWS, "a long line takes more rows, its words keep their size");
		int and = text.indexOf("and the");
		for (Piece piece : pieces) {
			if (piece.from() == and) {
				int before = -1;
				for (Piece other : pieces) {
					before = other.to() <= text.indexOf("tonight,") + 7 ? other.group() : before;
				}
				assertTrue(piece.group() > before, "a new row starts after the comma, with \"and\"");
			}
		}
	}

	/** A row with a symbol and small words on both sides gives way as a whole until it fits: nothing of it reaches out of the column. */
	@Test
	void aRowWithASymbolAndSmallWordsFitsTheColumn() {
		String[] lines = {"take the long way home with me", "and all of the stars in the sky with you tonight", "Donaudampfschifffahrtsgesellschaft und ich und du"};
		for (String text : lines) {
			List<Piece> pieces = pieces(timed(text, 0L));
			CutoutArrange.Box[] boxes = boxes(pieces);
			CutoutArrange.column(pieces, boxes, COLUMN, PAD, text.hashCode());
			for (int i = 0; i < boxes.length; i++) {
				float[] box = CutoutArrange.box(boxes[i], pieces.get(i));
				float left = pieces.get(i).symbol() != CutoutLexicon.NO_SYMBOL ? Math.min(box[0], boxes[i].symbolX - boxes[i].symbolHalf) : box[0];
				assertTrue(left >= -COLUMN / 2f - 2f * PAD && box[2] <= COLUMN / 2f + 2f * PAD, text + ": a word reaches out of the column");
			}
		}
	}

	@Test
	void neighboursKeepAGapByTheirDrawnLettersAlsoANarrowI() {
		String[] lines = {"I drive", "I want it all", "and I drive all night", "I love you so", "! I l 1 go"};
		for (String text : lines) {
			List<Piece> pieces = pieces(timed(text, 0L));
			CutoutArrange.Box[] boxes = boxes(pieces);
			CutoutArrange.column(pieces, boxes, COLUMN, PAD, text.hashCode());
			for (int i = 1; i < pieces.size(); i++) {
				if (pieces.get(i).group() != pieces.get(i - 1).group()) {
					continue;
				}
				float[] left = CutoutArrange.box(boxes[i - 1], pieces.get(i - 1));
				float[] right = CutoutArrange.box(boxes[i], pieces.get(i));
				float gap = right[0] - left[2];
				String name = "\"" + text + "\": " + text.substring(pieces.get(i - 1).from(), pieces.get(i - 1).to()) + " | "
					+ text.substring(pieces.get(i).from(), pieces.get(i).to());
				assertTrue(gap >= CutoutArrange.SMALL_GAP * 0.5f - 1e-3f, name + ": only " + gap + " apart");
			}
		}
	}

	@Test
	void threeSizesAtMost() {
		Set<Float> sizes = new HashSet<>();
		long at = 0L;
		for (String text : SONG) {
			for (Piece piece : pieces(timed(text, at))) {
				sizes.add(piece.size());
			}
			at += 5_000L;
		}
		assertTrue(sizes.size() <= 3, "sizes over the song: " + sizes);
	}

	@Test
	void theSameLineIsLaidOutTheSameEveryTime() {
		for (String text : SONG) {
			List<Piece> once = pieces(timed(text, 1_000L));
			List<Piece> again = pieces(timed(text, 1_000L));
			assertEquals(once, again);
			CutoutArrange.Box[] a = boxes(once);
			CutoutArrange.Box[] b = boxes(again);
			CutoutArrange.column(once, a, COLUMN, PAD, text.hashCode());
			CutoutArrange.column(again, b, COLUMN, PAD, text.hashCode());
			for (int i = 0; i < a.length; i++) {
				assertEquals(a[i].x, b[i].x);
				assertEquals(a[i].y, b[i].y);
			}
		}
	}
}
