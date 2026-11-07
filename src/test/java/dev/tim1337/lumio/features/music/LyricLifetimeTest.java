package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** An adlib's own lifetime, whatever its line does; where it stands against what is visible; and the lyrics around a cut of the film. */
class LyricLifetimeTest {
	private static final long NOT_SHOWN = AdlibTimes.NOT_SHOWN;

	@Test
	void anAdlibLongerThanItsLineStaysUntilItsOwnEnd() {
		// the line ends at 3000; the adlib runs from 2500 to 5000: its line is no input at all
		assertTrue(AdlibTimes.shown(2500L, 5000L, 2500L, 3500L));
		assertTrue(AdlibTimes.shown(2500L, 5000L, 2500L, 5000L));
		assertFalse(AdlibTimes.shown(2500L, 5000L, 2500L, 5000L + AdlibTimes.HOLD_MS + 1L));
	}

	@Test
	void anAdlibShorterThanItsLineGoesAtItsOwnEnd() {
		// the line runs to 6000; the adlib is long enough not to be held by its least time
		assertTrue(AdlibTimes.shown(1000L, 2500L, 1000L, 2500L));
		assertFalse(AdlibTimes.shown(1000L, 2500L, 1000L, 2500L + AdlibTimes.HOLD_MS + 1L));
	}

	@Test
	void overlappingAdlibsEachHaveTheirOwn() {
		long now = 3000L;
		assertTrue(AdlibTimes.shown(1000L, 3200L, 1000L, now));
		assertTrue(AdlibTimes.shown(2800L, 6000L, 2800L, now));
		assertFalse(AdlibTimes.shown(1000L, 3200L, 1000L, 5000L));
		assertTrue(AdlibTimes.shown(2800L, 6000L, 2800L, 5000L));
	}

	@Test
	void withoutAnEndItIsTheNextAdlibsStartOrItsLength() {
		assertEquals(5000L, AdlibTimes.ownEnd(1000L, 5000L, 3000L, 4), "its own end counts first");
		assertEquals(1000L + 700L, AdlibTimes.ownEnd(1000L, 1000L, AdlibTimes.NO_NEXT, 4), "a short text has the least length");
		assertEquals(1000L + 20 * 70L, AdlibTimes.ownEnd(1000L, 0L, AdlibTimes.NO_NEXT, 20), "a long text is as long as it takes");
		assertEquals(1500L, AdlibTimes.ownEnd(1000L, 1000L, 1500L, 20), "the next adlib ends it");
		assertEquals(1000L + 20 * 70L, AdlibTimes.ownEnd(1000L, 1000L, 900L, 20), "an earlier one says nothing");
	}

	@Test
	void aQuickAdlibIsStillSeen() {
		assertTrue(AdlibTimes.shown(1000L, 1000L, 1000L, 1800L));
		assertFalse(AdlibTimes.shown(1000L, 1000L, 1000L, 1000L + AdlibTimes.LEAST_SHOWN_MS + AdlibTimes.HOLD_MS));
		assertFalse(AdlibTimes.shown(1000L, 1200L, NOT_SHOWN, 3000L));
	}

	// ---------------------------------------------------------------------------------------- placement

	@Test
	void boxesOverlapAsTheyAreSeen() {
		assertTrue(AdlibBoxes.overlap(0f, 0f, 5f, 2f, 8f, 1f, 5f, 2f, 0f));
		assertFalse(AdlibBoxes.overlap(0f, 0f, 5f, 2f, 12f, 1f, 5f, 2f, 0f), "apart across");
		assertFalse(AdlibBoxes.overlap(0f, 0f, 5f, 2f, 0f, 5f, 5f, 2f, 0f), "apart up");
		assertTrue(AdlibBoxes.overlap(0f, 0f, 5f, 2f, 0f, 5f, 5f, 2f, 2f), "closer than the gap");
		assertTrue(AdlibBoxes.overlap(179f, 0f, 5f, 2f, -179f, 0f, 5f, 2f, 0f), "across the back of the compass");
	}

	@Test
	void lookingSteeplyTheYawCountsForLess() {
		assertFalse(AdlibBoxes.overlap(0f, 0f, 5f, 2f, 12f, 0f, 5f, 2f, 0f), "at the horizon they are apart");
		assertTrue(AdlibBoxes.overlap(0f, 70f, 5f, 2f, 12f, 70f, 5f, 2f, 0f), "looking up the same yaw is much closer");
	}

