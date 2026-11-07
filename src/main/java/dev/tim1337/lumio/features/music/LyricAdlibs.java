package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.Background;
import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adlibs in lyrics that do not mark background vocals: text in round brackets that stands on its own, such as "(oh, yeah)" at the end of a line,
 * is sung by a background voice. It is taken out of the line and becomes the line's adlib, with the timing of its words when the line has word
 * timings, otherwise placed by where it stands in the line. A line that is only such a bracket becomes an adlib sung on its own.
 */
final class LyricAdlibs {
	/** A bracket standing on its own: at the start or after a space, and at the end or before a space or punctuation. */
	private static final Pattern BRACKET = Pattern.compile("(?<=^|\\s)\\(([^()]{1,80})\\)(?=$|[\\s,.!?;:])");
	/** Without word timings a line is assumed to be sung over at most this long. */
	private static final long LONGEST_LINE_MS = 6500L;
	private static final long LAST_LINE_MS = 4000L;
	private static final long SHORTEST_ADLIB_MS = 700L;
	private static final long MS_PER_CHARACTER = 70L;

	private LyricAdlibs() {
	}

	/** @return the lines with their bracketed adlibs taken out; lines that already have background vocals are left as they are */
	static List<LyricLine> fromBrackets(List<LyricLine> lines) {
		List<LyricLine> out = null;
		for (int i = 0; i < lines.size(); i++) {
			LyricLine line = lines.get(i);
			if (!line.backgrounds().isEmpty() || line.text().indexOf('(') < 0) {
				if (out != null) {
					out.add(line);
				}
				continue;
			}
			long next = i + 1 < lines.size() ? lines.get(i + 1).timeMs() : line.timeMs() + LAST_LINE_MS;
			LyricLine split = split(line, Math.max(1L, Math.min(LONGEST_LINE_MS, next - line.timeMs())));
			if (split != line && out == null) {
				out = new ArrayList<>(lines.size());
				out.addAll(lines.subList(0, i));
			}
			if (out != null) {
				out.add(split);
			}
		}
		return out == null ? lines : List.copyOf(out);
	}

	/** @return the line without its bracketed adlibs and with them as background vocals, or the line itself if it has none */
	private static LyricLine split(LyricLine line, long lineMs) {
		String text = line.text();
		Matcher matcher = BRACKET.matcher(text);
		if (!matcher.find()) {
			return line;
		}
		int length = text.length();
		// where every character of the line goes in the main text, or -1 if it belongs to an adlib
		int[] map = new int[length + 1];
		StringBuilder main = new StringBuilder(length);
		List<int[]> groups = new ArrayList<>();
		int at = 0;
		do {
			int from = matcher.start();
			int to = matcher.end();
			// the space before the bracket goes with it, so no double space is left in the line
			int keep = from > at && text.charAt(from - 1) == ' ' ? from - 1 : from;
			copy(text, at, keep, main, map);
			for (int c = keep; c < from; c++) {
				map[c] = -1;
			}
			for (int c = from; c < to; c++) {
				map[c] = -1;
			}
			groups.add(new int[]{from, to});
			at = to;
		} while (matcher.find());
		copy(text, at, length, main, map);
		map[length] = main.length();
		String mainText = main.toString().strip();
		List<Background> backgrounds = new ArrayList<>(groups.size());
		for (int[] group : groups) {
			backgrounds.add(adlib(line, text, group[0], group[1], lineMs));
		}
		List<Word> mainWords = new ArrayList<>();
		if (!mainText.isEmpty()) {
			int lead = main.toString().indexOf(mainText.charAt(0));
			for (Word word : line.words()) {
				if (word.from() < length && map[word.from()] >= 0 && word.to() <= length && map[word.to() - 1] >= 0) {
					int from = map[word.from()] - lead;
					int to = map[word.to() - 1] + 1 - lead;
					if (from >= 0 && to <= mainText.length() && from < to) {
						mainWords.add(new Word(from, to, word.startMs(), word.endMs()));
					}
				}
			}
		}
		return new LyricLine(line.timeMs(), mainText, List.copyOf(mainWords), List.copyOf(backgrounds), line.singer());
	}

	/** Copies a part of the line to the main text, remembering where each character went. */
	private static void copy(String text, int from, int to, StringBuilder main, int[] map) {
		for (int c = from; c < to; c++) {
			map[c] = main.length();
			main.append(text.charAt(c));
		}
	}

	/** @return the adlib of a bracket in the line, timed by its words if the line has word timings, otherwise by its place in the line */
	private static Background adlib(LyricLine line, String text, int from, int to, long lineMs) {
		String said = text.substring(from, to);
		List<Word> words = new ArrayList<>();
		for (Word word : line.words()) {
			if (word.from() >= from && word.to() <= to) {
				words.add(new Word(word.from() - from, word.to() - from, word.startMs(), word.endMs()));
			}
		}
		if (!words.isEmpty()) {
			return new Background(said, List.copyOf(words), words.get(0).startMs(), words.get(words.size() - 1).endMs());
		}
		long start = line.timeMs() + lineMs * from / Math.max(1, text.length());
		long length = Math.max(SHORTEST_ADLIB_MS, said.length() * MS_PER_CHARACTER);
		return new Background(said, List.of(), start, start + length);
	}
}
