package dev.tim1337.lumio.features.music;

import java.util.List;

/**
 * Where the words of a Cut-out line stand and which of them show at a moment. A word is given its place once and never moves: the groups
 * stack downward from the top of the column, as many as are kept; then the column is full, and the next group starts a new page at the top,
 * the way a new verse does. The words of the full page go together just before it comes, so what shows always reads from the top down and
 * no word ever lies over another. While a group shows it is at full strength as the newest and dimmer as an older one; it comes in with a
 * short fade and goes with one. Every time here is the song's, so the motion is the same at any frame rate. Plain logic, tested without the
 * game.
 */
final class CutoutStack {
	/** A word comes in over this time; a page that is full goes over this time, or quicker (but not quicker than the least) in a fast part. */
	static final long IN_MS = 180L;
	static final long OUT_MS = 220L;
	static final long LEAST_OUT_MS = 90L;
	/** A group dims over this time when a newer one has come. */
	static final long DIM_MS = 260L;
	/** A word stays at full strength at least this long after its start, also when the next one has come. */
	static final long LEAST_SHOWN_MS = 350L;
	/** The last word of a full page is seen at least this long before the page goes, also in a fast part. */
	static final long LAST_SHOWN_MS = 140L;
	/** How strong the group before the newest is, and those before it. */
	static final float OLDER = 0.6f;
	static final float OLDEST = 0.42f;
	/** A small word waits at this strength in a group whose big word has come. */
	static final float SMALL_REST = 0.8f;

	private CutoutStack() {
	}

	/** @return when a group starts: when its first piece does */
	static long groupStart(List<CutoutWords.Piece> pieces, int group) {
		for (CutoutWords.Piece piece : pieces) {
			if (piece.group() == group) {
				return piece.startMs();
			}
		}
		return Long.MAX_VALUE;
	}

	/** @return the newest group that has started at a time, or -1 before the first */
	static int newest(List<CutoutWords.Piece> pieces, long at) {
		int newest = -1;
		for (CutoutWords.Piece piece : pieces) {
			if (piece.startMs() <= at) {
				newest = Math.max(newest, piece.group());
			}
		}
		return newest;
	}

	/**
	 * Gives every group its place: its top from the top of the column. The groups of a page stand one under the other; a page has as many
	 * groups as are kept.
	 *
	 * @param heights how tall each group is, with the room under it
	 * @param kept    how many groups stay
	 * @return the top of each group
	 */
	static float[] tops(float[] heights, int kept) {
		return tops(heights, kept, 0);
	}

	/** Same for a column that starts anew with a later group of the line (it was placed afresh): that group is the first of a page. */
	static float[] tops(float[] heights, int kept, int first) {
		float[] tops = new float[heights.length];
		float top = 0f;
		for (int group = 0; group < heights.length; group++) {
			if (group < first || (group - first) % kept == 0) {
				top = 0f;
			}
			tops[group] = top;
			top += heights[group];
		}
		return tops;
	}

	/**
	 * When the words of a line come and go, worked out once for the line.
	 *
	 * @param appear   when each piece comes in: at its start, or for the first words of a page a moment later, when the page before is gone
	 * @param goneFrom when each piece begins to go, and {@code goneFor} how long that takes; {@link Long#MAX_VALUE} for the last page, which stays
	 */
	record Timing(long[] appear, long[] goneFrom, long[] goneFor) {
	}

	/**
	 * @param kept how many groups stay: a page has that many
	 * @return when each piece of the line comes and goes. The words of a full page go together and are gone by the time the next page's first
	 *         word comes; in a fast part, where the page's last word has only just been sung, they go a moment later and quicker, and the next
	 *         page's first words wait that moment. So no word ever lies over another.
	 */
	static Timing timing(List<CutoutWords.Piece> pieces, int kept) {
		return timing(pieces, kept, 0);
	}