	@Test
	void theRealSizeCountsAfterScaling() {
		float near = AdlibBoxes.halfAngle(1f, 5.0);
		float far = AdlibBoxes.halfAngle(1f, 20.0);
		assertTrue(near > far, "the same text is larger close by");
		assertEquals(45f, AdlibBoxes.halfAngle(1f, 1.0), 0.01f);
		assertTrue(AdlibBoxes.overlap(0f, 0f, near, near, near + far - 0.1f, 0f, far, far, 0f));
		assertFalse(AdlibBoxes.overlap(0f, 0f, near, near, near + far + 0.1f, 0f, far, far, 0f));
	}

	@Test
	void newTextTakesTheNextFreeSpot() {
		float[] visible = {0f, 0f, 6f, 2f};
		// on it, below it but still on it, further below: free
		float[] spots = {0f, 0f, 6f, 2f, 0f, -3f, 6f, 2f, 0f, -5f, 6f, 2f};
		assertEquals(2, AdlibBoxes.firstFree(spots, 3, visible, 1, 0f));
		assertEquals(0, AdlibBoxes.firstFree(spots, 3, visible, 0, 0f), "with nothing visible it stands where it wants to");
	}

	@Test
	void withNoFreeSpotThereIsNone() {
		float[] visible = {0f, 0f, 30f, 30f};
		float[] spots = {0f, 0f, 6f, 2f, 0f, -3f, 6f, 2f};
		assertEquals(-1, AdlibBoxes.firstFree(spots, 2, visible, 1, 0f));
	}

	@Test
	void everyVisibleOneCounts() {
		float[] visible = {0f, 0f, 6f, 2f, 0f, -6f, 6f, 2f};
		float[] spots = {0f, -3f, 6f, 2f, 0f, -11f, 6f, 2f};
		assertEquals(1, AdlibBoxes.firstFree(spots, 2, visible, 2, 0.5f));
	}

	// ---------------------------------------------------------------------------------------- around a cut

	@Test
	void withoutACutTheLyricsAreThere() {
		assertEquals(1f, LyricCutFade.alpha(LyricCutFade.NONE, LyricCutFade.NONE));
		assertEquals(1f, LyricCutFade.alpha(LyricCutFade.NONE, 5000L));
		assertEquals(1f, LyricCutFade.alpha(LyricCutFade.OUT_MS + 50L, 5000L), "a cut still far off");
	}

	@Test
	void theyFadeOutBeforeAForeseenCutAndAreGoneAtIt() {
		assertEquals(0.5f, LyricCutFade.alpha(75L, 5000L), 0.001f);
		assertEquals(0f, LyricCutFade.alpha(0L, 5000L), "nothing at the moment of the cut");
	}

	@Test
	void aHardCutIsGoneAtOnceAndFadesIn() {
		assertEquals(0f, LyricCutFade.alpha(LyricCutFade.NONE, 0L), "the first frame of the new shot starts from nothing");
		assertEquals(0.5f, LyricCutFade.alpha(LyricCutFade.NONE, 100L), 0.001f);
		assertEquals(1f, LyricCutFade.alpha(LyricCutFade.NONE, LyricCutFade.IN_MS));
	}

	@Test
	void aLineThatChangesDuringTheFadeComesInWithIt() {
		assertTrue(LyricCutFade.inCutFade(0L));
		assertTrue(LyricCutFade.inCutFade(LyricCutFade.IN_MS - 1L));
		assertFalse(LyricCutFade.inCutFade(LyricCutFade.IN_MS), "after it, a line has its own fade");
		assertFalse(LyricCutFade.inCutFade(LyricCutFade.NONE));
		// the fade goes on as it was: nothing starts over for the new line
		assertEquals(LyricCutFade.alpha(LyricCutFade.NONE, 120L), LyricCutFade.alpha(LyricCutFade.NONE, 120L));
	}

	@Test
	void aSecondCutSoonAfterTheFirstFadesOutFromWhereItIs() {
		assertEquals(0.5f, LyricCutFade.alpha(75L, 150L), 0.001f, "the fade out wins where it is the lower");
		assertEquals(0.25f, LyricCutFade.alpha(120L, 50L), 0.001f, "the fade in wins where it is");
	}
}
