package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** A fast song in the world: no line is ever left out, their order is kept, fast lines group into blocks, and the oldest line makes room. */
class WorldLyricPaceTest {
	/** Lines of 0.3 to 0.8 s, one after another from 10 s on. */
	private static List<LyricLine> fastSong() {
		long[] lengths = {300, 450, 800, 300, 300, 350, 600, 800, 400, 300, 750, 500};
		List<LyricLine> lines = new ArrayList<>();
		long at = 10_000;
		for (int i = 0; i < lengths.length; i++) {
			lines.add(new LyricLine(at, "line " + i));
			at += lengths[i];
		}
		lines.add(new LyricLine(at, "the last line"));
		return lines;
	}

	/** @return the lines started while the clock runs through the song in steps: the sung one, with those it passed before it */
	private static List<Integer> startedWith(List<LyricLine> lines, long step, LongStep clock) {
		List<Integer> started = new ArrayList<>();
		int spawned = -1;
		for (long position = 9_500; position < 18_000; position += step) {
			long at = clock.at(position);
			int current = LyricTimes.mainIndexAt(lines, at);
			if (current < 0 || current == spawned) {
				continue;
			}
			for (int i = WorldLyricPace.firstToStart(lines, spawned, current, at); i <= current; i++) {
				started.add(i);
			}
			spawned = current;
		}
		return started;
	}

	private interface LongStep {
		long at(long position);
	}

