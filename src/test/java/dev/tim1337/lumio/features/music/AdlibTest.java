package dev.tim1337.lumio.features.music;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adlibs show however short their timing, keep a clear gap from their line and never overlap it or each other, and wrap to two rows at most. */
class AdlibTest {
	/** Six units per character, as a stand-in for the font. */
	private static final ToDoubleFunction<String> WIDTH = text -> text.length() * 6.0;
	private static final long FRAME_MS = 16L;

	/** @return how long an adlib shows when the lyrics play from a time on, frame by frame, as the motion asks for it */
	private static long shownFor(long startMs, long endMs, long fromMs) {
		long first = AdlibTimes.NOT_SHOWN;
		long shown = 0L;
		for (long now = fromMs; now < fromMs + 5000L; now += FRAME_MS) {
			boolean visible = AdlibTimes.shown(startMs, endMs, first, now);
			if (visible && first == AdlibTimes.NOT_SHOWN) {
				first = now;
			}
			if (visible) {
				shown += FRAME_MS;
			}
		}
		return shown;
	}

	@Test
	void aOneWordAdlibOf50MsShowsLongEnough() {
		assertTrue(shownFor(1000L, 1050L, 0L) >= AdlibTimes.LEAST_SHOWN_MS);
	}

	@Test
	void aZeroOrNegativeLengthStillShows() {
		assertTrue(shownFor(1000L, 1000L, 0L) >= AdlibTimes.LEAST_SHOWN_MS);
		assertTrue(shownFor(1000L, 900L, 0L) >= AdlibTimes.LEAST_SHOWN_MS);
		// even when the frame comes only after its whole time window has started
		assertTrue(AdlibTimes.due(1000L, 1000L, 1000L));
	}

	@Test
	void aSeekBackDoesNotKeepIt() {
		assertFalse(AdlibTimes.shown(5000L, 5050L, 5000L, 1000L));
	}

	@Test
	void threeQuickAdlibsInOneSecondEachShowAndStandApart() {
		long[] starts = {1000L, 1300L, 1700L};
		for (long start : starts) {
			assertTrue(shownFor(start, start + 60L, 0L) >= AdlibTimes.LEAST_SHOWN_MS);
		}
		// all three are on screen at once at some point, so each needs its own place
		long all = 1750L;
		for (long start : starts) {
			assertTrue(AdlibTimes.shown(start, start + 60L, start - AdlibTimes.LEAD_MS, all));
		}
		float[] widths = {40f, 60f, 30f};
		float[] heights = {20f, 20f, 20f};
		float[] spots = new float[6];
		AdlibLayout.place(-100f, -30f, 100f, 30f, 60f, widths, heights, 3, Float.MAX_VALUE, spots);
		assertApart(-100f, -30f, 100f, 30f, widths, heights, 3, spots);
	}

	@Test
	void aLongAdlibTakesTwoBalancedRowsAndKeepsTheGap() {
		String text = "yeah yeah yeah we are going all the way tonight";
		double room = 120.0;
		double wrap = AdlibLayout.wrapWidth(text, room, WIDTH);
		List<String> rows = LyricWrap.balanced(text, wrap, AdlibLayout.MOST_ROWS, WIDTH);
		assertEquals(2, rows.size(), "two rows: " + rows);
		// balanced: neither row is a single word
		for (String row : rows) {
			assertTrue(row.contains(" "), "a word alone: " + rows);
		}
		float widest = (float) Math.max(WIDTH.applyAsDouble(rows.get(0)), WIDTH.applyAsDouble(rows.get(1)));
		float scale = AdlibLayout.fitScale(widest * AdlibLayout.SCALE, (float) room);
		assertTrue(scale >= AdlibLayout.LEAST_SCALE && scale <= AdlibLayout.SCALE);
		float[] widths = {widest * scale};
		float[] heights = {2 * 38f * scale};
		float[] spots = new float[2];
		AdlibLayout.place(-60f, -26f, 60f, 26f, 52f, widths, heights, 1, Float.MAX_VALUE, spots);
		assertApart(-60f, -26f, 60f, 26f, widths, heights, 1, spots);
	}

	@Test
	void aShortAdlibStaysOnOneRowAtTheUsualSize() {
		assertEquals(200.0, AdlibLayout.wrapWidth("hey", 200.0, WIDTH));
		assertEquals(AdlibLayout.SCALE, AdlibLayout.fitScale(18f, 200f));
	}

