package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The game's vignette leaves out lyrics standing in the world, but not in the cinematic camera, where it is off altogether. */
class LyricVignetteTest {
	@Test
	void onlyVisibleLyricsOutsideTheFilmCount() {
		LyricVignette.update(true, false);
		assertTrue(LyricVignette.lyricsShown());
		LyricVignette.update(false, false);
		assertFalse(LyricVignette.lyricsShown());
		LyricVignette.update(true, true);
		assertFalse(LyricVignette.lyricsShown(), "the film has no vignette to leave them out of");
	}

	@Test
	void lyricsSwitchedOffCountNoMore() {
		LyricVignette.update(true, false);
		LyricVignette.off();
		assertFalse(LyricVignette.lyricsShown());
	}
}
