package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server text on a dark panel: no black or unreadable parts, no leftover codes, no color leaking from one part into the next. */
class ServerTextTest {
	private static final int SECONDARY = 0xA1A1AA;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** @return every visible part of a text with its color (-1 for none) */
	private static List<int[]> colors(Component text) {
		List<int[]> out = new ArrayList<>();
		text.visit((Style style, String part) -> {
			if (!part.isEmpty()) {
				out.add(new int[]{style.getColor() == null ? -1 : style.getColor().getValue(), part.length()});
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** A player's row in the list: the level in its color, then the name, as the server writes it. */
	private static List<int[]> row(String code, String level, String name) {
		return colors(ServerText.clean(Component.literal("§" + code + "[" + level + "] §b" + name), SECONDARY));
	}

	@Test
	void everyLevelColorStaysAsItIsSent() {
		String[][] tiers = {{"7", "1", "AAAAAA"}, {"f", "40", "FFFFFF"}, {"e", "80", "FFFF55"}, {"a", "120", "55FF55"}, {"2", "160", "00AA00"}, {"b", "200", "55FFFF"}, {"3", "272", "00AAAA"},
			{"9", "280", "5555FF"}, {"d", "324", "FF55FF"}, {"5", "360", "AA00AA"}, {"6", "400", "FFAA00"}, {"c", "461", "FF5555"}, {"4", "480", "AA0000"}, {"4", "486", "AA0000"}, {"4", "614", "AA0000"}};
		for (String[] tier : tiers) {
			List<int[]> parts = row(tier[0], tier[1], "Steve123");
			assertEquals(Integer.parseInt(tier[2], 16), parts.getFirst()[0], "the level [" + tier[1] + "]");
			assertEquals(0x55FFFF, parts.getLast()[0], "the name stays too");
		}
	}

	@Test
	void darkRedIsNeverGray() {
		assertEquals(0xAA0000, colors(ServerText.clean(Component.literal("[614] ").withStyle(net.minecraft.ChatFormatting.DARK_RED), SECONDARY)).getFirst()[0], "as a style");
		assertEquals(0xAA0000, colors(ServerText.clean(Component.literal("§4[480] §4Steve123"), SECONDARY)).getFirst()[0], "as a code");
	}

	@Test
	void aVeryDarkColorIsLightenedWithItsHue() {
		int blue = ServerText.readableColor(0x0000AA, SECONDARY);
		assertEquals(0, blue >> 16 & 0xFF);
		assertEquals(0, blue >> 8 & 0xFF);
		assertTrue((blue & 0xFF) >= 0xAA, "lighter, still only blue");
		assertTrue(ServerText.luminance(blue) >= 0.06f - 0.01f);
		int red = ServerText.readableColor(0x400000, SECONDARY);
		assertEquals(0, red >> 8 & 0xFFFF & 0xFF);
		assertEquals(0, red & 0xFF);
		assertTrue((red >> 16 & 0xFF) > 0x40);
	}

	@Test
	void onlyWhatHasNoHueTakesTheSecondaryColor() {
		assertEquals(SECONDARY, ServerText.readableColor(0x000000, SECONDARY));
		assertEquals(SECONDARY, ServerText.readableColor(0x555555, SECONDARY));
		assertEquals(0x888888, ServerText.readableColor(0x888888, SECONDARY));
	}

	@Test
	void legacyCodesBecomeStylesAndLeaveNoCharacters() {
		Component clean = ServerText.clean(Component.literal("§d§lRNG Meter §0(§8Voidgloom§0)"), SECONDARY);
		assertEquals("RNG Meter (Voidgloom)", clean.getString());
		for (int[] part : colors(clean)) {
			assertTrue(part[0] == -1 || ServerText.luminance(part[0]) >= 0.1f || part[0] == SECONDARY, "a dark part is left: " + Integer.toHexString(part[0]));
		}
	}

	@Test
	void blackStyleTakesTheSecondaryColor() {
		Component clean = ServerText.clean(Component.literal("Progress").withStyle(style -> style.withColor(0x000000)), SECONDARY);
		assertEquals(SECONDARY, colors(clean).getFirst()[0]);
	}

	@Test
	void aCodeDoesNotLeakIntoTheNextPart() {
		Component line = Component.literal("§8dark ").append(Component.literal("plain"));
		List<int[]> parts = colors(ServerText.clean(line, SECONDARY));
		assertEquals(-1, parts.getLast()[0]);
	}

	@Test
	void spacerLinesAreBlank() {
		assertTrue(ServerText.blank(Component.literal("§r §7 ")));
		assertTrue(ServerText.blank(Component.empty()));
		assertFalse(ServerText.blank(Component.literal("§aArea")));
	}
}
