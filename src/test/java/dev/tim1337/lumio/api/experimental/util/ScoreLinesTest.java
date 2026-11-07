package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The location symbol found before a line's first letter, whatever the server puts around it. */
class ScoreLinesTest {
	private static final String AREA = "⏣";

	@Test
	void plainForms() {
		assertEquals(0, ScoreLines.locationStart(AREA + " Void Sepulture"));
		assertEquals(1, ScoreLines.locationStart(" " + AREA + " Void Sepulture"));
		assertEquals(0, ScoreLines.locationStart("ф West Village"));
		assertEquals(0, ScoreLines.locationStart("📍 Cliffside Veins"));
		assertEquals(0, ScoreLines.locationStart(AREA + "Village"));
	}

	@Test
	void afterWhatShowsNothing() {
		for (String before : new String[]{"​", "‎", "️", " ", "", " ", "🎭 ", "§7", "§7§r ", " §f‍§7 "}) {
			String line = before + AREA + " Void Sepulture";
			assertEquals(line.indexOf(AREA), ScoreLines.locationStart(line), ScoreLines.codes(before));
		}
	}

	@Test
	void afterASymbolNoOneSeesAsALetter() {
		assertEquals(2, ScoreLines.locationStart("• " + AREA + " Hub"));
	}

	@Test
	void onlyBeforeTheFirstLetter() {
		for (String line : new String[]{"Purse: " + AREA + " 1,000", "Late " + AREA + " Summer", "7 " + AREA + " Hub", "Village", "", "   ", AREA, AREA + "   ​", "фото", "ф§aото"}) {
			assertEquals(-1, ScoreLines.locationStart(line), line);
		}
	}

	@Test
	void theNameStartsAfterFillersAndCodes() {
		String line = AREA + "️ §r​🎭 §5Void";
		assertEquals('V', line.charAt(ScoreLines.nameStart(line, 1)));
	}

	@Test
	void describesWhatALineIsBuiltOf() {
		Component line = Component.empty().append(Component.literal(" " + AREA)).append(Component.literal("name")).append(Component.literal(" Hub"));
		String text = ScoreLines.describe(line);
		assertTrue(text.contains("prefix"), text);
		assertTrue(text.contains("U+23E3"), text);
		assertTrue(text.contains("suffix"), text);
	}

	@Test
	void everyCharacterAsItsCode() {
		assertEquals("U+0020 U+23E3 U+1F4CD", ScoreLines.codes(" " + AREA + "📍"));
	}
}
