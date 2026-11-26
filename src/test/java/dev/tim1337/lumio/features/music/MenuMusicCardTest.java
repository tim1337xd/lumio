package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The main menu's music card: there with a song, and for a moment after the last one, so a gap between two songs does not make it blink. */
class MenuMusicCardTest {
	@Test
	void itIsThereWithASongAndTwoSecondsAfter() {
		assertTrue(MenuMusicCard.wanted(true, 0f));
		assertTrue(MenuMusicCard.wanted(true, 100f));
		assertTrue(MenuMusicCard.wanted(false, 0.5f), "a gap between two songs");
		assertTrue(MenuMusicCard.wanted(false, MenuMusicCard.HIDE_DELAY_SECONDS - 0.01f));
		assertFalse(MenuMusicCard.wanted(false, MenuMusicCard.HIDE_DELAY_SECONDS), "the music stopped: it goes");
		assertFalse(MenuMusicCard.wanted(false, 60f));
	}
}
