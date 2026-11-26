package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutAdlibs.Shown;
import dev.tim1337.lumio.features.music.MusicService.Background;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The ad-libs of a Cut-out line: each for its own time, at most two at once, in a slot that never lies over a word. */
class CutoutAdlibsTest {
	private static Background adlib(String text, long start, long end) {
		return new Background(text, List.of(), start, end);
	}

	@Test
	void anAdLibShowsFromItsStartAndIsGoneAfterItsEndAndTheFade() {
		Shown shown = CutoutAdlibs.track(List.of(adlib("oh yeah", 1200, 1900)), 0, 4000).getFirst();
		assertEquals(1200, shown.startMs());
		assertEquals(1900, shown.endMs());
		assertEquals(0f, CutoutAdlibs.strength(shown, 1199));
		assertEquals(0f, CutoutAdlibs.strength(shown, 1200), 1e-4f, "it fades in from nothing");
		assertEquals(1f, CutoutAdlibs.strength(shown, 1200 + CutoutAdlibs.IN_MS), 1e-4f);
		assertEquals(1f, CutoutAdlibs.strength(shown, 1900), 1e-4f, "whole until its own end");
		assertTrue(CutoutAdlibs.strength(shown, 1900 + CutoutAdlibs.OUT_MS / 2) < 1f);
		assertEquals(0f, CutoutAdlibs.strength(shown, 1900 + CutoutAdlibs.OUT_MS), "gone after its end and the fade");
		assertEquals(0f, CutoutAdlibs.strength(shown, 60_000), "it does not stay");
		float before = 0f;
		for (long at = 1200; at <= 1200 + CutoutAdlibs.IN_MS; at += 10) {
			assertTrue(CutoutAdlibs.strength(shown, at) >= before, "in without a step back");
			before = CutoutAdlibs.strength(shown, at);
		}
	}

	@Test
	void atMostTwoShowAtOnceAndTheOldestGoesFirst() {
		List<Background> close = List.of(adlib("one", 1000, 3000), adlib("two", 1200, 3000), adlib("three", 1400, 3000), adlib("four", 1500, 3000));
		List<Shown> track = CutoutAdlibs.track(close, 0, 4000);
		for (long at = 0; at < 5000; at += 5) {
			int showing = 0;
			for (Shown shown : track) {
				showing += CutoutAdlibs.strength(shown, at) > 0f ? 1 : 0;
			}
			assertTrue(showing <= CutoutAdlibs.AT_ONCE, showing + " at " + at);
		}
		assertTrue(track.get(0).goneMs() <= 1400, "the oldest is gone when the third comes");
		assertTrue(track.get(1).goneMs() <= 1500);
		assertEquals(3000, track.get(3).endMs(), "the newest keep their time");
		assertEquals(3, track.get(3).source());
	}

	@Test
	void anAdLibWithoutATimeOfItsOwnShowsInTheSecondHalfOfItsLine() {
		Shown shown = CutoutAdlibs.track(List.of(adlib("hey", 0, 0)), 10_000, 14_000).getFirst();
		assertEquals(12_000, shown.startMs());
		assertEquals(14_000, shown.endMs());
		Shown open = CutoutAdlibs.track(List.of(adlib("hey", 500, 500)), 10_000, Long.MAX_VALUE).getFirst();
		assertTrue(open.startMs() > 10_000 && open.endMs() > open.startMs() && open.endMs() < 20_000);
		assertTrue(CutoutAdlibs.track(List.of(adlib("  ", 0, 100)), 0, 1000).isEmpty(), "an empty one is none");
	}

	private static final float LINE_BOTTOM = 86f;

	/** Words of a column: two rows about its middle. */
	private static List<float[]> words() {
		List<float[]> taken = new ArrayList<>();
		taken.add(new float[] {-60f, 0f, 60f, 40f});
		taken.add(new float[] {-80f, 46f, 80f, 86f});
		return taken;
	}

	private static void clear(List<float[]> taken, float[] slot, float halfWidth, float halfHeight) {
		assertNotNull(slot);
		float halfW = halfWidth * slot[2];
		float halfH = halfHeight * slot[2];
		for (float[] box : taken) {
			boolean over = slot[0] - halfW < box[2] && slot[0] + halfW > box[0] && slot[1] - halfH < box[3] && slot[1] + halfH > box[1];
			assertFalse(over, "the ad-lib lies over a word");
		}
	}

	@Test
	void anAdLibStandsBesideTheSungWordAndNeverOverAWord() {
		List<float[]> taken = words();
		float[] right = CutoutAdlibs.slot(taken, 20f, 40f, LINE_BOTTOM, 30f, 7f, 160f, 2);
		clear(taken, right, 30f, 7f);
		assertTrue(right[0] > 60f, "to the right of the word being sung");
		assertEquals(20f, right[1], "at its height");
		assertEquals(1f, right[2]);
		// no room to the right: to the left
		taken.add(new float[] {64f, 0f, 160f, 40f});
		float[] left = CutoutAdlibs.slot(taken, 20f, 40f, LINE_BOTTOM, 30f, 7f, 160f, 2);
		clear(taken, left, 30f, 7f);
		assertTrue(left[0] < -60f);
		// a second ad-lib beside the first: clear of it too
		taken.add(new float[] {left[0] - 30f, left[1] - 7f, left[0] + 30f, left[1] + 7f});
		float[] second = CutoutAdlibs.slot(taken, 66f, 86f, LINE_BOTTOM, 30f, 7f, 160f, 2);
		clear(taken, second, 30f, 7f);
	}

