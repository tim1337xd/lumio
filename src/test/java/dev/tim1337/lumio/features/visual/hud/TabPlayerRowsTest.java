package dev.tim1337.lumio.features.visual.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Players written into a server's text columns are told from its information. */
class TabPlayerRowsTest {
	@Test
	void aLevelAndANameIsAPlayerAnywhere() {
		assertEquals("Steve123", TabPlayerRows.nameIn("[502] Steve123", false));
		assertEquals("Sample_01", TabPlayerRows.nameIn("[312] Sample_01", false));
		assertEquals("Someone", TabPlayerRows.nameIn("[88] Someone ✦", false));
	}

	@Test
	void aBareNameIsAPlayerOnlyInThePlayersColumn() {
		assertEquals("Someone", TabPlayerRows.nameIn("Someone", true));
		assertNull(TabPlayerRows.nameIn("Someone", false));
	}

	@Test
	void informationIsNoPlayer() {
		assertNull(TabPlayerRows.nameIn("Area: The End", false));
		assertNull(TabPlayerRows.nameIn("[502] 10/100 XP towards it", false) == null ? null : "not a name");
		assertNull(TabPlayerRows.nameIn("Active Effects (40)", true));
		assertNull(TabPlayerRows.nameIn("26 Hours (75%)", true));
	}

	@Test
	void headingsAreKnown() {
		assertTrue(TabPlayerRows.isPlayersHeading("Players (21)"));
		assertFalse(TabPlayerRows.isPlayersHeading("Players"));
		assertTrue(TabPlayerRows.isOtherHeading("Info"));
		assertFalse(TabPlayerRows.isOtherHeading("Area:"));
	}
}
