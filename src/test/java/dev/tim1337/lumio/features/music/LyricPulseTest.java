package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pulse of the sung word in the Cut-out preset: in time with the word's own timing. */
class LyricPulseTest {
	@Test
	void theWordSwellsAtItsStartAndEasesBackInItsOwnTime() {
		assertEquals(0f, LyricPulse.at(-1, 400), "nothing before the word");
		assertEquals(0f, LyricPulse.at(0, 400));
		assertEquals(1f, LyricPulse.at(LyricPulse.ATTACK_MS, 400), 1e-4f, "the peak right after the start");
		float before = 1f;
		for (long at = LyricPulse.ATTACK_MS; at <= 400; at += 5) {
			float pulse = LyricPulse.at(at, 400);
			assertTrue(pulse <= before, "only eases back, at " + at);
			before = pulse;
		}
		assertEquals(0f, LyricPulse.at(400, 400), "over when the word is");
		assertTrue(LyricPulse.at(300, 2_000) > LyricPulse.at(300, 400), "a long word eases back slower");
		assertEquals(0f, LyricPulse.at(LyricPulse.MOST_MS, 5_000), "but never longer than a long note");
		assertTrue(LyricPulse.at(150, 50) > 0f, "and a very short word is still seen");
	}
}
