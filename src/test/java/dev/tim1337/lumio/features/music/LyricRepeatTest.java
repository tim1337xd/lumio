package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LyricRepeatTest {
	@Test
	void theSameWordsSoonAfterAreARepeat() {
		assertTrue(LyricChorus.repeats("Hold me close", 2600L, "hold me, close!", 3000L), "case and punctuation do not count");
		assertTrue(LyricChorus.repeats("Hold me close", 2600L, "Hold me close", 4600L), "a pause of two seconds keeps it");
	}

	@Test
	void otherWordsOrALongPauseAreNot() {
		assertFalse(LyricChorus.repeats("Hold me close", 2600L, "Never let me go", 3000L));
		assertFalse(LyricChorus.repeats("Hold me close", 2600L, "Hold me close", 4700L), "a longer pause ends it");
		assertFalse(LyricChorus.repeats("...", 2600L, "!!!", 3000L), "no words are no line");
	}
}
