package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import java.util.List;

/**
 * When the lines of a song are sung: which main line (one with text, not only an adlib) is sung at a time, which comes next, and when a line
 * ends. Shared by the views that follow the lines' timing (the info bar, the cinematic camera).
 */
public final class LyricTimes {
	/** Two lines with the same text this close are the same line, with its time adjusted, not the same words sung again. */
	private static final long SAME_LINE_MILLIS = 1500L;

	/** @return whether two lines are the same one: the same text at about the same time */
	static boolean sameLine(String text, long startMs, String otherText, long otherStartMs) {
		return text != null && text.equals(otherText) && Math.abs(startMs - otherStartMs) <= SAME_LINE_MILLIS;
	}

	/** A line without word timing is estimated to last this long, plus this long per word. */
	private static final long LINE_BASE_MS = 1200L;
	private static final long LINE_WORD_MS = 350L;

	private LyricTimes() {
	}

	/** @return whether a line has text of its own, rather than being only an adlib or an empty pause */
	public static boolean main(LyricLine line) {
		return !line.text().isBlank();
	}

	/** @return the index of the main line sung at a time (the last one started), or -1 */
	public static int mainIndexAt(List<LyricLine> lines, long at) {
		int found = -1;
		for (int i = 0; i < lines.size(); i++) {
			LyricLine line = lines.get(i);
			if (line.timeMs() > at) {
				break;
			}
			if (!line.adlibOnly()) {
				found = main(line) ? i : -1;
			}
		}
		return found;
	}

	/** A step back of the song's clock shorter than this (a player reporting its time late) is no going back to the line before. */
	static final long RESYNC_MS = 600L;

	/**
	 * @param shown the index of the line shown so far, or -1
	 * @return the line to show at a time: the one sung, but the one shown so far when the clock only stepped back a little before its start, so a
	 *         small correction of the time never brings the line before back; a real jump back (a seek) still does
	 */
	public static int steadyIndex(List<LyricLine> lines, long at, int found, int shown) {
		if (found >= 0 && shown > found && shown < lines.size() && lines.get(shown).timeMs() - at < RESYNC_MS) {
			return shown;
		}
		return found;
	}

	/** @return the index of the next main line after a time, or -1 */
	public static int nextMainIndex(List<LyricLine> lines, long at) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).timeMs() > at && main(lines.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/** @return when a line ends: its last word, or an estimate from its words, and at the latest when the next line starts */
	public static long lineEnd(List<LyricLine> lines, int index) {
		LyricLine line = lines.get(index);
		long end;
		if (LyricBreaks.is(line)) {
			// a break lasts until the next line
			end = Long.MAX_VALUE;
		} else if (!line.words().isEmpty()) {
			end = line.words().get(line.words().size() - 1).endMs();
		} else {
			end = line.timeMs() + LINE_BASE_MS + words(line.text()) * LINE_WORD_MS;
		}
		for (int i = index + 1; i < lines.size(); i++) {
			if (!lines.get(i).adlibOnly()) {
				end = Math.min(end, lines.get(i).timeMs());
				break;
			}
		}
		return end;
	}

	/** @return how many words a text has */
	public static int words(String text) {
		int count = 0;
		boolean inWord = false;
		for (int i = 0; i < text.length(); i++) {
			boolean letter = !Character.isWhitespace(text.charAt(i));
			if (letter && !inWord) {
				count++;
			}
			inWord = letter;
		}
		return count;
	}
}
