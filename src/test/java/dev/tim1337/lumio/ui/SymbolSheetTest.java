package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * The mod's symbol sheets (tools/FontSymbols.java) hold a cell per symbol of {@link MissingGlyphs#OWN_GLYPHS}, drawn as high as the capitals: a
 * symbol much lower than its cell came out as a speck next to the text, as the game's Unifont glyphs did.
 */
class SymbolSheetTest {
	/** The share of the cell (the capitals' height) a symbol's ink must reach at least. */
	private static final double LEAST_HEIGHT = 0.7;
	private static final int[] SHEETS = {12, 16, 24, 32};
	private static final int INK_ALPHA = 48;

	@Test
	void everySymbolIsAboutCapHeight() throws IOException {
		int symbols = MissingGlyphs.OWN_GLYPHS.length();
		for (int size : SHEETS) {
			BufferedImage sheet;
			try (InputStream in = SymbolSheetTest.class.getResourceAsStream("/assets/lumio/textures/font/symbols/" + size + ".png")) {
				assertNotNull(in, "sheet " + size);
				sheet = ImageIO.read(in);
			}
			assertEquals(symbols * size, sheet.getWidth(), "one cell per symbol in sheet " + size);
			for (int i = 0; i < symbols; i++) {
				int top = size;
				int bottom = -1;
				for (int y = 0; y < size; y++) {
					for (int x = i * size; x < (i + 1) * size; x++) {
						if ((sheet.getRGB(x, y) >>> 24) >= INK_ALPHA) {
							top = Math.min(top, y);
							bottom = Math.max(bottom, y);
						}
					}
				}
				int height = bottom - top + 1;
				char symbol = MissingGlyphs.OWN_GLYPHS.charAt(i);
				assertTrue(height >= LEAST_HEIGHT * size, String.format("U+%04X in sheet %d is %d of %d px high", (int) symbol, size, height, size));
			}
		}
	}
}