	@Test
	void anAdLibThatDoesNotFitIsSetSmallerAndLeftOutWhenItStillDoesNot() {
		List<float[]> taken = words();
		// wide: only fits smaller
		float[] smaller = CutoutAdlibs.slot(taken, 20f, 40f, LINE_BOTTOM, 60f, 7f, 160f, 2);
		clear(taken, smaller, 60f, 7f);
		assertTrue(smaller[2] < 1f && smaller[2] >= CutoutAdlibs.FITS[CutoutAdlibs.FITS.length - 1]);
		// far too wide for the room beside the words: a row of its own under the row being sung, or under the line
		float[] own = CutoutAdlibs.slot(taken, 20f, 40f, LINE_BOTTOM, 140f, 7f, 160f, 2);
		clear(taken, own, 140f, 7f);
		assertTrue(own[1] > 40f);
		// wider than the block even at the smallest size: left out
		assertNull(CutoutAdlibs.slot(taken, 20f, 40f, LINE_BOTTOM, 300f, 7f, 160f, 2));
		for (float width = 10f; width < 150f; width += 7f) {
			for (float y = 0f; y < 90f; y += 9f) {
				float[] slot = CutoutAdlibs.slot(taken, y, y + 20f, LINE_BOTTOM, width, 7f, 160f, 2);
				if (slot != null) {
					clear(taken, slot, width, 7f);
					assertTrue(Math.abs(slot[0]) + width * slot[2] <= 160f + 1e-3f, "inside the room it has");
				}
			}
		}
	}


	@Test
	void shortAndLongAdLibsInANarrowBlockNeverLieOverAWord() {
		// a narrow block: three rows that fill most of its width, the block only a quarter wider than its words
		float reach = 100f;
		List<float[]> lineWords = new ArrayList<>();
		lineWords.add(new float[] {-78f, 0f, -30f, 40f});
		lineWords.add(new float[] {-24f, 0f, 20f, 40f});
		lineWords.add(new float[] {26f, 0f, 78f, 40f});
		lineWords.add(new float[] {-70f, 46f, 70f, 86f});
		lineWords.add(new float[] {-40f, 92f, 55f, 132f});
		float lineBottom = 132f;
		float[][] rows = {{20f, 40f}, {66f, 86f}, {112f, 132f}};
		int placed = 0;
		for (int words = 1; words <= 9; words++) {
			// about 22 units to a word at full size, as "(how I see the same ones)" is
			float halfWidth = words * 11f;
			for (float[] row : rows) {
				List<float[]> taken = new ArrayList<>(lineWords);
				float[] slot = CutoutAdlibs.slot(taken, row[0], row[1], lineBottom, halfWidth, 13f, reach, words);
				if (slot == null) {
					continue;
				}
				placed++;
				clear(taken, slot, halfWidth, 13f);
				assertTrue(Math.abs(slot[0]) + halfWidth * slot[2] <= reach + 1e-3f, words + " words: inside the block");
				assertTrue(slot[2] >= CutoutAdlibs.FITS[CutoutAdlibs.FITS.length - 1], "never under the smallest size that reads");
				if (words > CutoutAdlibs.MOST_WORDS_BESIDE) {
					assertTrue(slot[1] > row[1], words + " words: a long one has a row of its own under the row being sung");
				}
				// a second one that shows at the same time keeps clear of the first too
				taken.add(new float[] {slot[0] - halfWidth * slot[2], slot[1] - 13f * slot[2], slot[0] + halfWidth * slot[2], slot[1] + 13f * slot[2]});
				float[] second = CutoutAdlibs.slot(taken, row[0], row[1], lineBottom, 22f, 13f, reach, 2);
				if (second != null) {
					clear(taken, second, 22f, 13f);
				}
			}
		}
		assertTrue(placed >= 20, "most of them find a place: " + placed);
	}

	@Test
	void anAdLibOverAWordThatOnlyAppearsLaterIsNoPlace() {
		// "Yeah yeah yeah" with an ad-lib after the second word: the third word stands in the row though it is not sung yet
		List<float[]> taken = new ArrayList<>();
		taken.add(new float[] {-90f, 0f, -40f, 40f});
		taken.add(new float[] {-30f, 0f, 20f, 40f});
		taken.add(new float[] {30f, 0f, 90f, 40f});
		float[] slot = CutoutAdlibs.slot(taken, 20f, 40f, 40f, 66f, 13f, 115f, 6);
		clear(taken, slot, 66f, 13f);
		assertTrue(slot[1] > 40f, "on a row of its own under the row");
	}

	@Test
	void wordsAreCounted() {
		assertEquals(6, CutoutAdlibs.words("How I see the same ones?"));
		assertEquals(1, CutoutAdlibs.words("  yeah "));
		assertEquals(0, CutoutAdlibs.words(" "));
	}
	@Test
	void anAdLibIsDrawnInBrackets() {
		assertEquals("(oh yeah)", CutoutAdlibs.text("oh yeah"));
		assertEquals("(oh yeah)", CutoutAdlibs.text(" (oh yeah) "));
	}
}
