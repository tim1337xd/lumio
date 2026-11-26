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

/** The Cut-out tile belongs to the key word only: it stamps in when that word is sung, stays with its line, and the sung word never gets a box. */
class CutoutStampTest {
	/** A word-synced line: a word every 400 ms, each sung for 350 ms, the last one held long. */
	static LyricLine timed(String text) {
		List<Word> words = new ArrayList<>();
		int from = -1;
		long at = 0L;
		for (int i = 0; i <= text.length(); i++) {
			boolean letter = i < text.length() && text.charAt(i) != ' ';
			if (letter && from < 0) {
				from = i;
			} else if (!letter && from >= 0) {
				boolean last = i >= text.length();
				words.add(new Word(from, i, at, at + (last ? 1400L : 350L)));
				at += 400L;
				from = -1;
			}
		}
		return new LyricLine(0L, text, words);
	}

	private static String word(LyricLine line, Piece piece) {
		return line.text().substring(piece.from(), piece.to());
	}

	/** @return how strong the tile of a piece is drawn at a time, as the Cut-out draws it, with the word fully there */
	private static float tileOf(Piece piece, long now) {
		return piece.tile() ? CutoutStamp.tile(CutoutStamp.strength(now, piece.startMs()), 1f) : 0f;
	}

	@Test
	void aLineHasOneTileAtMost() {
		for (String text : new String[] {"we keep on driving through the endless summer nights", "running wonderful forever tonight", "hold me closer tonight"}) {
			for (LyricLine line : new LyricLine[] {timed(text), new LyricLine(0L, text)}) {
				List<Piece> pieces = CutoutWords.pieces(line, line.words(), 6000L, true, false);
				long tiles = pieces.stream().filter(Piece::tile).count();
				assertTrue(tiles <= 1, text + ": " + tiles + " tiles");
				pieces.stream().filter(Piece::tile).forEach(piece -> assertTrue(piece.key(), "the tile is on the key word: " + word(line, piece)));
			}
		}
	}

	@Test
	void aLineWithoutAStrongWordHasNoTile() {
		for (String text : new String[] {"oh oh oh", "it is in the", "a b c d", "and you and me"}) {
			for (LyricLine line : new LyricLine[] {timed(text), new LyricLine(0L, text)}) {
				assertTrue(CutoutWords.pieces(line, line.words(), 4000L, true, false).stream().noneMatch(Piece::tile), text);
			}
		}
	}

	@Test
	void theTileStampsInWhenItsWordIsSungAndStays() {
		LyricLine line = timed("we keep on driving through the endless summer nights");
		List<Piece> pieces = CutoutWords.pieces(line, line.words(), 6000L, true, false);
		Piece key = pieces.stream().filter(Piece::tile).findFirst().orElseThrow();
		assertEquals(0f, tileOf(key, key.startMs() - 1L), "no tile before its word is sung");
		assertTrue(CutoutStamp.scale(CutoutStamp.progress(key.startMs(), key.startMs())) > 1.1f, "it starts larger");
		assertTrue(tileOf(key, key.startMs()) < 0.7f, "and see-through");
		assertEquals(1f, CutoutStamp.scale(CutoutStamp.progress(key.startMs() + CutoutStamp.STAMP_MILLIS, key.startMs())), 1e-4f, "settled");
		float before = 0f;
		for (long now = key.startMs(); now < key.startMs() + CutoutStamp.STAMP_MILLIS; now += 5L) {
			float scale = CutoutStamp.scale(CutoutStamp.progress(now, key.startMs()));
			assertTrue(scale >= 1f, "no bounce below its size at " + now);
			assertTrue(tileOf(key, now) >= before, "it only grows stronger");
			before = tileOf(key, now);
		}
		// placed, it stays for as long as the line is there: long after its word and the whole line were sung
		for (long now = key.startMs() + CutoutStamp.STAMP_MILLIS; now < 60_000L; now += 250L) {
			assertEquals(1f, tileOf(key, now), 1e-4f, "gone at " + now);
		}
		// it leaves only with its line, as the words go
		assertEquals(0f, CutoutStamp.tile(1f, 0f), "gone with its word's way out");
	}

	@Test
	void noTileOnFillersOrSwearWords() {
		for (String text : new String[] {"this whole thing is shit tonight", "fucking beautiful", "I am in love with you", "yeah yeah yeah baby"}) {
			for (LyricLine line : new LyricLine[] {timed(text), new LyricLine(0L, text)}) {
				for (Piece piece : CutoutWords.pieces(line, line.words(), 4000L, true, false)) {
					String word = word(line, piece);
					if (piece.tile()) {
						assertFalse(CutoutLexicon.rude(word), "a tile on a swear word: " + word);
						assertTrue(CutoutTiles.letters(word) >= CutoutTiles.LEAST_LETTERS, "a tile on a filler: " + word);
						assertFalse(piece.small(), "a tile on a small word: " + word);
					}
				}
			}
		}
	}

	@Test
	void theSungWordNeverGetsABox() {
		LyricLine line = timed("tonight forever running wonderful");
		List<Piece> pieces = CutoutWords.pieces(line, line.words(), 3000L, true, false);
		for (Piece sung : pieces) {
			long middle = sung.startMs() + 200L;
			if (!sung.tile()) {
				assertEquals(0f, tileOf(sung, middle), word(line, sung) + " got a box while sung");
			}
			for (Piece other : pieces) {
				if (other != sung && !other.tile()) {
					assertEquals(0f, tileOf(other, middle));
				}
			}
		}
	}
}
