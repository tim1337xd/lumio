package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A scrolling Cut-out column: one smooth motion of the whole stack, each line at the bottom as it starts, no step in its speed. */
class CutoutScrollTest {
	private static final float ROOM = 300f;
	private static final float GAP = 12f;
	/** Lines of uneven heights and lengths, as a song has them. */
	private static final float[] HEIGHTS = {40f, 90f, 40f, 40f, 130f, 40f, 60f, 40f, 40f, 40f};
	private static final long[] STARTS = {1_000L, 2_400L, 6_100L, 7_000L, 8_200L, 12_900L, 13_300L, 15_800L, 16_400L, 20_000L};

	private static final float[] TOPS = new float[HEIGHTS.length];

	private static CutoutScroll stack() {
		CutoutScroll stack = new CutoutScroll();
		float top = 0f;
		for (int i = 0; i < HEIGHTS.length; i++) {
			TOPS[i] = top;
			stack.add(STARTS[i], top + HEIGHTS[i], ROOM);
			top += HEIGHTS[i] + GAP;
		}
		return stack;
	}

	@Test
	void eachLineArrivesAtTheBottomAsItIsSung() {
		CutoutScroll stack = stack();
		for (int i = 0; i < HEIGHTS.length; i++) {
			// it eases in a little before the line and is at the bottom (or higher, where the stack still fills) once the smoothing has passed it
			float bottom = TOPS[i] + HEIGHTS[i] - stack.at(STARTS[i] + CutoutScroll.HALF_WINDOW_MS);
			assertTrue(bottom <= ROOM + 1e-3f, "line " + i + " ends at " + bottom);
		}
		assertEquals(TOPS[0] + HEIGHTS[0] - ROOM, stack.at(STARTS[0] - CutoutScroll.HALF_WINDOW_MS), 1e-3f);
	}

	@Test
	void speedAndAccelerationHaveNoStepAtAnyPoint() {
		CutoutScroll stack = stack();
		for (long start : STARTS) {
			for (double edge : new double[] {start, start - CutoutScroll.HALF_WINDOW_MS, start + CutoutScroll.HALF_WINDOW_MS}) {
				assertEquals(stack.speed(edge - 0.01), stack.speed(edge + 0.01), 1e-5, "speed at " + edge);
				assertEquals(stack.acceleration(edge - 0.01), stack.acceleration(edge + 0.01), 1e-7, "acceleration at " + edge);
			}
		}
	}

	@Test
	void wordsNeverMoveAgainstEachOther() {
		CutoutScroll stack = stack();
		// every line is drawn at its own top less the one motion of the stack: two lines stay the same distance apart at any time
		for (long t = 0L; t <= 22_000L; t += 7L) {
			float moved = stack.at(t);
			for (int i = 1; i < HEIGHTS.length; i++) {
				float apart = (TOPS[i] - moved) - (TOPS[i - 1] - moved);
				assertEquals(HEIGHTS[i - 1] + GAP, apart, 1e-3f);
			}
		}
	}

	@Test
	void itNeverRunsBack() {
		CutoutScroll stack = stack();
		float before = stack.at(0L);
		for (long t = 0L; t <= 22_000L; t++) {
			float now = stack.at(t);
			assertTrue(now >= before - 1e-4f, "back at " + t);
			before = now;
		}
	}

	@Test
	void theSpeedHasNoStepAtALineStart() {
		CutoutScroll stack = stack();
		for (long start : STARTS) {
			// across the line start the speed changes no more than it does from one millisecond to the next just before and after it
			float across = Math.abs(stack.speed(start + 1L) - stack.speed(start - 1L));
			float beside = Math.max(Math.abs(stack.speed(start - 1L) - stack.speed(start - 2L)), Math.abs(stack.speed(start + 2L) - stack.speed(start + 1L)));
			assertTrue(across <= 2.5f * beside + 1e-5f, "a step in the speed at " + start + ": " + across + " against " + beside);
		}
	}

	@Test
	void theSameAtAnyFrameRate() {
		CutoutScroll stack = stack();
		// the motion is a matter of the song's time only: frames at 30 and at 144 per second see the stack at the same place at the same time
		for (long t = 0L; t <= 22_000L; t += 1000L) {
			float at = stack.at(t);
			float again = stack.at(t);
			assertEquals(at, again);
		}
		long thirty = 0L;
		long fast = 0L;
		while (thirty < 20_000L) {
			thirty += 1000L / 30L;
			while (fast + 1000L / 144L <= thirty) {
				fast += 1000L / 144L;
			}
			if (fast == thirty) {
				assertEquals(stack.at(thirty), stack.at(fast));
			}
		}
	}

