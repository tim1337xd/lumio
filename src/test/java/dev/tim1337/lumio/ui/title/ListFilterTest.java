package dev.tim1337.lumio.ui.title;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListFilterTest {
	private static final List<String> NAMES = List.of("Hypixel", "Lobby", "Old friends");
	private static final List<String> ADDRESSES = List.of("mc.hypixel.net", "localhost:25565", "play.example.org");

	@Test
	void emptyQueryKeepsEverything() {
		assertEquals(List.of(0, 1, 2), ListFilter.matching(NAMES, ADDRESSES, ""));
		assertEquals(List.of(0, 1, 2), ListFilter.matching(NAMES, ADDRESSES, "   "));
		assertEquals(List.of(0, 1, 2), ListFilter.matching(NAMES, null, null));
	}

	@Test
	void matchesNameOrAddressWithoutCase() {
		assertEquals(List.of(1), ListFilter.matching(NAMES, ADDRESSES, "LOB"));
		assertEquals(List.of(2), ListFilter.matching(NAMES, ADDRESSES, "example"));
		assertEquals(List.of(0), ListFilter.matching(NAMES, ADDRESSES, " hypixel.net "));
		assertEquals(List.of(), ListFilter.matching(NAMES, ADDRESSES, "nothing"));
		// worlds have no second text
		assertEquals(List.of(), ListFilter.matching(NAMES, null, "example"));
	}

	@Test
	void addressesAreRecognized() {
		assertTrue(ListFilter.looksLikeAddress("mc.hypixel.net"));
		assertTrue(ListFilter.looksLikeAddress("127.0.0.1:25565"));
		assertTrue(ListFilter.looksLikeAddress("localhost"));
		assertFalse(ListFilter.looksLikeAddress("Old friends"));
		assertFalse(ListFilter.looksLikeAddress("lobby"));
		assertFalse(ListFilter.looksLikeAddress(""));
	}

	@Test
	void sortedByLastPlayedLatestFirst() {
		assertEquals(List.of(1, 2, 0), ListFilter.byLastPlayed(List.of(100L, 300L, 200L)));
		// equal times keep their order
		assertEquals(List.of(0, 1), ListFilter.byLastPlayed(List.of(5L, 5L)));
	}

	@Test
	void agoInWords() {
		long now = 1_000_000_000_000L;
		long minute = 60_000L;
		assertEquals("just now", ListFilter.ago(now - 10_000L, now));
		assertEquals("a minute ago", ListFilter.ago(now - minute, now));
		assertEquals("5 minutes ago", ListFilter.ago(now - 5 * minute, now));
		assertEquals("2 hours ago", ListFilter.ago(now - 120 * minute, now));
		assertEquals("yesterday", ListFilter.ago(now - 30 * 60 * minute, now));
		assertEquals("3 weeks ago", ListFilter.ago(now - 21 * 24 * 60 * minute, now));
		assertEquals("just now", ListFilter.ago(now + minute, now));
	}
}
