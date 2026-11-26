package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * No word of a sung line is skipped in a small room: a line with a phrase twice is laid out as normal text, every word in order, and the same
 * line sung three times in a row never lands where the room no longer shows (the cause of "all the" alone, with "we left" and the words before
 * it never seen).
 */
class CutoutRowStackTest {
	private static final float ROW = 30f;
	private static final float GAP = 12f;
	/** Room for two rows only. */
	private static final float ROOM = 2 * ROW + GAP;

	/**
	 * Stacks the rows of lines as the column does, each line's rows sung one after the other: the room's bottom follows the newest row placed
	 * (the scroll moves a row to the bottom as it starts; a row in its earlier copy's place does not move it). @return for each row whether it
	 * stood wholly in the room at the time it was sung
	 */
	private static boolean[] sungInRoom(int[] rowsPerLine, boolean[] sameAsBefore) {
		int rows = Arrays.stream(rowsPerLine).sum();
		float[] tops = new float[rows];
		float[] heights = new float[rows];
		float[] fits = new float[rows];
		int[] repeatOf = new int[rows];
		Arrays.fill(repeatOf, -1);
		int[] firstOf = new int[rowsPerLine.length];
		int r = 0;
		for (int line = 0; line < rowsPerLine.length; line++) {
			firstOf[line] = r;
			for (int g = 0; g < rowsPerLine[line]; g++) {
				fits[r] = ROW;
				heights[r] = ROW + (g == rowsPerLine[line] - 1 ? GAP : 0f);
				repeatOf[r] = sameAsBefore[line] && rowsPerLine[line - 1] == rowsPerLine[line] ? firstOf[line - 1] + g : -1;
				r++;
			}
		}
		boolean[] seen = new boolean[rows];
		float bottom = 0f;
		float roomBottom = 0f;
		for (int line = 0; line < rowsPerLine.length; line++) {
			int from = firstOf[line];
			int last = from + rowsPerLine[line] - 1;
			bottom = CutoutRowStack.placeLine(tops, heights, fits, repeatOf, from, last, bottom, ROOM);
			for (int row = from; row <= last; row++) {
				if (repeatOf[row] < 0) {
					roomBottom = Math.max(roomBottom, tops[row] + fits[row]);
				}
				seen[row] = tops[row] >= roomBottom - ROOM - 0.01f && tops[row] + fits[row] <= roomBottom + 0.01f;
			}
		}
		return seen;
	}

	@Test
	void theSameLineThreeTimesInARowShowsEveryRow() {
		// a two-row line sung three times: the second and third copies would have gone into a place already scrolled out
		boolean[] seen = sungInRoom(new int[] {2, 2, 2, 2}, new boolean[] {false, false, true, true});
		for (int row = 0; row < seen.length; row++) {
			assertTrue(seen[row], "row " + row + " was not in the room when sung");
		}
		// a three-row line in a room of two: its first row has scrolled out when it is sung again; the repeat must not go there
		boolean[] tall = sungInRoom(new int[] {3, 3, 3}, new boolean[] {false, true, true});
		for (int row = 0; row < tall.length; row++) {
			assertTrue(tall[row], "row " + row + " of the three-row line was not in the room when sung");
		}
		// one-row lines in a row may stand in place: the earlier copy is still in the room
		boolean[] short3 = sungInRoom(new int[] {1, 1, 1}, new boolean[] {false, true, true});
		for (boolean each : short3) {
			assertTrue(each);
		}
	}

	@Test
	void aLineWithAPhraseTwiceIsNormalTextEveryWordInOrder() {
		String text = "we left all the pain behind, we left all the pain behind";
		LyricLine line = CutoutStampTest.timed(text);
		List<Piece> pieces = CutoutWords.pieces(line, line.words(), 6000L, true, false);
		List<String> words = new ArrayList<>();
		for (Piece piece : pieces) {
			words.add(text.substring(piece.from(), piece.to()).replace(",", ""));
		}
		assertEquals(List.of("we", "left", "all", "the", "pain", "behind", "we", "left", "all", "the", "pain", "behind"), words);
		long before = Long.MIN_VALUE;
		for (Piece piece : pieces) {
			assertTrue(piece.startMs() >= before, "in order of their times");
			before = piece.startMs();
		}
		// laid out as an earlier line, its pieces stay its own: none is dropped
		CutoutRepeats repeats = new CutoutRepeats();
		Object song = new Object();
		List<String> plain = new ArrayList<>();
		for (String word : words) {
			plain.add(word.toLowerCase(java.util.Locale.ROOT));
		}
		repeats.lay(song, "we left all the pain behind", plain.subList(0, 6), pieces.subList(0, 6));
		assertEquals(pieces.size(), repeats.lay(song, text, plain, pieces).pieces().size());
		// as three rows in a room of two, every row is in the room when it is sung
		for (boolean each : sungInRoom(new int[] {3}, new boolean[] {false})) {
			assertTrue(each);
		}
	}

	@Test
	void estimatedTimingsAndAdLibsKeepEveryWord() {
		LyricLine plain = new LyricLine(0L, "we left all the pain behind (oh)");
		List<Piece> pieces = CutoutWords.pieces(plain, plain.words(), 6000L, true, false);
		String joined = String.join(" ", pieces.stream().map(piece -> plain.text().substring(piece.from(), piece.to())).toList());
		for (String word : new String[] {"we", "left", "all", "the", "pain", "behind"}) {
			assertTrue(joined.contains(word), word + " missing: " + joined);
		}
	}
}