	private static List<Integer> all(int count) {
		List<Integer> all = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			all.add(i);
		}
		return all;
	}

	@Test
	void everyLineOfAFastSongStartsOnceAndInOrder() {
		List<LyricLine> lines = fastSong();
		// a look every frame, a look every tenth of a second, and one so coarse that whole lines fall between two looks
		for (long step : new long[] {16, 100, 250, 700}) {
			assertEquals(all(lines.size()), startedWith(lines, step, position -> position), "a look every " + step + " ms");
		}
	}

	@Test
	void aStepOfTheClockLeavesNoLineOut() {
		List<LyricLine> lines = fastSong();
		// the player reports its position late: the clock stands for a moment, then steps ahead over two short lines
		assertEquals(all(lines.size()), startedWith(lines, 16, position -> position < 11_500 || position > 12_300 ? position : 11_500));
	}

	@Test
	void theLeadOfTheClockNeverJumpsOverALine() {
		// the lead wanted changes at once by 340 ms when the line ahead is long instead of short; the lead used only drifts
		LyricLead lead = new LyricLead();
		long before = Long.MIN_VALUE;
		for (long position = 0; position < 6_000; position += 16) {
			long wanted = position / 700 % 2 == 0 ? 300 : 640;
			long clock = position + lead.at(position, wanted);
			assertTrue(clock >= before, "the clock never steps back");
			assertTrue(before == Long.MIN_VALUE || clock - before <= 16 + 4, "and never ahead by more than the song moved, plus its drift");
			before = clock;
		}
		// it gets to the wanted lead when that stays
		LyricLead steady = new LyricLead();
		long last = 0;
		for (long position = 0; position < 6_000; position += 16) {
			last = steady.at(position, position < 100 ? 300 : 640);
		}
		assertEquals(640, last);
		// a seek takes the wanted lead at once
		assertEquals(300, steady.at(90_000, 300));
	}

	@Test
	void aSeekStartsOnlyTheLineSung() {
		List<LyricLine> lines = fastSong();
		long far = lines.get(12).timeMs() + 100;
		assertEquals(12, WorldLyricPace.firstToStart(lines, 2, 12, far + 60_000), "lines long past are not brought back");
		// at most a few at once
		assertEquals(12 - (WorldLyricPace.MOST_AT_ONCE - 1), WorldLyricPace.firstToStart(lines, -1, 12, lines.get(12).timeMs()));
		// adlibs on their own are passed over, a break ends the catching up
		List<LyricLine> withAdlib = List.of(new LyricLine(1_000, "one"), new LyricLine(1_300, "", List.of(), new MusicService.Background("hey", List.of(), 1_300, 1_500)),
			new LyricLine(1_600, "two"));
		assertEquals(0, WorldLyricPace.firstToStart(withAdlib, -1, 2, 1_700));
	}

	@Test
	void fadesFollowTheLinesLength() {
		assertEquals(0.25f, WorldLyricPace.fadeSeconds(WorldLyricPace.Fade.AUTO, 4_000, 0.25f), "a long line: the usual fade");
		assertEquals(0.2f, WorldLyricPace.fadeSeconds(WorldLyricPace.Fade.AUTO, 800, 0.25f), 1e-6, "a quarter of a short line's length");
		assertEquals(WorldLyricPace.AT_ONCE_SECONDS, WorldLyricPace.fadeSeconds(WorldLyricPace.Fade.AUTO, 400, 0.25f), "very short: almost at once");
		assertEquals(0.25f, WorldLyricPace.fadeSeconds(WorldLyricPace.Fade.AUTO, Long.MAX_VALUE, 0.25f));
		assertEquals(0.6f, WorldLyricPace.fadeSeconds(WorldLyricPace.Fade.SLOW, 300, 0.6f));
		assertEquals(WorldLyricPace.FAST_SECONDS, WorldLyricPace.fadeSeconds(WorldLyricPace.Fade.FAST, 5_000, 0.6f));
	}

	@Test
	void theOldestMakesRoomOnceItWasReadable() {
		long[] starts = {3_000, 1_000, 2_000, 0};
		boolean[] staying = {true, true, true, false};
		assertEquals(1, WorldLyricPace.toEvict(starts, new float[] {0.2f, 2f, 1f, 9f}, staying, 2), "three stay, two may: the oldest goes");
		assertEquals(-1, WorldLyricPace.toEvict(starts, new float[] {0.2f, 2f, 1f, 9f}, staying, 3));
		assertEquals(-1, WorldLyricPace.toEvict(starts, new float[] {0.2f, 0.3f, 0.25f, 9f}, staying, 2), "not before it was readable for half a second");
		assertEquals(1, WorldLyricPace.toEvict(starts, new float[] {0.2f, 0.5f, 0.25f, 9f}, staying, 0));
		assertEquals(-1, WorldLyricPace.toEvict(starts, new float[4], new boolean[4], 0), "nothing stays: nothing to go");
	}

	@Test
	void fastLinesGroupIntoBlocks() {
		// 0.4 s, 0.4 s, 0.4 s, 0.4 s, then a long line: two blocks of three and... the fourth joins what is left
		List<LyricLine> lines = List.of(new LyricLine(1_000, "na na"), new LyricLine(1_400, "hey hey"), new LyricLine(1_800, "oh"), new LyricLine(2_200, "yeah"),
			new LyricLine(2_600, "a long line that takes its time"), new LyricLine(6_000, "another slow one"), new LyricLine(9_000, "the end"));
		List<LyricLine> grouped = LyricGroups.group(lines, 1_000);
		assertEquals(5, grouped.size());
		LyricLine block = grouped.get(0);
		assertEquals("na na\nhey hey\noh", block.text(), "up to three lines under each other");
		assertEquals(1_000, block.timeMs());
		assertSame(lines.get(3), grouped.get(1), "a fast line alone stays a line");
		assertSame(lines.get(4), grouped.get(2));
		// the block's words run through all of it in reading order, each line from its own start
		List<Word> words = block.words();
		assertEquals(5, words.size());
		assertEquals(0, words.get(0).from());
		assertEquals(1_000, words.get(0).startMs());
		assertEquals("hey".length() + 6, words.get(2).to());
		assertEquals(1_400, words.get(2).startMs(), "the second line starts at its own time");
		assertEquals(block.text().length(), words.get(4).to());
		assertEquals(1_800, words.get(4).startMs());
		for (int i = 1; i < words.size(); i++) {
			assertTrue(words.get(i).startMs() >= words.get(i - 1).endMs() && words.get(i).from() > words.get(i - 1).from(), "in order, never back");
		}
		// the highlight is at 0 when the block starts and never ahead of a line's own start
		assertEquals(0f, LyricSweep.sung(block, 9_000L, 1_000));
		assertTrue(LyricSweep.sung(block, 9_000L, 1_399) <= "na na".length());
		assertEquals(block.text().length(), LyricSweep.sung(block, 9_000L, 2_200));
		// real word timings are kept exactly, moved to their place in the block
		List<LyricLine> timed = List.of(new LyricLine(1_000, "go go", List.of(new Word(0, 2, 1_000, 1_100), new Word(3, 5, 1_150, 1_300))),
			new LyricLine(1_500, "now", List.of(new Word(0, 3, 1_520, 1_700))), new LyricLine(2_000, "a slow line"), new LyricLine(8_000, "end"));
		LyricLine timedBlock = LyricGroups.group(timed, 1_000).get(0);
		assertEquals("go go\nnow", timedBlock.text());
		assertEquals(new Word(6, 9, 1_520, 1_700), timedBlock.words().get(2));
		// nothing fast, or grouping only above the threshold: the list itself
		assertSame(timed, LyricGroups.group(timed, 400));
		// a block's rows: each of its lines keeps rows of its own
		assertEquals(List.of("na na", "hey hey", "oh"), LyricWrap.balanced(block.text(), 300, 4, text -> text.length() * 10));
		assertEquals(List.of("na na hey", "hey oh"), LyricWrap.balanced(block.text(), 90, 2, text -> text.length() * 10), "too many rows: as one line");
	}
}
