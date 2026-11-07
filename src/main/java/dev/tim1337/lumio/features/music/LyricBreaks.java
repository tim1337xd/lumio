package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The breaks of a song's lyrics, where nothing is sung for a while: the intro before the first line and every long gap between two lines. Each
 * becomes a line of its own, shown as three dots that fill one after another over the break, so the views bring it in and out like any other
 * line. It is no line of the song: it never counts as a repeated line and is not kept among the lines sung before. Plain logic, so it is unit
 * tested.
 */
public final class LyricBreaks {
	/** A break line's text: three dots with spaces, which the views light up one after another as a line is lit while it is sung (see MusicFeature.sungChars); no lyrics have this as a whole line. */
	static final String MARK = "\u2022 \u2022 \u2022";
	/** A gap at least this long (from one line's end to the next line's start) is a break; so is an intro this long. */
	static final long LEAST_BREAK_MS = 4_000L;
	/** A line without word timing is taken to last this long at least, or as long as it takes to read when it is long. */
	private static final long LEAST_LINE_MS = 3_000L;
	private static final long READ_BASE_MS = 1_000L;
	private static final long READ_WORD_MS = 350L;
	/** Before the next line the dots go: they shrink and fade over this time, so the line comes in after them. */
	static final long LEAVE_MS = 300L;

	private LyricBreaks() {
	}

	/** @return whether a line is a break, shown as dots */
	public static boolean is(LyricLine line) {
		return line != null && MARK.equals(line.text());
	}

	/** @return the lines with a break line put in at the start of the intro and of every long gap; the same list when there is none */
	static List<LyricLine> withBreaks(List<LyricLine> lines) {
		List<LyricLine> out = new ArrayList<>(lines.size() + 8);
		boolean any = false;
		long lastEnd = 0L;
		for (LyricLine line : lines) {
			if (LyricTimes.main(line) && !line.adlibOnly()) {
				if (line.timeMs() - lastEnd >= LEAST_BREAK_MS) {
					out.add(new LyricLine(lastEnd, MARK));
					any = true;
				}
				lastEnd = Math.max(lastEnd, end(line));
			}
			out.add(line);
		}
		if (!any) {
			return lines;
		}
		// a break starts where the line before it ended, which can be before an adlib sung in the gap: the lines stay in the order of their times
		out.sort(Comparator.comparingLong(LyricLine::timeMs));
		return List.copyOf(out);
	}

	/** @return when a line ends: its last word (or background vocal), or, without word timing, an estimate from its length */
	static long end(LyricLine line) {
		long end;
		if (!line.words().isEmpty()) {
			end = line.timeMs();
			for (MusicService.Word word : line.words()) {
				end = Math.max(end, word.endMs());
			}
		} else {
			end = line.timeMs() + Math.max(LEAST_LINE_MS, READ_BASE_MS + LyricTimes.words(line.text()) * READ_WORD_MS);
		}
		for (MusicService.Background background : line.backgrounds()) {
			end = Math.max(end, background.endMs());
		}
		return end;
	}

	/**
	 * @param startMs when the break began
	 * @param endMs   when the next line begins
	 * @return how far the break is, from 0 to 1, so that the third dot is full a moment before the next line
	 */
	static float progress(long startMs, long endMs, long at) {
		long length = Math.max(1L, endMs - startMs - LEAVE_MS);
		return Math.clamp((at - startMs) / (float) length, 0f, 1f);
	}

	/** @return how much of the dots is left as the next line comes: 1 until shortly before it, then down to 0 */
	static float leaving(long endMs, long at) {
		return Math.clamp((endMs - at) / (float) LEAVE_MS, 0f, 1f);
	}

	/** @return when the line after a break begins: the next main line's start, or a moment after the break when there is none */
	static long nextStart(List<LyricLine> lines, LyricLine breakLine) {
		boolean after = false;
		for (LyricLine line : lines) {
			if (line == breakLine) {
				after = true;
			} else if (after && LyricTimes.main(line) && !line.adlibOnly()) {
				return line.timeMs();
			}
		}
		return breakLine.timeMs() + LEAST_BREAK_MS;
	}

	/** @return how full one of the three dots is (0 to 2) at a progress of the break: one after another */
	static float dot(int index, float progress) {
		return Math.clamp(progress * 3f - index, 0f, 1f);
	}
}
