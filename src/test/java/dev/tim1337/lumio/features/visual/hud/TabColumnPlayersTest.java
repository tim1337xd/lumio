package dev.tim1337.lumio.features.visual.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.util.TabSections;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Names under a section such as Guests or Coop stay in their section; only those of the players' column become players. */
class TabColumnPlayersTest {
	private static List<Component> lines(String... texts) {
		List<Component> lines = new ArrayList<>();
		for (String text : texts) {
			lines.add(Component.literal(text));
		}
		return lines;
	}

	private static List<String> texts(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	@Test
	void knowsTheSections() {
		assertTrue(TabSections.isNameSection("Guests (1)"));
		assertTrue(TabSections.isNameSection("Coop (3)"));
		assertTrue(TabSections.isNameSection("Co-op (3):"));
		assertTrue(TabSections.isNameSection("Island Members (4)"));
		assertTrue(TabSections.isNameSection("Party (2/5)"));
		assertTrue(TabSections.isNameSection("Party"));
		assertFalse(TabSections.isNameSection("Players (21)"), "that column is the players");
		assertFalse(TabSections.isNameSection("Area:"));
		assertFalse(TabSections.isNameSection("[502] Steve123"));
		assertFalse(TabSections.isNameSection(""));
	}

	@Test
	void guestsStayInTheirSection() {
		List<TabListHudFeature.Entry> players = new ArrayList<>();
		List<Component> left = TabListHudFeature.takeColumnPlayers(players, lines("Guests (1)", "[302] Steve123", ""), line -> null, "Self");
		assertEquals(List.of("Guests (1)", "[302] Steve123", ""), texts(left));
		assertTrue(players.isEmpty());
	}

	@Test
	void coopMembersStayWithTheirStatus() {
		List<TabListHudFeature.Entry> players = new ArrayList<>();
		List<String> sent = List.of("Coop (3)", "[302] Steve123 Online", "[18] Bob Last online 2d ago", "Eve Offline", "");
		List<Component> left = TabListHudFeature.takeColumnPlayers(players, lines(sent.toArray(String[]::new)), line -> null, "Self");
		assertEquals(sent, texts(left), "exactly as sent");
		assertTrue(players.isEmpty());
	}

	@Test
	void anEmptySectionEndsAtTheBlankLine() {
		List<TabListHudFeature.Entry> players = new ArrayList<>();
		List<Component> left = TabListHudFeature.takeColumnPlayers(players, lines("Guests (0)", "", "[50] Cara"), line -> null, "Self");
		assertEquals(List.of("Guests (0)", ""), texts(left));
		assertEquals(1, players.size(), "after the section a level and a name is a player again");
	}

	@Test
	void thePlayersColumnStillGivesPlayers() {
		List<TabListHudFeature.Entry> players = new ArrayList<>();
		List<Component> left = TabListHudFeature.takeColumnPlayers(players, lines("Coop (1)", "[9] Bob Online", "Players (2)", "[9] Bob", "Steve123"), line -> null, "Self");
		assertEquals(2, players.size());
		assertEquals(List.of("Coop (1)", "[9] Bob Online", "Players (2)"), texts(left));
	}
}
