package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fewer, smarter tiles: never on a short word or a function word, one a line at most, only on a strong key word. */
class CutoutTilesTest {
	/** A sample song: verses and a chorus that comes back, English and German, short and long lines. */
	private static final String[] SONG = {"I believe in the children, listen", "and this one only knows when it starts", "we were running through the city lights",
		"hold me closer tonight", "never let me go", "running through the city lights", "the moon and the stars above my home", "oh oh oh",
		"Wir fahren durch die Nacht und mein Herz brennt", "under the falling stars tonight", "running through the city lights", "all of my cars are gone",
		"I just threw twenty, count it, we were rolling", "hold me closer tonight", "it is in the", "a b c d", "Feuer und Flamme in der Dunkelheit",
		"running through the city lights", "we keep on driving through the endless summer nights", "goodbye"};

	private static List<LyricLine> song(boolean timed) {
		List<LyricLine> lines = new ArrayList<>();
		long at = 1_000;
		for (String text : SONG) {
			long length = 600L + 380L * text.split(" ").length;
			lines.add(timed ? new LyricLine(at, text, LyricEstimate.words(text, at, at + length)) : new LyricLine(at, text));
			at += length + 200;
		}
		return lines;
	}

	private static List<List<Piece>> pieces(List<LyricLine> song) {
		Map<String, CutoutTiles.Choice> plan = CutoutTiles.plan(song);
		List<List<Piece>> all = new ArrayList<>();
		for (int i = 0; i < song.size(); i++) {
			LyricLine line = song.get(i);
			long end = i + 1 < song.size() ? song.get(i + 1).timeMs() : line.timeMs() + 3000;
			all.add(CutoutWords.pieces(line, line.words(), end, true, true, plan.get(CutoutTiles.keyOf(line))));
		}
		return all;
	}

	private static String word(LyricLine line, Piece piece) {
		return line.text().substring(piece.from(), piece.to());
	}

	@Test
	void noTileOnAShortWordOrAFunctionWord() {
		for (boolean timed : new boolean[] {false, true}) {
			List<LyricLine> song = song(timed);
			List<List<Piece>> all = pieces(song);
			for (int i = 0; i < all.size(); i++) {
				List<String> words = new ArrayList<>();
				for (Piece piece : all.get(i)) {
					words.add(word(song.get(i), piece));
				}
				boolean known = CutoutLexicon.known(words);
				for (Piece piece : all.get(i)) {
					String word = word(song.get(i), piece);
					if (piece.tile()) {
						assertTrue(CutoutTiles.letters(word) >= 4, "a tile on a short word: " + word);
						assertFalse(CutoutLexicon.small(word, known), "a tile on a function word: " + word);
					}
				}
			}
		}
		assertEquals(4, CutoutTiles.letters("Herz"));
		assertEquals(4, CutoutTiles.letters("über,"), "an umlaut counts, a comma does not");
		assertFalse(CutoutTiles.eligible("the", true));
		assertFalse(CutoutTiles.eligible("und", true));
		assertFalse(CutoutTiles.eligible("from", true), "a function word, whatever its length");
	}

	@Test
	void bothKindsHaveOneTileAtMostOnAStrongKeyWord() {
		for (boolean timed : new boolean[] {false, true}) {
			List<LyricLine> song = song(timed);
			List<List<Piece>> all = pieces(song);
			int withTile = 0;
			for (int i = 0; i < all.size(); i++) {
				int tiles = 0;
				for (Piece piece : all.get(i)) {
					tiles += piece.tile() ? 1 : 0;
					if (piece.tile()) {
						assertTrue(piece.key(), "the tile is on the key word: " + word(song.get(i), piece));
					}
				}
				assertTrue(tiles <= 1, "line " + i + " has " + tiles + " tiles");
				withTile += tiles;
			}
			assertTrue(withTile >= all.size() / 5 && withTile <= (all.size() + 1) / 2, (timed ? "word" : "line") + "-synced: a tile every two to four lines or so: " + withTile + " in " + all.size());
		}
	}

	@Test
	void theScoreFavoursHeldLastChorusAndRareWordsAndAvoidsSymbols() {
		String text = "baby running wonderfully";
		List<MusicService.Word> words = List.of(new MusicService.Word(0, 4, 0, 300), new MusicService.Word(5, 12, 300, 1600), new MusicService.Word(13, 24, 1600, 1900));
		float[] scores = CutoutTiles.scores(text, words, java.util.Set.of());
		assertTrue(scores[1] > scores[0], "held long beats a common word");
		float[] chorus = CutoutTiles.scores(text, words, java.util.Set.of("wonderfully"));
		assertTrue(chorus[2] > scores[2], "a word of the chorus scores higher");
		float[] symbol = CutoutTiles.scores("heart wonder", List.of(new MusicService.Word(0, 5, 0, 400), new MusicService.Word(6, 12, 400, 800)), java.util.Set.of());
		assertTrue(symbol[0] < symbol[1], "a word with a symbol scores lower: " + symbol[0] + " " + symbol[1]);
		assertEquals(Float.NEGATIVE_INFINITY, CutoutTiles.scores("in the", List.of(new MusicService.Word(0, 2, 0, 900), new MusicService.Word(3, 6, 900, 1000)),
			java.util.Set.of())[0]);
	}

	@Test
	void theKeyWordIsTheBestScoredWord() {
		List<LyricLine> song = song(false);
		List<List<Piece>> all = pieces(song);
		for (int i = 0; i < all.size(); i++) {
			for (Piece piece : all.get(i)) {
				if (piece.tile()) {
					assertTrue(piece.key(), "the tile goes to the key word: " + word(song.get(i), piece));
				}
			}
		}
	}

	@Test
	void roundedCornersKeepTheLettersInsideWithTheirPadding() {
		for (String word : new String[] {"knew", "www", "ÜBER", "children", "Herz"}) {
			float[] ink = dev.tim1337.lumio.render.CutoutInk.bounds(word);
			if (ink == null) {
				continue;
			}
			float size = 40f;
			float[] letters = {ink[0] * size, ink[1] * size, ink[2] * size, ink[3] * size};
			float pad = CutoutLyrics.TILE_PAD;
			float[] tile = CutoutWords.tile(letters, pad);
			float radius = (tile[3] - tile[1]) * CutoutLyrics.TILE_ROUND;
			assertTrue(radius < (tile[3] - tile[1]) / 2f * 0.5f, "a box, never a pill");
			// every corner of the letters lies inside the rounded tile
			for (float x : new float[] {letters[0], letters[2]}) {
				for (float y : new float[] {letters[1], letters[3]}) {
					float cx = Math.clamp(x, tile[0] + radius, tile[2] - radius);
					float cy = Math.clamp(y, tile[1] + radius, tile[3] - radius);
					assertTrue(Math.hypot(x - cx, y - cy) <= radius, word + ": a letter reaches into a rounded corner");
				}
			}
		}
	}
}