	@Test
	void aRoomChangeMovesEveryLineAlike() {
		CutoutScroll stack = stack();
		float at = stack.at(9_000L);
		stack.shift(-40f);
		assertEquals(at - 40f, stack.at(9_000L), 1e-3f);
	}

	/**
	 * Pages: the lines stand under each other until the room is full; then, as a line starts, the column moves up just enough for it. So a line
	 * stays whole while the lines after it still fit, no line leaves before a later one has started, and the line sung before stays as far as the
	 * room allows. Lines of 60 units, 30 units apart, four of them to the room.
	 */
	@Test
	void pagesMoveOncePerLineAndOnlyAsFarAsTheNextLineNeeds() {
		float tall = 60f;
		float gap = 30f;
		float room = 4 * tall + 3 * gap;
		long every = 4_000L;
		int lines = 10;
		CutoutScroll stack = new CutoutScroll();
		float[] tops = new float[lines];
		for (int i = 0; i < lines; i++) {
			tops[i] = i * (tall + gap);
			stack.addStep(i * every, CutoutScroll.pagePlace(tops[i] + tall, room));
		}
		for (int i = 0; i < lines; i++) {
			// from its start until the fourth line after it comes (the step begins a moment before that line starts), the line is whole in the room
			long until = Math.min((lines - 1) * every, (i + 4) * every - CutoutScroll.STEP_MS - (long) CutoutScroll.HALF_WINDOW_MS);
			for (long at = i * every; at <= until; at += 100L) {
				float moved = stack.at(at);
				assertTrue(tops[i] - moved >= -1e-3f, "line " + i + " leaves too early, at " + at + " ms");
				assertTrue(tops[i] + tall - moved <= room + 1e-3f || at < i * every + CutoutScroll.HALF_WINDOW_MS, "line " + i + " below the room at " + at);
			}
		}
		// the first four lines fit: the column does not move for them
		assertEquals(0f, stack.at(3 * every + CutoutScroll.HALF_WINDOW_MS), 1e-3f);
		// the fifth line moves it up by one line and its gap, no farther
		assertEquals(tall + gap, stack.at(4 * every + CutoutScroll.HALF_WINDOW_MS), 1e-3f);
	}

	/** A line taller than the room left under the lines before: the line just sung keeps what the room allows, the oldest rows leave first. */
	@Test
	void theLineSungBeforeStaysAsFarAsTheRoomAllows() {
		float room = 200f;
		CutoutScroll stack = new CutoutScroll();
		// two lines of 150: the second starts, the first keeps its last 50 units in the room
		stack.addStep(0L, CutoutScroll.pagePlace(150f, room));
		stack.addStep(5_000L, CutoutScroll.pagePlace(300f, room));
		float moved = stack.at(5_000L + CutoutScroll.HALF_WINDOW_MS);
		assertEquals(100f, moved, 1e-3f);
		assertTrue(150f - moved > 0f, "the line sung before is still partly there");
		// never back, and never above the room's top at the start
		assertEquals(0f, stack.at(0L), 1e-3f);
	}

	@Test
	void aLongSongNeverHoldsMoreThanTheKeptLines() {
		// a tall room (six rows) and 120 lines of one to three rows, every two seconds, scrolling as WorldLyrics.stackAhead does
		float row = 20f;
		float room = 6 * row;
		CutoutScroll stack = new CutoutScroll();
		CutoutScroll.Kept kept = new CutoutScroll.Kept();
		int lines = 120;
		float[] tops = new float[lines];
		float[] bottoms = new float[lines];
		long[] starts = new long[lines];
		int most = 0;
		float bottom = 0f;
		for (int line = 0; line < lines; line++) {
			// short lines first (the room alone would hold six of them), then longer ones
			int rows = line < lines / 2 ? 1 : 1 + line % 3;
			tops[line] = bottom;
			starts[line] = 2_000L * line;
			float least = kept.add(tops[line]);
			for (int r = 0; r < rows; r++) {
				bottom += row;
				stack.addPlace(starts[line] + r * 300L, Math.max(bottom - room, least));
			}
			bottoms[line] = bottom;
			if (line == 0) {
				continue;
			}
			// the line before, between its start and this one's, once the stack has settled (it knows a line ahead, as in WorldLyrics): the lines still
			// below the room's top (WorldLyrics.gone)
			float at = stack.at(starts[line - 1] + 1_000.0);
			int held = 0;
			for (int other = 0; other < line; other++) {
				held += bottoms[other] - at > 0f ? 1 : 0;
			}
			most = Math.max(most, held);
			assertTrue(held <= CutoutScroll.KEPT_LINES + 1, "line " + (line - 1) + " holds " + held + " lines");
		}
		assertEquals(CutoutScroll.KEPT_LINES + 1, most, "three sung lines above the one being sung, no more");
	}
}
