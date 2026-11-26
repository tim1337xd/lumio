package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.Background;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups very fast lines for the lyrics in the world: consecutive lines that are each shorter than a threshold become one block, a line of its
 * own with their texts under each other (a line break between them) that starts with the first and ends with the last. The block's word
 * timings are those of its lines, moved to their place in the block, so the highlight runs through the whole block in reading order; a line
 * without word timings gets them from {@link LyricSweep}. Lines that are not fast stay as they are, the same objects. Plain logic, tested
 * without the game.
 */
final class LyricGroups {
	/** The most lines of a block. */
	static final int MOST_LINES = 3;
	/** Between the lines of a block, in its text: a line break, which is one character like the space between rows. */
	static final char BREAK = '\n';

	private LyricGroups() {
	}

	/**
	 * @param thresholdMs a line shorter than this (from its start to the next line's) is fast
	 * @return the lines with every run of fast lines made into blocks of at most {@link #MOST_LINES}; the list itself when nothing is fast
	 */
	static List<LyricLine> group(List<LyricLine> lines, long thresholdMs) {
		List<LyricLine> out = null;
		int i = 0;
		while (i < lines.size()) {
			int run = 0;
			while (run < MOST_LINES && i + run < lines.size() && fast(lines, i + run, thresholdMs) && (run == 0
				|| lines.get(i + run).singer() == lines.get(i).singer())) {
				run++;
			}
			if (run < 2) {
				if (out != null) {
					out.add(lines.get(i));
				}
				i++;
				continue;
			}
			if (out == null) {
				out = new ArrayList<>(lines.subList(0, i));
			}
			out.add(block(lines, i, run));
			i += run;
		}
		return out == null ? lines : out;
	}

	/** @return whether a line is a sung line that the next line follows within the threshold */
	private static boolean fast(List<LyricLine> lines, int index, long thresholdMs) {
		LyricLine line = lines.get(index);
		if (!LyricTimes.main(line) || LyricBreaks.is(line) || index + 1 >= lines.size()) {
			return false;
		}
		LyricLine next = lines.get(index + 1);
		// an adlib on its own or a break after it ends the run: the line's length is not known from them
		return LyricTimes.main(next) && next.timeMs() - line.timeMs() < thresholdMs && next.timeMs() > line.timeMs();
	}

	private static LyricLine block(List<LyricLine> lines, int first, int count) {
		StringBuilder text = new StringBuilder();
		List<Word> words = new ArrayList<>();
		List<Background> backgrounds = new ArrayList<>();
		for (int k = 0; k < count; k++) {
			LyricLine line = lines.get(first + k);
			if (k > 0) {
				text.append(BREAK);
			}
			int offset = text.length();
			text.append(line.text());
			if (line.words().isEmpty()) {
				long next = first + k + 1 < lines.size() ? lines.get(first + k + 1).timeMs() : line.timeMs();
				words.addAll(LyricSweep.words(line.text(), line.timeMs(), next, offset));
			} else {
				for (Word word : line.words()) {
					words.add(new Word(word.from() + offset, word.to() + offset, word.startMs(), word.endMs()));
				}
			}
			backgrounds.addAll(line.backgrounds());
		}
		return new LyricLine(lines.get(first).timeMs(), text.toString(), words, backgrounds, lines.get(first).singer());
	}
}