	@Test
	void withoutRoomBelowAdlibsGoBesideTheLine() {
		float[] widths = {50f, 50f};
		float[] heights = {20f, 20f};
		float[] spots = new float[4];
		// room below for one adlib only
		AdlibLayout.place(-100f, -30f, 100f, 30f, 60f, widths, heights, 2, 30f + 30f + 21f, spots);
		assertApart(-100f, -30f, 100f, 30f, widths, heights, 2, spots);
		assertTrue(spots[2] >= 100f, "the second stands beside the line");
	}

	@Test
	void aLineInTwoRowsKeepsItsAdlibsBelowWithinTheRoomKeptForThem() {
		// two rows of 30, three quick one-row adlibs at the usual size: the room kept below the line holds all three
		float row = 30f;
		float[] widths = {40f, 40f, 45f};
		float[] heights = {row * AdlibLayout.SCALE, row * AdlibLayout.SCALE, row * AdlibLayout.SCALE};
		float[] spots = new float[6];
		// the room as the world lyrics keep it: in rows of the line and back, which rounds
		float rowUnit = 7.3f;
		float lowest = 30f + AdlibLayout.room(row, heights, 3) / rowUnit * rowUnit;
		AdlibLayout.place(-100f, -30f, 100f, 30f, row, widths, heights, 3, lowest, spots);
		assertApart(-100f, -30f, 100f, 30f, row, widths, heights, 3, spots);
		for (int i = 0; i < 3; i++) {
			assertTrue(spots[2 * i] < 100f, "adlib " + i + " stays below the line");
			assertTrue(spots[2 * i + 1] + heights[i] <= lowest + row * 0.01f, "adlib " + i + " stays within the room");
		}
		// the gap is half a row, not half the whole line
		assertEquals(30f + AdlibLayout.gap(row), spots[1], 0.01f);
	}

	@Test
	void inTheWorldAdlibsStandAQuarterRowFromTheirLine() {
		float row = 40f;
		float[] widths = {50f, 60f, 40f};
		float[] heights = {row * AdlibLayout.SCALE, row * AdlibLayout.SCALE, row * AdlibLayout.SCALE};
		float[] spots = new float[6];
		float lowest = 40f + AdlibLayout.room(row, AdlibLayout.WORLD_GAP_SHARE, heights, 3);
		AdlibLayout.place(-100f, -40f, 100f, 40f, row, AdlibLayout.WORLD_GAP_SHARE, widths, heights, 3, lowest, spots);
		assertEquals(40f + row * 0.25f, spots[1], 0.01f, "a quarter of a row below the line, its glow included");
		for (int i = 0; i < 3; i++) {
			assertTrue(spots[2 * i + 1] >= 40f + row * 0.25f - 0.01f, "adlib " + i + " never reaches into the line");
			assertTrue(spots[2 * i] < 100f, "adlib " + i + " still below the line");
			for (int k = 0; k < i; k++) {
				assertTrue(spots[2 * i + 1] >= spots[2 * k + 1] + heights[k] - 0.01f, "adlibs " + k + " and " + i + " overlap");
			}
		}
		// the room kept below the line is smaller than with the info bar's gap
		assertTrue(AdlibLayout.room(row, AdlibLayout.WORLD_GAP_SHARE, heights, 3) < AdlibLayout.room(row, heights, 3));
	}

	private static void assertApart(float left, float top, float right, float bottom, float[] widths, float[] heights, int count, float[] spots) {
		assertApart(left, top, right, bottom, bottom - top, widths, heights, count, spots);
	}

	/** Checks that every adlib keeps the gap (from one row's height) from the line's bounds and that no two adlibs overlap. */
	private static void assertApart(float left, float top, float right, float bottom, float rowHeight, float[] widths, float[] heights, int count,
									float[] spots) {
		float gap = AdlibLayout.gap(rowHeight);
		for (int i = 0; i < count; i++) {
			float x0 = spots[2 * i];
			float y0 = spots[2 * i + 1];
			float x1 = x0 + widths[i];
			float y1 = y0 + heights[i];
			// outside the line grown by the gap
			boolean clear = x1 <= left - gap + 0.01f || x0 >= right + gap - 0.01f || y1 <= top - gap + 0.01f || y0 >= bottom + gap - 0.01f;
			assertTrue(clear, "adlib " + i + " is closer than the gap to the line");
			for (int k = 0; k < i; k++) {
				boolean apart = x1 <= spots[2 * k] || x0 >= spots[2 * k] + widths[k] || y1 <= spots[2 * k + 1] || y0 >= spots[2 * k + 1] + heights[k];
				assertTrue(apart, "adlibs " + k + " and " + i + " overlap");
			}
		}
	}
}
