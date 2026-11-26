package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * The symbols of 2026-11-17: many words each, English and German, brand names beside the thing in general, the forms songs use, and still one
 * symbol a line at most.
 */
class CutoutSymbolsTest {
	private static final Set<String> NEW = Set.of("smoke", "wristwatch", "hanger", "pray", "storm", "record", "siren", "palmtree", "spotlight",
		"dumbbell");
	/** Brand names, and the general symbols they may stand beside. */
	private static final String[] BRANDS = {"porsche", "benz", "mercedes", "amg", "maybach", "bmw", "audi", "lambo", "ferrari", "bentley", "rolex",
		"patek", "gucci", "prada", "balenciaga", "versace", "dior", "chanel", "nike", "adidas", "jordans", "yeezy", "spotify", "iphone"};
	private static final Set<String> GENERAL = Set.of("car", "wristwatch", "hanger", "shoe", "headphones", "phone");

	private static String name(String word) {
		int symbol = CutoutLexicon.symbol(word);
		return symbol == CutoutLexicon.NO_SYMBOL ? "none" : CutoutLexicon.symbolName(symbol);
	}

	@Test
	void theNewSymbolsHaveManyWords() {
		for (int symbol = 0; symbol < CutoutLexicon.SYMBOLS_COUNT; symbol++) {
			String name = CutoutLexicon.symbolName(symbol);
			int words = CutoutLexicon.symbolWords(symbol).length;
			assertTrue(words >= 8, name + " has " + words);
			if (NEW.contains(name)) {
				assertTrue(words >= 20, name + " has " + words + " words, at least 20 wanted");
			}
		}
		assertEquals(dev.tim1337.lumio.render.CutoutIcons.COUNT, CutoutLexicon.SYMBOLS_COUNT, "a drawing for every symbol");
	}

	@Test
	void brandNamesStandBesideTheThingInGeneral() {
		for (String brand : BRANDS) {
			assertTrue(GENERAL.contains(name(brand)), brand + " is beside " + name(brand));
		}
		for (int symbol = 0; symbol < CutoutLexicon.SYMBOLS_COUNT; symbol++) {
			for (String brand : BRANDS) {
				assertTrue(!CutoutLexicon.symbolName(symbol).equals(brand), "no drawing of " + brand);
			}
		}
	}

	@Test
	void theFormsSongsUseAreFound() {
		assertEquals("smoke", name("smokin'"));
		assertEquals("smoke", name("Smokin"));
		assertEquals("car", name("Mercedes-Benz"));
		assertEquals("car", name("G-Wagon"));
		assertEquals("car", name("Sportwagen"));
		assertEquals("headphones", name("Kopfhoerer"));
		assertEquals("crown", name("king's"));
		assertEquals("wristwatch", name("watches"));
		assertEquals("money", name("racks"));
		assertEquals("pray", name("Gebet"));
		assertEquals("storm", name("Gewitter"));
		assertEquals("record", name("Platten"));
		assertEquals("siren", name("Polizei"));
		assertEquals("plane", name("Flieger"));
		assertEquals("microphone", name("Strophe"));
		assertEquals("diamond", name("Klunker"));
		assertEquals("trophy", name("Platin"));
		assertEquals("camera", name("berühmt"));
		// endings taken off only where a word is left: no car in "cares", "cart" or "caring", no bar in "barn"
		for (String not : new String[] {"cares", "cart", "caring", "barn", "carpet"}) {
			assertEquals("none", name(not), not);
		}
		assertEquals("plane", CutoutLexicon.symbolName(CutoutLexicon.phrase("private", "jet")));
		assertEquals("shoe", CutoutLexicon.symbolName(CutoutLexicon.phrase("Air", "Max")));
		assertEquals("trophy", CutoutLexicon.symbolName(CutoutLexicon.phrase("number", "one")));
	}

	@Test
	void noSwearWordsAndNoDrugsHaveASymbol() {
		for (String word : new String[] {"fuck", "fucking", "shit", "bitch", "scheiße", "fick", "weed", "kush", "blunt", "joint", "coke", "koks", "molly",
			"lean", "xanax", "pills", "gras", "gun", "guns", "knife", "messer", "waffe", "glock"}) {
			assertEquals("none", name(word), word);
		}
	}

	@Test
	void oneSymbolALineAtMostAndAPhraseOnItsMainWord() {
		String[] song = {"I pull up in the Benz with the private jet waiting", "Racks on racks and the Rolex on my wrist", "Smokin' in the city with my kicks",
			"Ich fahr mit dem Sportwagen durch die Stadt", "Wir beten zu Gott in der Nacht", "Blaulicht hinter uns und wir fliegen", "Number one on the stage tonight"};
		int symbols = 0;
		long at = 0L;
		for (String text : song) {
			LyricLine line = new LyricLine(at, text);
			List<Piece> pieces = CutoutWords.pieces(line, List.of(), at + 4_000L, true, true);
			int inLine = 0;
			for (Piece piece : pieces) {
				inLine += piece.symbol() != CutoutLexicon.NO_SYMBOL ? 1 : 0;
				assertTrue(!piece.small() || piece.symbol() == CutoutLexicon.NO_SYMBOL, "never beside a small word");
			}
			assertTrue(inLine <= 1, text + ": " + inLine + " symbols");
			symbols += inLine;
			at += 5_000L;
		}
		assertTrue(symbols >= song.length / 2, "a symbol about every one or two lines: " + symbols + " in " + song.length);
	}
}
