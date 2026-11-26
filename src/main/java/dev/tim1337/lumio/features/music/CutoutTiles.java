package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.features.music.MusicService.Word;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which word of a Cut-out line gets the tile, and which is its key word. Tiles belong to key words only: a word of at least four letters
 * that is no function word or swear word, one per line at most, and none in a line without a word that can take one. Each word gets a score: held longer than the other words of its line (by real or estimated timings),
 * the last strong word of the line (often the rhyme), a word of the chorus (it comes back in several lines), long and uncommon words higher,
 * (a word with a symbol as any other). The best word gets the tile when it is among the song's strongest third ({@link #bar}), never two lines in a row; after {@link #MOST_LINES_WITHOUT} lines
 * without one, the next line's best word gets it anyway, so a tile comes about every two to four lines. The key word ("Key word" emphasis) is the line's best word by
 * the same score. Plain logic, tested without the game.
 */
final class CutoutTiles {
	/** The fewest letters a word with a tile has. */
	static final int LEAST_LETTERS = 4;
	/** The least score of a word with a tile; a song sets its own bar above it ({@link #bar}). */
	static final float THRESHOLD = 0.9f;
	/** After this many lines in a row without a tile, the next line's best word (four letters or more, no filler or swear word) gets one. */
	static final int MOST_LINES_WITHOUT = 4;
	/** A word in this many lines of the song or more is a word of the chorus. */
	private static final int CHORUS_LINES = 3;
	/** What each reason adds to a word's score. */
	private static final float HELD = 1.2f;
	private static final float MOST_HELD = 1.6f;
	private static final float LAST_STRONG = 0.8f;
	private static final float CHORUS = 0.9f;
	private static final float PER_LETTER = 0.12f;
	private static final int LETTERS_COUNTED = 6;
	private static final float COMMON = -0.5f;
	/** No word. */
	static final int NONE = -1;
	/** Content words heard in nearly every song, which a tile should not go to before a rarer one. */
	private static final Set<String> COMMON_WORDS = Set.of("love", "baby", "know", "like", "time", "yeah", "never", "want", "just", "make", "come", "feel",
		"going", "gonna", "wanna", "really", "every", "always", "nothing", "something", "everything", "liebe", "immer", "wieder", "alles", "nichts", "einfach",
		"heute", "schon", "noch", "mehr", "machen", "gehen", "kommen", "wissen");

	/**
	 * The choice for a line.
	 *
	 * @param key  where the line's key word starts in its text, or {@link #NONE}
	 * @param tile where the word with the tile starts, or {@link #NONE} for a line without a tile
	 * @param pale whether its tile is the pale one (they take turns over the song)
	 */
	record Choice(int key, int tile, boolean pale) {
		static final Choice NONE_AT_ALL = new Choice(NONE, NONE, false);
	}

	private CutoutTiles() {
	}

	/** @return how many letters a word has; umlauts count, marks do not */
	static int letters(String word) {
		int count = 0;
		for (int i = 0; i < word.length(); i++) {
			count += Character.isLetter(word.charAt(i)) ? 1 : 0;
		}
		return count;
	}

	/** @return whether a word can get a tile at all: four letters or more, and no function word */
	static boolean eligible(String word, boolean known) {
		return letters(word) >= LEAST_LETTERS && !CutoutLexicon.small(word, known) && !CutoutLexicon.rude(word);
	}

	/** @return the words of a line with their times: its own, or estimated ones over its time */
	static List<Word> timed(LyricLine line, long nextStartMs) {
		List<Word> words = line.words();
		boolean none = words.isEmpty() || words.size() == 1 && words.getFirst().endMs() <= words.getFirst().startMs();
		return none ? LyricEstimate.words(line.text(), line.timeMs(), nextStartMs == Long.MAX_VALUE ? Long.MIN_VALUE : nextStartMs) : words;
	}

	/**
	 * @param chorus the plain words of the song's chorus
	 * @return each word's score; {@link Float#NEGATIVE_INFINITY} for one that cannot get a tile
	 */
	static float[] scores(String text, List<Word> words, Set<String> chorus) {
		int count = words.size();
		float[] scores = new float[count];
		String[] spelled = new String[count];
		List<String> all = new ArrayList<>(count);
		long total = 0L;
		for (int i = 0; i < count; i++) {
			Word word = words.get(i);
			spelled[i] = text.substring(Math.clamp(word.from(), 0, text.length()), Math.clamp(word.to(), 0, text.length()));
			all.add(spelled[i]);
			total += Math.max(0L, word.endMs() - word.startMs());
		}
		boolean known = CutoutLexicon.known(all);
		float mean = count == 0 ? 1f : Math.max(1f, total / (float) count);
		int lastStrong = NONE;
		for (int i = 0; i < count; i++) {
			lastStrong = eligible(spelled[i], known) ? i : lastStrong;
		}
		for (int i = 0; i < count; i++) {
			if (!eligible(spelled[i], known)) {
				scores[i] = Float.NEGATIVE_INFINITY;
				continue;
			}
			String plain = CutoutLexicon.plain(spelled[i]);
			Word word = words.get(i);
			float held = Math.max(0L, word.endMs() - word.startMs()) / mean - 1f;
			float score = Math.clamp(held * HELD, 0f, MOST_HELD);
			score += i == lastStrong ? LAST_STRONG : 0f;
			score += chorus.contains(plain) ? CHORUS : 0f;
			score += PER_LETTER * Math.min(letters(plain) - LEAST_LETTERS, LETTERS_COUNTED);
			score += COMMON_WORDS.contains(plain) ? COMMON : 0f;
			scores[i] = score;
		}
		return scores;
	}

