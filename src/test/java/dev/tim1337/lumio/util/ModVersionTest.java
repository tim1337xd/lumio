package dev.tim1337.lumio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How a version reads for players: no build number, no "alpha" on what they get, and the first beta only as "Beta". */
class ModVersionTest {
	@Test
	void theFirstBeta() {
		assertEquals("1.0.0 Beta", ModVersion.Shown.of("1.0.0-beta.1").text());
		assertEquals("", ModVersion.Shown.of("1.0.0-beta.1").build());
	}

	@Test
	void aLaterBeta() {
		assertEquals("1.0.0 Beta 2", ModVersion.Shown.of("1.0.0-beta.2").text());
	}

	@Test
	void aRelease() {
		assertEquals("1.0.0", ModVersion.Shown.of("1.0.0").text());
	}

	@Test
	void anAlphaBuildForTheDeveloper() {
		ModVersion.Shown shown = ModVersion.Shown.of("1.0.1-alpha+build.31");
		assertEquals("1.0.1 Alpha", shown.text());
		assertEquals("Build 31", shown.build());
	}

	@Test
	void anythingElseAsItIs() {
		assertEquals("development", ModVersion.Shown.of("development").text());
	}
}
