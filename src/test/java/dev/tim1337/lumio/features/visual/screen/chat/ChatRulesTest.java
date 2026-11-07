package dev.tim1337.lumio.features.visual.screen.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the chat reads out of a message's text: tab filters, repeat collapsing, mentions and timestamps. */
class ChatRulesTest {
	@Test
	void aRepeatsCountIsPlainText() {
		assertEquals(" (x2)", ChatRules.countText(2));
		assertEquals(" (x13)", ChatRules.countText(13));
	}

	@Test
	void theCountWrapsWithTheLineLikeAWord() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		net.minecraft.network.chat.Component line = net.minecraft.network.chat.Component.empty().append("a long line that nearly fills its row").append(net.minecraft.network.chat.Component.literal(ChatRules.countText(2)));
		int letter = 6;
		net.minecraft.client.StringSplitter splitter = new net.minecraft.client.StringSplitter((codepoint, style) -> letter);
		int width = letter * "a long line that nearly fills its row".length();
		var rows = splitter.splitLines(line, width, net.minecraft.network.chat.Style.EMPTY);
		assertEquals(2, rows.size(), "the count goes to the next row");
		assertEquals("(x2)", rows.get(1).getString().strip());
		for (var row : rows) {
			assertTrue(splitter.stringWidth(row) <= width, "never past the row's end");
		}
	}

	private static final long TEN_MINUTES = 600_000L;

	@Test
	void aRepeatJoinsUnderTenMinutes() {
		ChatRules.Repeats<String> repeats = new ChatRules.Repeats<>(TEN_MINUTES, 100);
		repeats.note("a", 1, 0L, "first");
		long justUnder = TEN_MINUTES - 1_000L;
		assertEquals(2, repeats.count("a", justUnder), "9:59 joins");
		assertEquals("first", repeats.replaced("a", justUnder));
	}

	@Test
	void atTenMinutesOrMoreItIsALineOfItsOwn() {
		ChatRules.Repeats<String> repeats = new ChatRules.Repeats<>(TEN_MINUTES, 100);
		repeats.note("a", 3, 0L, "the old line with its number");
		assertEquals(1, repeats.count("a", TEN_MINUTES), "10:00 starts a new line, at no count");
		assertNull(repeats.replaced("a", TEN_MINUTES), "and the old line stays");
		assertEquals(1, repeats.count("a", TEN_MINUTES + 60_000L));
		assertNull(repeats.replaced("a", TEN_MINUTES + 60_000L));
	}

	@Test
	void theTenMinutesCountFromTheLastRepeat() {
		ChatRules.Repeats<String> repeats = new ChatRules.Repeats<>(TEN_MINUTES, 100);
		repeats.note("a", 1, 0L, "one");
		repeats.note("a", 2, TEN_MINUTES - 1_000L, "two");
		assertEquals(3, repeats.count("a", 2 * TEN_MINUTES - 2_000L));
	}

	@Test
	void otherMessagesInBetweenChangeNothing() {
		ChatRules.Repeats<String> repeats = new ChatRules.Repeats<>(TEN_MINUTES, 100);
		repeats.note("a", 1, 0L, "one");
		repeats.note("b", 1, 1_000L, "other");
		assertEquals(2, repeats.count("a", 2_000L));
		assertEquals("one", repeats.replaced("a", 2_000L));
		assertEquals(1, repeats.count("c", 2_000L));
	}

	@Test
	void theSenderComesFromEveryKindOfLine() {
		assertEquals("Steve123", ChatRules.sender("[MVP+] Steve123: anyone selling a juju bow?"));
		assertEquals("Bob", ChatRules.sender("Party > [VIP] Bob: ready for m3?"));
		assertEquals("Eve", ChatRules.sender("Guild > [MVP] Eve [Officer]: gg on the drop"));
		assertEquals("Cara", ChatRules.sender("From [MVP+] Cara: can you craft one for me?"));
		assertEquals("Bob", ChatRules.sender("[VIP] Bob: check this"));
	}

	@Test
	void aLineOfTheGameHasNoSender() {
		assertNull(ChatRules.sender("You received 1,200 coins from killing a Zealot."));
		assertNull(ChatRules.sender("RARE DROP! Summoning Eye (+400% Magic Find)"));
		assertNull(ChatRules.sender("----------------"));
	}

	@Test
	void aMentionIsAWordOfItsOwn() {
		assertTrue(ChatRules.mentions("hey bob, warp us", List.of("Bob")));
		assertTrue(ChatRules.mentions("BOB!", List.of("bob")));
		assertFalse(ChatRules.mentions("Bobby is here", List.of("Bob")));
		assertFalse(ChatRules.mentions("bob_the_builder", List.of("Bob")));
		assertFalse(ChatRules.mentions("anything", List.of("", "  ")));
		assertTrue(ChatRules.mentions("selling a juju bow", List.of("hyperion", "juju")));
	}

	@Test
	void timestampsReadInBothClocks() {
		assertEquals("14:30", ChatRules.time(14, 30, 5, false, false));
		assertEquals("04:05:09", ChatRules.time(4, 5, 9, false, true));
		assertEquals("2:30 PM", ChatRules.time(14, 30, 5, true, false));
		assertEquals("12:00 AM", ChatRules.time(0, 0, 0, true, false));
		assertEquals("12:15 PM", ChatRules.time(12, 15, 0, true, false));
		assertEquals("11:59:07 AM", ChatRules.time(11, 59, 7, true, true));
	}

	@Test
	void anOwnTabFiltersInThreeWays() {
		String line = "Party > [VIP] Bob: ready for m3?";
		assertTrue(ChatRules.matchesFilter(line, ChatRules.FilterMode.CONTAINS, "M3"));
		assertFalse(ChatRules.matchesFilter(line, ChatRules.FilterMode.CONTAINS, "m4"));
		assertTrue(ChatRules.matchesFilter(line, ChatRules.FilterMode.STARTS_WITH, "party"));
		assertFalse(ChatRules.matchesFilter(line, ChatRules.FilterMode.STARTS_WITH, "Bob"));
		assertTrue(ChatRules.matchesFilter(line, ChatRules.FilterMode.FROM_PLAYER, "bob"));
		assertFalse(ChatRules.matchesFilter(line, ChatRules.FilterMode.FROM_PLAYER, "Eve"));
		assertFalse(ChatRules.matchesFilter(line, ChatRules.FilterMode.CONTAINS, "  "));
	}

	@Test
	void repeatsCountWithinTheirSpan() {
		ChatRules.Repeats<String> repeats = new ChatRules.Repeats<>(1000L, 10);
		assertEquals(1, repeats.count("a", 0L));
		assertNull(repeats.replaced("a", 0L));
		repeats.note("a", 1, 0L, "shown once");
		assertEquals(2, repeats.count("a", 500L));
		assertEquals("shown once", repeats.replaced("a", 500L));
		repeats.note("a", 2, 500L, "shown twice");
		assertEquals(3, repeats.count("a", 1000L));
		assertEquals(1, repeats.count("a", 2000L));
		assertNull(repeats.replaced("a", 2000L));
		assertEquals(1, repeats.count("b", 500L));
	}

	@Test
	void theMemoryOfRepeatsHasALimit() {
		ChatRules.Repeats<String> repeats = new ChatRules.Repeats<>(1000L, 3);
		for (int i = 0; i < 100; i++) {
			repeats.note("line " + i, 1, i * 5000L, "x");
		}
		assertEquals(1, repeats.count("line 0", 500_000L));
	}

	@Test
	void linksAreFoundAndCanBeOpened() {
		assertArrayEquals(new int[]{6, 34}, ChatRules.link("check hypixel.net/threads/12345678 ok"));
		assertArrayEquals(new int[]{0, 19}, ChatRules.link("https://example.org"));
		assertNull(ChatRules.link("no link here. really"));
		assertEquals("https://hypixel.net/x", ChatRules.openable("hypixel.net/x"));
		assertEquals("http://a.org", ChatRules.openable("http://a.org"));
	}
}