	/** @return the index of the best score, or {@link #NONE} when no word can get a tile */
	static int best(float[] scores) {
		int best = NONE;
		for (int i = 0; i < scores.length; i++) {
			if (scores[i] != Float.NEGATIVE_INFINITY && (best == NONE || scores[i] > scores[best])) {
				best = i;
			}
		}
		return best;
	}

	/** @return the plain words of the song's chorus: those in {@link #CHORUS_LINES} of its lines or more */
	static Set<String> chorus(List<LyricLine> song) {
		Map<String, Integer> lines = new HashMap<>();
		for (LyricLine line : song) {
			Set<String> seen = new HashSet<>();
			for (String word : line.text().split("\\s+")) {
				String plain = CutoutLexicon.plain(word);
				if (!plain.isEmpty() && seen.add(plain)) {
					lines.merge(plain, 1, Integer::sum);
				}
			}
		}
		Set<String> chorus = new HashSet<>();
		lines.forEach((word, count) -> {
			if (count >= CHORUS_LINES) {
				chorus.add(word);
			}
		});
		return chorus;
	}

	/** @return the key of a line in a plan: its start and its text */
	static String keyOf(LyricLine line) {
		return line.timeMs() + "\u0000" + line.text();
	}

	/** @return the choice for every line of a song, by {@link #keyOf}; lines without words are left out */
	static Map<String, Choice> plan(List<LyricLine> song) {
		Set<String> chorus = chorus(song);
		Map<String, Choice> plan = new HashMap<>();
		// each line's best word and its score first: what counts as strong is measured against the song itself
		int[] keys = new int[song.size()];
		float[] bests = new float[song.size()];
		List<Float> strong = new ArrayList<>();
		for (int index = 0; index < song.size(); index++) {
			LyricLine line = song.get(index);
			keys[index] = NONE;
			bests[index] = Float.NEGATIVE_INFINITY;
			if (line.text().isBlank()) {
				continue;
			}
			long next = index + 1 < song.size() ? song.get(index + 1).timeMs() : Long.MAX_VALUE;
			List<Word> words = timed(line, next);
			float[] scores = scores(line.text(), words, chorus);
			int best = best(scores);
			if (best != NONE) {
				keys[index] = words.get(best).from();
				bests[index] = scores[best];
				strong.add(scores[best]);
			}
		}
		float bar = bar(strong);
		int tiles = 0;
		int without = 0;
		boolean lastHadTile = false;
		for (int index = 0; index < song.size(); index++) {
			LyricLine line = song.get(index);
			if (line.text().isBlank()) {
				continue;
			}
			int key = keys[index];
			// a tile for a strong key word, one per line at most and never two lines in a row; after a few lines without, the next one's best
			// word gets it anyway; the tiles take turns in color, so two neighbours in a block never match
			boolean strongHere = bests[index] >= bar && !lastHadTile;
			if (key != NONE && (strongHere || without >= MOST_LINES_WITHOUT)) {
				plan.put(keyOf(line), new Choice(key, key, tiles % 2 == 1));
				tiles++;
				without = 0;
				lastHadTile = true;
			} else {
				plan.put(keyOf(line), new Choice(key, NONE, false));
				without++;
				lastHadTile = false;
			}
		}
		return plan;
	}

	/** The share of a song's lines whose best word counts as strong: about a third, so a tile comes every two to four lines. */
	static final float STRONG_SHARE = 0.34f;

	/** @return the score a line's best word must reach in this song: that of its strongest third, never below {@link #THRESHOLD} */
	static float bar(List<Float> bests) {
		if (bests.isEmpty()) {
			return THRESHOLD;
		}
		List<Float> sorted = new ArrayList<>(bests);
		sorted.sort(java.util.Collections.reverseOrder());
		int at = Math.clamp(Math.round(sorted.size() * STRONG_SHARE) - 1, 0, sorted.size() - 1);
		return Math.max(THRESHOLD, sorted.get(at));
	}

	/** @return the choice for a line seen alone, without its song: its best word, with a tile when it beats the threshold */
	static Choice alone(LyricLine line, long nextStartMs) {
		List<Word> words = timed(line, nextStartMs);
		float[] scores = scores(line.text(), words, Set.of());
		int best = best(scores);
		if (best == NONE) {
			return Choice.NONE_AT_ALL;
		}
		int key = words.get(best).from();
		return new Choice(key, scores[best] >= THRESHOLD ? key : NONE, (line.text().hashCode() >>> 5 & 1) == 1);
	}

	/** The plan of the song last asked for, kept while its lines are the same list. */
	private static List<LyricLine> plannedFor;
	private static Map<String, Choice> planned = Map.of();

	/** @return the choice for a line of the song playing: from the song's plan, or the line's own when it is not in it */
	static Choice of(LyricLine line, List<LyricLine> song, long nextStartMs) {
		if (song != plannedFor) {
			plannedFor = song;
			planned = song == null ? Map.of() : plan(song);
		}
		Choice choice = planned.get(keyOf(line));
		return choice != null ? choice : alone(line, nextStartMs);
	}
}
