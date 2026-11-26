package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.CutoutWords.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cut-out layouts of a song's lines, kept so a line sung again looks as it did: the same line later (a returning chorus) and a line that
 * differs from an earlier one in a word or two ("… anything", then "… everything") get the earlier one's rows, sizes, key word, tile and nudge;
 * only a changed word is its own. Lines are known by their words, case, marks and ad-libs in brackets aside ({@link LyricChorus#lineKey}). The
 * times always stay the current line's own, so no word comes early or is missed because the text matched. Plain logic, tested without the game.
 */
final class CutoutRepeats {
	/** A line counts as the same as an earlier one with at most this many of its words changed, and fewer than half of them. */
	static final int MOST_CHANGED = 2;
	/** The most layouts a song keeps. */
	private static final int MOST_KNOWN = 400;

	/** A line's layout as it was first shown: its words (plain, lower case), its pieces and the seed of its nudges. */
	private record Known(String key, String[] words, List<Piece> pieces, int seed) {
	}

	/** A line's pieces as they are shown, and the seed its rows are nudged by. */
	record Laid(List<Piece> pieces, int seed) {
	}

	private final List<Known> known = new ArrayList<>();
	private Object song;

	/**
	 * @param song   what the song is (its lines as loaded): the kept layouts belong to it and go with it
	 * @param text   the line's text
	 * @param words  its words, plain and lower case, one for each of its pieces
	 * @param pieces its pieces as laid out for itself, with its own times
	 * @return the pieces to show: laid out as the same or a nearly same line before it where there is one, with this line's own times
	 */
	Laid lay(Object song, String text, List<String> words, List<Piece> pieces) {
		if (song != this.song) {
			this.song = song;
			known.clear();
		}
		String key = LyricChorus.lineKey(text);
		// the line's own words: ad-libs in brackets left in it are no part of what it is known by, and keep their own look
		List<Piece> own = new ArrayList<>(pieces.size());
		List<String> ownWords = new ArrayList<>(pieces.size());
		for (int i = 0; i < pieces.size(); i++) {
			if (!pieces.get(i).quiet()) {
				own.add(pieces.get(i));
				ownWords.add(words.get(i));
			}
		}
		Known same = null;
		Known near = null;
		for (Known each : known) {
			if (each.pieces.size() != own.size()) {
				continue;
			}
			if (each.key.equals(key)) {
				same = each;
				break;
			}
			int changed = changed(each.words, ownWords);
			if (near == null && changed <= MOST_CHANGED && changed * 2 < ownWords.size()) {
				near = each;
			}
		}
		Known earlier = same != null ? same : near;
		if (earlier == null) {
			int seed = key.isEmpty() ? text.hashCode() : key.hashCode();
			if (known.size() < MOST_KNOWN) {
				known.add(new Known(key, ownWords.toArray(new String[0]), own, seed));
			}
			return new Laid(pieces, seed);
		}
		List<Piece> laid = new ArrayList<>(pieces.size());
		int k = 0;
		for (int i = 0; i < pieces.size(); i++) {
			Piece mine = pieces.get(i);
			if (mine.quiet()) {
				// an ad-lib in brackets stands in the row of the word before it, so the rows stay in their order
				int row = laid.isEmpty() ? earlier.pieces.getFirst().group() : laid.getLast().group();
				laid.add(new Piece(mine.source(), mine.from(), mine.to(), mine.startMs(), mine.endMs(), mine.size(), mine.small(), true, false, false,
					CutoutLexicon.NO_SYMBOL, row, false));
				continue;
			}
			Piece before = earlier.pieces.get(k);
			String word = ownWords.get(k);
			k++;
			// a changed word keeps the place of the one it stands for; a symbol only where that one had room for one
			boolean changed = !earlier.words[k - 1].equals(word);
			int symbol = !changed ? before.symbol() : before.symbol() != CutoutLexicon.NO_SYMBOL ? mine.symbol() : CutoutLexicon.NO_SYMBOL;
			// a swear word or slur put in place of the key word never takes its tile or its part as the key word
			boolean plainOnly = changed && CutoutLexicon.rude(word);
			laid.add(new Piece(mine.source(), mine.from(), mine.to(), mine.startMs(), mine.endMs(), before.size(), before.small(), mine.quiet(), before.tile() && !plainOnly,
				before.pale(), plainOnly ? CutoutLexicon.NO_SYMBOL : symbol, before.group(), before.key() && !plainOnly));
		}
		return new Laid(laid, earlier.seed);
	}

	/** @return how many of two lines' words differ, place by place */
	private static int changed(String[] earlier, List<String> words) {
		int changed = 0;
		for (int i = 0; i < earlier.length; i++) {
			changed += earlier[i].equals(words.get(i)) ? 0 : 1;
		}
		return changed;
	}
}
