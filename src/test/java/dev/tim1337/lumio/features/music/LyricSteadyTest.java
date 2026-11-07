package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import org.junit.jupiter.api.Test;

import java.util.List;

/** A small step back of the song's clock never brings the line before back; a real jump back does. */
class LyricSteadyTest {
	private static final List<LyricLine> FAST = List.of(new LyricLine(1_000, "one"), new LyricLine(1_800, "two"), new LyricLine(2_600, "three"));

	@Test
	void aSmallStepBackKeepsTheLine() {
		// "two" began at 1.8 s and is shown; the clock steps back to 1.75 s, where "one" is sung
		assertEquals(1, LyricTimes.steadyIndex(FAST, 1_750, 0, 1));
	}

	@Test
	void aRealJumpBackGoesBack() {
		assertEquals(0, LyricTimes.steadyIndex(FAST, 1_100, 0, 1));
	}

	@Test
	void goingOnIsNeverHeldBack() {
		assertEquals(2, LyricTimes.steadyIndex(FAST, 2_700, 2, 1));
		assertEquals(0, LyricTimes.steadyIndex(FAST, 1_000, 0, -1));
	}
}
