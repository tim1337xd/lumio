package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** At a song change, a seek, a long pause or the lyrics switched off, the old lyrics go together and quickly, and nothing of them is left. */
class SongBreakTest {
	private static final float FRAME = 1f / 60f;

	@Test
	void afterABreakNothingOfTheOldSongIsDrawnWithinFourTenthsOfASecond() {
		SongBreak songBreak = new SongBreak();
		songBreak.start();
		float seconds = 0f;
		boolean cleared = false;
		while (seconds < 0.4f) {
			cleared |= songBreak.step(FRAME);
			seconds += FRAME;
		}
		assertTrue(cleared, "the old lyrics are cleared");
		assertFalse(songBreak.running(), "the break is over");
		assertEquals(0f, SongBreak.light(0.4f), 0f, "nothing of the old lyrics shows");
	}

	@Test
	void theOldLyricsFadeAndShrinkWithoutAJump() {
		SongBreak songBreak = new SongBreak();
		songBreak.start();
		float light = songBreak.light();
		float size = songBreak.size();
		assertEquals(1f, light, 0f);
		while (!songBreak.step(FRAME)) {
			assertTrue(songBreak.light() <= light && light - songBreak.light() < 0.15f, "the light falls smoothly");
			assertTrue(songBreak.size() <= size && songBreak.size() >= 1f - SongBreak.SHRINK, "it shrinks a little");
			light = songBreak.light();
			size = songBreak.size();
		}
		assertEquals(1f, songBreak.light(), 0f, "the next lyrics are drawn as usual");
	}

	@Test
	void aSecondStartDoesNotRestartARunningBreak() {
		SongBreak songBreak = new SongBreak();
		songBreak.start();
		songBreak.step(0.2f);
		songBreak.start();
		assertTrue(songBreak.step(0.15f), "it ends on time");
	}
}
