package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Logs;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * The symbols the menu font (Inter) has no glyph for, such as the server's ✯ ✦ ❈ ☘ ⸕ ✎. Inside the menu fonts the game's own font fills in for
 * them, but at its small pixel size within a font drawn scaled down, so they came out as specks; {@link Ui} draws them in the game's font at the
 * text's size instead. Which ones are missing is read once from the font file itself, on a background thread at start; until then none is.
 */
public final class MissingGlyphs {
	/** The menu font; its semibold and bold faces cover the same characters. */
	private static final String FONT = "/assets/lumio/font/inter.ttf";
	/** The symbol ranges checked: punctuation, letterlike symbols, arrows, math, shapes, dingbats and the supplemental punctuation (⸕ ⸎). */
	static final int FIRST = 0x2000;
	static final int LAST = 0x2E7F;
	/**
	 * Symbols the mod draws itself in each of its fonts, at the capitals' height (tools/FontSymbols.java): Hypixel's location pin and its moon,
	 * weather, star and swords, and those the game takes from Unifont, small within their box (᠅ ⸕ ❈ ❁ ☘ ✧ ⚚ ♲ ൠ ⛃ ⫽ ✯ ⏢ 〠). They stay in the
	 * menu font; text in the game's font draws them from font/symbols.json.
	 */
	static final String OWN_GLYPHS = "⏣☽☁☂❄✦⚔᠅⸕❈❁☘✧⚚♲ൠ⛃⫽✯⏢〠";

	private static volatile BitSet missing = new BitSet();

	private MissingGlyphs() {
	}

	/** Reads the font's characters on a background thread. */
	public static void load() {
		Thread thread = new Thread(MissingGlyphs::read, "Lumio glyphs");
		thread.setDaemon(true);
		thread.start();
	}

	/** @return whether the menu font has no glyph for the character; only symbols are ever missing */
	public static boolean missing(char c) {
		return c >= FIRST && c <= LAST && missing.get(c - FIRST) && OWN_GLYPHS.indexOf(c) < 0;
	}

	private static void read() {
		byte[] bytes;
		try (InputStream in = MissingGlyphs.class.getResourceAsStream(FONT)) {
			if (in == null) {
				return;
			}
			bytes = in.readAllBytes();
		} catch (IOException e) {
			Logs.warn("Could not read the menu font's characters", e);
			return;
		}
		ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
		try (STBTTFontinfo info = STBTTFontinfo.malloc()) {
			data.put(bytes).flip();
			if (!STBTruetype.stbtt_InitFont(info, data)) {
				return;
			}
			BitSet found = new BitSet(LAST - FIRST + 1);
			for (int c = FIRST; c <= LAST; c++) {
				if (STBTruetype.stbtt_FindGlyphIndex(info, c) == 0) {
					found.set(c - FIRST);
				}
			}
			missing = found;
		} finally {
			MemoryUtil.memFree(data);
		}
	}
}
