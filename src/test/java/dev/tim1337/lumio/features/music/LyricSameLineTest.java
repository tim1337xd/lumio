package dev.tim1337.lumio.features.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A line that is showing keeps its place when it is asked for again; the same words sung later are a line of their own. */
class LyricSameLineTest {
	@Test
	void theSameLineWithItsTimeAdjusted() {
		assertTrue(LyricTimes.sameLine("Hello", 10_000L, "Hello", 10_000L));
		assertTrue(LyricTimes.sameLine("Hello", 10_000L, "Hello", 11_200L));
	}

	@Test
	void anotherLine() {
		assertFalse(LyricTimes.sameLine("Hello", 10_000L, "Hello", 40_000L));
		assertFalse(LyricTimes.sameLine("Hello", 10_000L, "World", 10_000L));
		assertFalse(LyricTimes.sameLine(null, 10_000L, "World", 10_000L));
	}
}