	/**
	 * Same for a column that starts anew with a later group of the line: the groups before it never show, and it is the first of a page.
	 *
	 * @param first the first group that shows
	 */
	static Timing timing(List<CutoutWords.Piece> pieces, int kept, int first) {
		int count = pieces.size();
		long[] appear = new long[count];
		long[] goneFrom = new long[count];
		long[] goneFor = new long[count];
		int groups = CutoutWords.groups(pieces);
		int pages = (Math.max(0, groups - first) + kept - 1) / kept;
		java.util.Arrays.fill(appear, Long.MAX_VALUE);
		java.util.Arrays.fill(goneFrom, Long.MAX_VALUE);
		java.util.Arrays.fill(goneFor, OUT_MS);
		// when each page is gone: the page before the first never was there
		long[] gone = new long[pages + 1];
		gone[0] = Long.MIN_VALUE;
		for (int page = 0; page < pages; page++) {
			int next = first + (page + 1) * kept;
			long from = Long.MAX_VALUE;
			long length = OUT_MS;
			if (next < groups) {
				long nextStart = groupStart(pieces, next);
				long last = Long.MIN_VALUE;
				for (CutoutWords.Piece each : pieces) {
					if (each.group() < next) {
						last = Math.max(last, each.startMs());
					}
				}
				from = Math.max(nextStart - OUT_MS, Math.min(last + LAST_SHOWN_MS, nextStart));
				length = Math.clamp(nextStart - from, LEAST_OUT_MS, OUT_MS);
			}
			gone[page + 1] = from == Long.MAX_VALUE ? Long.MAX_VALUE : from + length;
			for (int i = 0; i < count; i++) {
				CutoutWords.Piece piece = pieces.get(i);
				if (piece.group() >= first && (piece.group() - first) / kept == page) {
					appear[i] = Math.max(piece.startMs(), gone[page]);
					goneFrom[i] = from;
					goneFor[i] = length;
				}
			}
		}
		return new Timing(appear, goneFrom, goneFor);
	}

	/**
	 * @param index the piece
	 * @return how strongly a piece shows at a time, from 0 (not yet there, or gone) to 1 (the newest): how far it is there ({@link #shown}) times
	 *         how much of its strength it keeps as it grows older ({@link #rest})
	 */
	static float strength(List<CutoutWords.Piece> pieces, int index, long at, Timing timing) {
		return shown(index, at, timing) * rest(pieces, index, at);
	}

	/** @return how far a piece is there at a time, from 0 (not yet come, or gone) to 1: its way in and out alone */
	static float shown(int index, long at, Timing timing) {
		if (at < timing.appear()[index]) {
			return 0f;
		}
		return in(index, at, timing) * (1f - gone(index, at, timing));
	}

	/** @return how much of its strength a piece keeps at a time, from 1 (the newest) down to {@link #OLDEST} as newer groups come */
	static float rest(List<CutoutWords.Piece> pieces, int index, long at) {
		CutoutWords.Piece piece = pieces.get(index);
		int newest = newest(pieces, at);
		int age = newest - piece.group();
		if (age <= 0) {
			// in the newest group: the word sung now and the small ones before it
			boolean last = index + 1 >= pieces.size() || pieces.get(index + 1).startMs() > at;
			return last || at - piece.startMs() < LEAST_SHOWN_MS ? 1f : piece.small() ? SMALL_REST : 1f;
		}
		float rest = age == 1 ? OLDER : OLDEST;
		// a word only just sung is not dimmed at once
		long dimFrom = Math.max(groupStart(pieces, piece.group() + 1), piece.startMs() + LEAST_SHOWN_MS);
		float dim = smooth((at - dimFrom) / (float) DIM_MS);
		return 1f + (rest - 1f) * dim;
	}

	/** @return how far a piece has gone at a time, from 0 (it stands) to 1 (gone) */
	static float gone(int index, long at, Timing timing) {
		long from = timing.goneFrom()[index];
		return from == Long.MAX_VALUE ? 0f : smooth((at - from) / (float) timing.goneFor()[index]);
	}

	/** @return how far a piece has come in at a time, from 0 to 1: quick at first and settling, never past its place */
	static float in(int index, long at, Timing timing) {
		float t = Math.clamp((at - timing.appear()[index]) / (float) IN_MS, 0f, 1f);
		float left = 1f - t;
		return 1f - left * left * left;
	}

	private static float smooth(float t) {
		t = Math.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** @return whether a piece is the one being sung at a time: the last that has started */
	static boolean sung(List<CutoutWords.Piece> pieces, int index, long at) {
		return pieces.get(index).startMs() <= at && (index + 1 >= pieces.size() || pieces.get(index + 1).startMs() > at);
	}

	/**
	 * The time a line's words are shown by: the song's, but never running backwards by a little. A player's clock is corrected now and then
	 * by a few dozen milliseconds; shown as it is, a word that had just come would go and come again. A step back of more than
	 * {@link #SEEK_MS} is the listener seeking, and is followed.
	 */
	static long steady(long shown, long song) {
		return song >= shown || shown - song > SEEK_MS ? song : shown;
	}

	static final long SEEK_MS = 1_500L;
}
