package dev.tim1337.lumio.features.visual.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

/**
 * Hypixel writes each scoreboard line as a team prefix, an entry and a suffix; the entry is a filler emoji with a color code that only makes the
 * line unique. A Glacite Tunnels scoreboard in that form: the lines read as the game's text without the filler, in either style.
 */
class ScoreboardFillerTest {
	/** Prefix, entry and suffix of each line, as Hypixel sends them (the filler emoji as SkyHanni lists them). */
	private static final String[][] GLACITE = {
		{"§707/11/26 §8m91AB", "§0🍫", ""},
		{"", "§1💣", ""},
		{" Late Autumn 7th", "§2👽", ""},
		{" §79:40am §e☀", "§3🔮", ""},
		{" §7⏣ §bGlacite", "§4🐍", " Tunnels"},
		{"", "§5👾", ""},
		{"Cold: §b❄ -12", "§6🌠", ""},
		{"§ewww.hypixel.net", "§7🍭", ""}};
	private static final String[] SHOWN = {"§707/11/26 §8m91AB", "", " Late Autumn 7th", " §79:40am §e☀", " §7⏣ §bGlacite Tunnels", "", "Cold: §b❄ -12",
		"§ewww.hypixel.net"};

	@Test
	void theFillerIsLeftOutAndTheRestStaysAsSent() {
		for (int i = 0; i < GLACITE.length; i++) {
			String[] parts = GLACITE[i];
			Component line = Component.empty().append(Component.literal(parts[0])).append(ScoreboardLines.entryShown(Component.literal(parts[1])))
				.append(Component.literal(parts[2]));
			assertEquals(SHOWN[i], line.getString());
		}
	}

	@Test
	void onlyAFillerIsAFiller() {
		assertTrue(ScoreboardLines.filler("§c🍫"));
		assertTrue(ScoreboardLines.filler("👾§r"));
		// a player's name, a plain color code, a symbol of the basic plane and nothing at all are shown as sent
		assertFalse(ScoreboardLines.filler("Steve"));
		assertFalse(ScoreboardLines.filler("§1"));
		assertFalse(ScoreboardLines.filler("§e☀"));
		assertFalse(ScoreboardLines.filler(""));
		assertEquals("Steve", ScoreboardLines.entryShown(Component.literal("Steve")).getString());
	}
}
