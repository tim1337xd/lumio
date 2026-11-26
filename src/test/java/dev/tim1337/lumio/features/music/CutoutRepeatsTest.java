package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A line sung again looks as it did: twice in a row, with a word changed, and a chorus that comes back later; its times are always its own. */
class CutoutRepeatsTest {
	private final Object song = new Object();

	/** A word-synced line from a start, a word every {@code step} ms, each held a little differently so the timings differ from copy to copy. */
	private static LyricLine timed(String text, long startMs, long step, long held) {
		List<Word> words = new ArrayList<>();
		int from = -1;
		long at = startMs;
		for (int i = 0; i <= text.length(); i++) {
			boolean letter = i < text.length() && text.charAt(i) != ' ';
			if (letter && from < 0) {
				from = i;
			} else if (!letter && from >= 0) {
				words.add(new Word(from, i, at, at + held));
				at += step;
				from = -1;
			}
		}
		return new LyricLine(startMs, text, words);
	}

	private CutoutRepeats.Laid lay(CutoutRepeats repeats, LyricLine line) {
		List<Piece> pieces = CutoutWords.pieces(line, line.words(), line.timeMs() + 8_000L, true, true);
		List<String> words = new ArrayList<>();
		for (Piece piece : pieces) {
			words.add(CutoutLexicon.plain(line.text().substring(piece.from(), piece.to())).toLowerCase(Locale.ROOT));
		}
		return repeats.lay(song, line.text(), words, pieces);
	}

	private static void sameLayout(CutoutRepeats.Laid a, CutoutRepeats.Laid b, String name) {
		assertEquals(a.seed(), b.seed(), name + ": the same nudges");
		assertEquals(a.pieces().size(), b.pieces().size(), name);
		for (int i = 0; i < a.pieces().size(); i++) {
			Piece x = a.pieces().get(i);
			Piece y = b.pieces().get(i);
			assertEquals(x.group(), y.group(), name + ": word " + i + " in another row");
			assertEquals(x.size(), y.size(), name + ": word " + i + " of another size");
			assertEquals(x.small(), y.small(), name + ": word " + i);
			assertEquals(x.key(), y.key(), name + ": word " + i);
			assertEquals(x.tile(), y.tile(), name + ": word " + i);
		}
	}

	@Test
	void theSameLineTwiceInARowLooksTheSameWithItsOwnTimes() {
		CutoutRepeats repeats = new CutoutRepeats();
		LyricLine first = timed("hold on to the night forever", 0L, 300L, 280L);
		// sung again right after, slower, one word held long: by itself it would be laid out otherwise
		LyricLine again = timed("Hold on to the night, forever!", 4_000L, 420L, 900L);
		CutoutRepeats.Laid a = lay(repeats, first);
		CutoutRepeats.Laid b = lay(repeats, again);
		sameLayout(a, b, "twice in a row");
		for (int i = 0; i < b.pieces().size(); i++) {
			assertEquals(again.words().get(i).startMs(), b.pieces().get(i).startMs(), "word " + i + " comes at its own time");
			assertEquals(again.words().get(i).endMs(), b.pieces().get(i).endMs(), "word " + i + " ends at its own time");
		}
		assertTrue(LyricChorus.sameLine(first.text(), again.text()), "case and marks aside, it is the same line");
	}

	@Test
	void aLineWithAWordChangedKeepsTheLayoutOfTheOneBefore() {
		CutoutRepeats repeats = new CutoutRepeats();
		CutoutRepeats.Laid a = lay(repeats, timed("I would give you anything tonight", 0L, 300L, 280L));
		LyricLine changed = timed("I would give you everything tonight", 3_000L, 300L, 280L);
		CutoutRepeats.Laid b = lay(repeats, changed);
		sameLayout(a, b, "one word changed");
		// the changed word is its own: its own letters
		int index = 4;
		assertEquals("everything", changed.text().substring(b.pieces().get(index).from(), b.pieces().get(index).to()));
		assertFalse(LyricChorus.sameLine("I would give you anything tonight", changed.text()), "and it is not the same line: its own block");
	}

	@Test
	void aChorusThatComesBackLaterLooksAsItDidTheFirstTime() {
		CutoutRepeats repeats = new CutoutRepeats();
		CutoutRepeats.Laid chorus = lay(repeats, timed("we are the champions of the world", 0L, 300L, 280L));
		lay(repeats, timed("no time for losers", 4_000L, 300L, 280L));
		lay(repeats, timed("cause we are the champions", 8_000L, 300L, 280L));
		CutoutRepeats.Laid back = lay(repeats, timed("We are the champions (yeah) of the world", 60_000L, 350L, 600L));
		// the ad-lib in brackets is no word of the line: the same words, the same layout
		sameLayout(chorus, withoutAdlibs(back), "the chorus again");
	}

	@Test
	void linesThatDifferMoreAreTheirOwn() {
		CutoutRepeats repeats = new CutoutRepeats();
		CutoutRepeats.Laid a = lay(repeats, timed("running through the city lights", 0L, 300L, 280L));
		CutoutRepeats.Laid b = lay(repeats, timed("walking past the river banks", 3_000L, 300L, 280L));
		assertNotEquals(a.seed(), b.seed(), "another line, its own nudges");
		// a short line needs the same words: two of three changed is another line
		CutoutRepeats.Laid c = lay(repeats, timed("go go go", 6_000L, 300L, 280L));
		CutoutRepeats.Laid d = lay(repeats, timed("no no go", 7_000L, 300L, 280L));
		assertNotEquals(c.seed(), d.seed());
	}

	@Test
	void aNewSongForgetsTheLayouts() {
		CutoutRepeats repeats = new CutoutRepeats();
		CutoutRepeats.Laid a = lay(repeats, timed("hold on to the night forever", 0L, 300L, 280L));
		List<Piece> pieces = CutoutWords.pieces(timed("hold on to the night forever", 0L, 900L, 880L), List.of(), 8_000L, true, true);
		List<String> words = new ArrayList<>();
		for (Piece piece : pieces) {
			words.add("x" + piece.from());
		}
		CutoutRepeats.Laid other = repeats.lay(new Object(), "a new song begins here now", words, pieces);
		assertNotEquals(a.seed(), other.seed());
	}

	/** @return a line's layout without its ad-libs in brackets (quiet pieces), which the earlier copy did not have */
	private static CutoutRepeats.Laid withoutAdlibs(CutoutRepeats.Laid laid) {
		List<Piece> own = new ArrayList<>();
		for (Piece piece : laid.pieces()) {
			if (!piece.quiet()) {
				own.add(piece);
			}
		}
		return new CutoutRepeats.Laid(own, laid.seed());
	}
}
