package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.Changelog;
import dev.tim1337.lumio.api.Changelog.Kind;
import dev.tim1337.lumio.api.Changelog.Release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What is new: the changelog format, the tabs and their order, the unseen marks, and a missing or broken add-on file. */
class WhatsNewTest {
	private static final String LUMIO = """
		## In development
		> Second beta.
		+ Waypoints: mark places @waypoints
		* Menu with a top bar
		! Loading screen stuck with quick play

		## 1.0.0-beta.2 | 2026-11-24
		+ Molten, a new sky style @sky
		! Login error after Rejoin

		## 1.0.0-beta.1 | 2026-11-07
		+ The first beta
		""";
	private static final String ADDON = """
		## 1.0.0-beta.2 | 2026-11-25
		+ Dungeons
		## 1.0.0-beta.1 | 2026-11-07
		+ Panels
		""";

	private static WhatsNew.Mod lumio() {
		return new WhatsNew.Mod("lumio", "Lumio", Changelog.parse(LUMIO));
	}

	private static WhatsNew.Mod addon() {
		return new WhatsNew.Mod("lumio-skyblock", "SkyBlock", Changelog.parse(ADDON));
	}

	@Test
	void theFormatIsRead() {
		List<Release> releases = Changelog.parse(LUMIO);
		assertEquals(3, releases.size());
		Release dev = releases.getFirst();
		assertTrue(dev.inDevelopment());
		assertEquals(List.of("Second beta."), dev.summary());
		assertEquals("Waypoints: mark places", dev.of(Kind.ADDED).getFirst().text());
		assertEquals("waypoints", dev.of(Kind.ADDED).getFirst().link());
		assertNull(dev.of(Kind.CHANGED).getFirst().link());
		assertEquals(1, dev.of(Kind.FIXED).size());
		assertEquals("2026-11-24", releases.get(1).date());
		assertEquals("sky", releases.get(1).of(Kind.ADDED).getFirst().link());
	}

	@Test
	void aBrokenFileIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> Changelog.parse("+ an entry before any section"));
		assertThrows(IllegalArgumentException.class, () -> Changelog.parse("## 1.0.0\n+ released without a date"));
		assertThrows(IllegalArgumentException.class, () -> Changelog.parse("## 1.0.0 | 2026-11-07\nnot an entry"));
		assertThrows(IllegalArgumentException.class, () -> Changelog.parse("## 1.0.0 | 2026-11-07\n+ " + "x".repeat(201)));
	}

	@Test
	void aMissingOrBrokenAddonFileLeavesItsTabOut() {
		// the screen reads each file and leaves out what it cannot read; a mod with no versions to show gets no tab either
		List<WhatsNew.Mod> mods = new ArrayList<>(List.of(lumio()));
		try {
			mods.add(new WhatsNew.Mod("broken", "Broken", Changelog.parse("+ nothing before a section")));
		} catch (IllegalArgumentException e) {
			// left out, as the screen does
		}
		mods.add(new WhatsNew.Mod("empty", "Empty", Changelog.parse("## In development\n+ only work in progress")));
		WhatsNew model = new WhatsNew(mods, Map.of(), true);
		assertEquals(List.of("Lumio"), model.tabs(), "no All for one mod, and no tab for the broken or empty file");
	}

	@Test
	void tabsAndOrder() {
		WhatsNew model = new WhatsNew(List.of(lumio(), addon()), Map.of(), true);
		assertEquals(List.of("Lumio", "SkyBlock"), model.tabs(), "Lumio first, then each add-on; no All");
		assertEquals(List.of("Lumio"), new WhatsNew(List.of(lumio()), Map.of(), true).tabs(), "Lumio alone: one list, no tab row");
		// a release build shows released versions only, newest first
		List<String> lumio = model.items("Lumio").stream().map(item -> item.release().version()).toList();
		assertEquals(List.of("1.0.0-beta.2", "1.0.0-beta.1"), lumio);
		// a development build shows "In development" first
		WhatsNew dev = new WhatsNew(List.of(lumio(), addon()), Map.of(), false);
		assertTrue(dev.items("Lumio").getFirst().release().inDevelopment());
	}

	@Test
	void theUnseenMarker() {
		// seen up to beta.1: beta.2 is new in both, the tabs have their dot, Lumio's tab opens first
		Map<String, String> seen = Map.of("lumio", "1.0.0-beta.1", "lumio-skyblock", "1.0.0-beta.1");
		WhatsNew model = new WhatsNew(List.of(lumio(), addon()), seen, true);
		assertTrue(model.items("Lumio").getFirst().isNew());
		assertFalse(model.items("Lumio").get(1).isNew());
		assertTrue(model.hasNew("Lumio") && model.hasNew("SkyBlock"), "both tabs have their dot");
		assertEquals("Lumio", model.changedTab());
		// only the add-on new: its tab keeps its dot, Lumio's has none
		WhatsNew addonOnly = new WhatsNew(List.of(lumio(), addon()), Map.of("lumio", "1.0.0-beta.2", "lumio-skyblock", "1.0.0-beta.1"), true);
		assertFalse(addonOnly.hasNew("Lumio"));
		assertTrue(addonOnly.hasNew("SkyBlock"));
		// the window opened: everything seen, nothing left to mark
		WhatsNew none = new WhatsNew(List.of(lumio(), addon()), model.allSeen(), true);
		assertNull(none.changedTab());
		// "In development" is never new
		WhatsNew dev = new WhatsNew(List.of(lumio()), Map.of("lumio", "1.0.0-beta.2"), false);
		assertFalse(dev.hasNew("Lumio"));
	}

	@Test
	void theSeenVersionsAreSavedAndRead() {
		Map<String, String> seen = WhatsNew.readSeen("lumio=1.0.0-beta.2;lumio-skyblock=1.0.0-beta.1;broken;=x");
		assertEquals(Map.of("lumio", "1.0.0-beta.2", "lumio-skyblock", "1.0.0-beta.1"), seen);
		assertEquals(seen, WhatsNew.readSeen(WhatsNew.writeSeen(seen)));
		assertEquals("1.0.0 Beta 2", WhatsNew.versionShown("1.0.0-beta.2"));
		assertEquals("1.2.0", WhatsNew.versionShown("1.2.0"));
		assertEquals("24 Nov 2026", WhatsNew.dateShown("2026-11-24"));
	}

	@Test
	void theShippedChangelogIsReadable() throws IOException {
		try (InputStream in = WhatsNewTest.class.getResourceAsStream("/" + Changelog.path("lumio"))) {
			List<Release> releases = Changelog.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			assertFalse(releases.isEmpty());
			// the newest section is also the release notes: about 8 entries per group at most (Beta 1, before this format, had more)
			Release newest = releases.getFirst();
			for (Kind kind : Kind.values()) {
				assertTrue(newest.of(kind).size() <= 9, newest.version() + " has more than 9 " + kind + " entries");
			}
		}
	}
}
