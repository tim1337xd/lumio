package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The chorus of a song: the lines that come back. A line whose words appear again elsewhere in the song (ignoring case and punctuation) is part
 * of it; very short lines ("oh") do not count. Worked out once per lyrics and kept.
 */
public final class LyricChorus {
	/** Lines shorter than this, in letters, are too short to tell a chorus by. */
	private static final int SHORTEST = 8;
	/** A pause longer than this between two sayings of a line ends it, as any other line. */
	static final long REPEAT_PAUSE_MS = 2000L;

	private static List<LyricLine> of;
	private static boolean[] chorus = new boolean[0];

	private LyricChorus() {
	}

	/** @return for every line of the lyrics whether it is part of the chorus; the array is shared and must not be changed */
	public static boolean[] of(List<LyricLine> lines) {
		if (lines != of) {
			of = lines;
			Map<String, Integer> seen = new HashMap<>();
			String[] keys = new String[lines.size()];
			for (int i = 0; i < lines.size(); i++) {
				keys[i] = key(lines.get(i).text());
				if (keys[i].length() >= SHORTEST) {
					seen.merge(keys[i], 1, Integer::sum);
				}
			}
			boolean[] made = new boolean[lines.size()];
			for (int i = 0; i < made.length; i++) {
				made[i] = keys[i].length() >= SHORTEST && seen.getOrDefault(keys[i], 0) >= 2;
			}
			chorus = made;
		}
		return chorus;
	}

	/** @return whether the line at the index is part of the chorus */
	public static boolean at(List<LyricLine> lines, int index) {
		boolean[] marks = of(lines);
		return index >= 0 && index < marks.length && marks[index];
	}

	/** @return whether two lines say the same words, ignoring case and punctuation (and are not empty) */
	public static boolean sameWords(String a, String b) {
		String key = key(a);
		return !key.isEmpty() && key.equals(key(b));
	}

	/** @return whether the next line is the same line sung again soon after: shorter pauses than this between them keep the line on show */
	public static boolean repeats(String shownText, long shownEndMs, String nextText, long nextStartMs) {
		return sameWords(shownText, nextText) && nextStartMs - shownEndMs <= REPEAT_PAUSE_MS;
	}

	private static String key(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				out.append(Character.toLowerCase(c));
			}
		}
		return out.toString().toLowerCase(Locale.ROOT);
	}
}
