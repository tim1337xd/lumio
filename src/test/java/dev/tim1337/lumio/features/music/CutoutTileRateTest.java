package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** How often a Cut-out tile comes over a whole song: about every two to four lines, in a rap song, a pop song and a line-synced one. */
class CutoutTileRateTest {
	/** A rap verse and hook: many short words at an even pace. */
	private static final String[] RAP = {"I been on the road since the summer started", "counting every mile while the city sleeping",
		"pocket full of plans and a head full of numbers", "never let a doubt in the room where I'm working", "all my people know that the grind never quit",
		"stacking up the pages till the story gets written", "mama said be patient so I waited on the moment", "now the lights are on and the crowd is in motion",
		"every single verse is a brick in the building", "we been underrated but we never stopped climbing", "late night in the studio, coffee going cold",
		"every word I'm writing worth its weight in gold", "they ask me how I made it, I just tell them I was focused", "kept my circle tiny and I kept my vision open",
		"rolling through the avenue with the windows down", "hometown on my back and I'm wearing it proud", "I remember nothing but the hunger in the basement",
		"now I'm on the rooftop and the view is amazing", "keep it moving, keep it moving, never look behind", "every season changes but the music stays mine",
		"tell the world we coming and we coming in numbers", "thunder in the speakers every night of the summer", "this one for the dreamers in the back of the classroom",
		"write it on the wall that we made it to the main room"};
	/** A pop song: verses, a chorus that comes back, held words at the line ends. */
	private static final String[] POP = {"I saw you standing in the light", "your shadow dancing through the night", "we were young and so alive",
		"chasing stars across the sky", "hold me closer, don't let go", "we can run where rivers flow", "under neon, burning bright",
		"we belong to the night", "every heartbeat feels like thunder", "every moment pulls me under", "say my name like a promise",
		"we were wild and we were honest", "hold me closer, don't let go", "we can run where rivers flow", "under neon, burning bright",
		"we belong to the night", "and when the morning comes again", "I'll be waiting where it ends", "hold me closer, don't let go",
		"we can run where rivers flow", "under neon, burning bright", "we belong to the night"};
	/** A ballad with line timings only. */
	private static final String[] BALLAD = {"The winter came and took the leaves", "and left the garden bare", "I wrote your name on frozen glass",
		"and watched it disappear", "the candle burns a little low", "the clock forgets the hour", "but somewhere in the quiet house",
		"a single silver flower", "so sing it softly, sing it slow", "the song we used to know", "and if the river finds the sea",
		"then you will come to me", "the letters in the attic dust", "the photographs in rows", "a melody without the words",
		"that only your heart knows", "so sing it softly, sing it slow", "the song we used to know", "and if the river finds the sea",
		"then you will come to me"};

	/** @return a line with word timings: an even pace, its last word held when {@code holdLast} */
	private static LyricLine timed(long at, String text, long wordMs, boolean holdLast) {
		List<Word> words = new ArrayList<>();
		int from = -1;
		long t = at;
		String[] parts = text.split(" ");
		int index = 0;
		for (int i = 0; i <= text.length(); i++) {
			boolean letter = i < text.length() && text.charAt(i) != ' ';
			if (letter && from < 0) {
				from = i;
			} else if (!letter && from >= 0) {
				boolean last = index == parts.length - 1;
				long length = last && holdLast ? wordMs * 3 : wordMs - 40;
				words.add(new Word(from, i, t, t + length));
				t += length + 40;
				from = -1;
				index++;
			}
		}
		return new LyricLine(at, text, words);
	}

	private static List<LyricLine> song(String[] texts, long wordMs, boolean holdLast, boolean lineSynced) {
		List<LyricLine> lines = new ArrayList<>();
		long at = 1_000L;
		for (String text : texts) {
			lines.add(lineSynced ? new LyricLine(at, text) : timed(at, text, wordMs, holdLast));
			at += text.split(" ").length * wordMs + (holdLast ? 3 * wordMs : 0) + 400L;
		}
		return lines;
	}

	/** @return the tiles per line over a song, and checks the rules on the way */
	private static double rate(String name, List<LyricLine> song) {
		Map<String, CutoutTiles.Choice> plan = CutoutTiles.plan(song);
		int tiles = 0;
		int without = 0;
		int mostWithout = 0;
		for (int i = 0; i < song.size(); i++) {
			LyricLine line = song.get(i);
			long end = i + 1 < song.size() ? song.get(i + 1).timeMs() : line.timeMs() + 4_000L;
			List<CutoutWords.Piece> pieces = CutoutWords.pieces(line, line.words(), end, true, true, plan.get(CutoutTiles.keyOf(line)));
			long onLine = pieces.stream().filter(CutoutWords.Piece::tile).count();
			assertTrue(onLine <= 1, name + ": more than one tile in line " + i);
			for (CutoutWords.Piece piece : pieces) {
				String word = line.text().substring(piece.from(), piece.to());
				if (piece.tile()) {
					assertTrue(CutoutTiles.letters(word) >= CutoutTiles.LEAST_LETTERS, name + ": a tile on a filler: " + word);
					assertFalse(CutoutLexicon.rude(word), name + ": a tile on a swear word: " + word);
				}
			}
			tiles += (int) onLine;
			without = onLine > 0 ? 0 : without + 1;
			mostWithout = Math.max(mostWithout, without);
		}
		double rate = tiles / (double) song.size();
		System.out.printf(java.util.Locale.ROOT, "TILERATE\t%s\t%d tiles in %d lines\tone every %.1f lines\tlongest run without: %d%n", name, tiles, song.size(),
			song.size() / (double) Math.max(1, tiles), mostWithout);
		return rate;
	}

	@Test
	void aTileEveryTwoToFourLines() {
		Object[][] songs = {{"rap", song(RAP, 260L, false, false)}, {"pop", song(POP, 420L, true, false)}, {"line-synced", song(BALLAD, 450L, false, true)}};
		double[] rates = new double[songs.length];
		for (int i = 0; i < songs.length; i++) {
			@SuppressWarnings("unchecked")
			List<LyricLine> lines = (List<LyricLine>) songs[i][1];
			rates[i] = rate((String) songs[i][0], lines);
		}
		for (int i = 0; i < songs.length; i++) {
			Object[] each = songs[i];
			double rate = rates[i];
			assertTrue(rate >= 1.0 / 4.2 && rate <= 1.0 / 1.8, each[0] + ": one tile every " + (1.0 / rate) + " lines");
		}
	}
}
