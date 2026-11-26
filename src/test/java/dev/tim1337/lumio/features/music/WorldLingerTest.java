package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** "Older lines stay": counted from the moment the next line starts, how long an older line stays and how long it then fades. */
class WorldLingerTest {
	private static final float EXACT = 1e-6f;

	@Test
	void theThreeTimings() {
		assertEquals(1f, MusicFeature.WorldLinger.SHORT.seconds, EXACT);
		assertEquals(0.4f, MusicFeature.WorldLinger.SHORT.fadeSeconds, EXACT);
		assertEquals(3f, MusicFeature.WorldLinger.NORMAL.seconds, EXACT);
		assertEquals(0.6f, MusicFeature.WorldLinger.NORMAL.fadeSeconds, EXACT);
		assertEquals(6f, MusicFeature.WorldLinger.LONG.seconds, EXACT);
		assertEquals(0.8f, MusicFeature.WorldLinger.LONG.fadeSeconds, EXACT);
	}

	@Test
	void shortIsGoneAboutOneAndAHalfSecondsAfterTheNextLine() {
		MusicFeature.WorldLinger shortLinger = MusicFeature.WorldLinger.SHORT;
		assertEquals(1.4f, shortLinger.seconds + shortLinger.fadeSeconds, EXACT);
	}
}
