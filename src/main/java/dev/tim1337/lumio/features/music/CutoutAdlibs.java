package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.Background;

import java.util.ArrayList;
import java.util.List;

/**
 * The ad-libs (background vocals) of a line in the Cut-out preset: a track of their own, apart from the line's words. An ad-lib is never one
 * of the words of the column, so those stand and come exactly the same with ad-libs and without. It shows from its own start, in a slot
 * beside the word being sung then, and fades out shortly after its own end; it does not stay. At most two show at once: when a third comes,
 * the oldest is gone by then. One that has no time of its own shows during the second half of its line. Plain logic, tested without the game.
 */
final class CutoutAdlibs {
	/** An ad-lib comes in over this time, and fades out over this time after its end. */
	static final long IN_MS = 180L;
	static final long OUT_MS = 260L;
	/** This many show at once at most. */
	static final int AT_ONCE = 2;
	/** An ad-lib of a line whose end is not known shows this long. */
	private static final long LENGTH_MS = 1_500L;
	/** The room between an ad-lib and what it stands beside, in text units; it is set this much smaller at most to fit. */
	static final float GAP = 5f;
	static final float[] FITS = {1f, 0.8f, 0.62f};

	private CutoutAdlibs() {
	}

	/**
	 * One ad-lib as it shows.
	 *
	 * @param source  which of the line's ad-libs it is
	 * @param startMs when it appears, and {@code endMs} when it begins to fade
	 */
	record Shown(int source, long startMs, long endMs) {
		/** @return when nothing of it is left */
		long goneMs() {
			return endMs + OUT_MS;
		}
	}

	/**
	 * @param adlibs    the line's ad-libs
	 * @param lineStart when the line starts, and {@code lineEnd} when it ends ({@link Long#MAX_VALUE} when that is not known)
	 * @return when each shows, in the order they start
	 */
	static List<Shown> track(List<Background> adlibs, long lineStart, long lineEnd) {
		List<Shown> track = new ArrayList<>(adlibs.size());
		for (int i = 0; i < adlibs.size(); i++) {
			Background adlib = adlibs.get(i);
			if (adlib.text().isBlank()) {
				continue;
			}
			long start = adlib.startMs();
			long end = adlib.endMs();
			if (end <= start) {
				// no time of its own: during the second half of its line
				boolean open = lineEnd == Long.MAX_VALUE || lineEnd <= lineStart;
				start = open ? lineStart + LENGTH_MS : lineStart + (lineEnd - lineStart) / 2;
				end = open ? start + LENGTH_MS : lineEnd;
			}
			track.add(new Shown(i, start, end));
		}
		track.sort((a, b) -> Long.compare(a.startMs(), b.startMs()));
		// never more than two at once: when another comes while that many still show, the oldest is gone by then
		for (int i = 0; i < track.size(); i++) {
			long start = track.get(i).startMs();
			int showing = 0;
			for (int j = i - 1; j >= 0; j--) {
				Shown before = track.get(j);
				if (before.goneMs() > start && ++showing >= AT_ONCE) {
					track.set(j, new Shown(before.source(), before.startMs(), Math.max(before.startMs(), start - OUT_MS)));
				}
			}
		}
		return track;
	}

	/** @return how strongly an ad-lib shows at a time, from 0 to 1: nothing before its start and after its end and the fade */
	static float strength(Shown adlib, long at) {
		if (at < adlib.startMs() || at >= adlib.goneMs()) {
			return 0f;
		}
		float in = Math.clamp((at - adlib.startMs()) / (float) IN_MS, 0f, 1f);
		float out = 1f - Math.clamp((at - adlib.endMs()) / (float) OUT_MS, 0f, 1f);
		// a short one that is cut off before it was all there does not jump: it goes from where it is
		float eased = 1f - (1f - in) * (1f - in);
		return eased * out * out * (3f - 2f * out);
	}

	/** An ad-lib of more words than this is long: it always gets a row of its own under the row being sung, never the slot beside it. */
	static final int MOST_WORDS_BESIDE = 4;

	/**
	 * Finds the place of an ad-lib, in this order: beside the row being sung (to the right, or to the left where the right has no room), set
	 * smaller down to the smallest that reads where it does not fit; else on a row of its own under the row being sung, and where a word of the
	 * line stands there, under the line's last row; a long ad-lib goes straight to a row of its own. Where none of that is free it is left out.
	 * It never lies over a word, a tile or a symbol of its line, also not one that only appears later, and never reaches out of the block.
	 *
	 * @param taken      every word of the line with its tile and symbol, whenever it appears, and the other ad-libs that show while this one
	 *                   does: each left, top, right, bottom
	 * @param middleY    the height of the row being sung, and {@code rowBottom} where that row ends
	 * @param lineBottom where the line's last row ends
	 * @param halfWidth  half the ad-lib's width and ({@code halfHeight}) height at its full size
	 * @param reach      how far from the middle of the column an ad-lib may reach, to either side: half the block's width
	 * @param words      how many words the ad-lib has
	 * @return its middle (across and down) and its size as a share of the full one; null when there is no room for it
	 */
	static float[] slot(List<float[]> taken, float middleY, float rowBottom, float lineBottom, float halfWidth, float halfHeight, float reach, int words) {
		if (words <= MOST_WORDS_BESIDE) {
			for (float fit : FITS) {
				float halfW = halfWidth * fit;
				float halfH = halfHeight * fit;
				// what stands at its height: it goes beside the outermost of those
				float right = 0f;
				float left = 0f;
				for (float[] box : taken) {
					if (box[3] > middleY - halfH - GAP && box[1] < middleY + halfH + GAP) {
						right = Math.max(right, box[2]);
						left = Math.min(left, box[0]);
					}
				}
				float atRight = right + GAP + halfW;
				if (atRight + halfW <= reach && free(taken, atRight, middleY, halfW, halfH)) {
					return new float[] {atRight, middleY, fit};
				}
				float atLeft = left - GAP - halfW;
				if (atLeft - halfW >= -reach && free(taken, atLeft, middleY, halfW, halfH)) {
					return new float[] {atLeft, middleY, fit};
				}
			}
		}
		// a row of its own: under the row being sung, in the middle or at either side of the block; else under the line's last row
		for (float under : rowBottom < lineBottom ? new float[] {rowBottom, lineBottom} : new float[] {lineBottom}) {
			for (float fit : FITS) {
				float halfW = halfWidth * fit;
				float halfH = halfHeight * fit;
				if (halfW > reach) {
					continue;
				}
				float y = under + GAP + halfH;
				for (float x : new float[] {0f, reach - halfW, halfW - reach}) {
					if (free(taken, x, y, halfW + GAP, halfH + GAP / 2f)) {
						return new float[] {x, y, fit};
					}
				}
			}
		}
		return null;
	}

	/** @return how many words a text has */
	static int words(String text) {
		int count = 0;
		boolean inWord = false;
		for (int i = 0; i < text.length(); i++) {
			boolean letter = !Character.isWhitespace(text.charAt(i));
			count += letter && !inWord ? 1 : 0;
			inWord = letter;
		}
		return count;
	}

	/** @return whether a box lies over none of those taken */
	static boolean free(List<float[]> taken, float x, float y, float halfWidth, float halfHeight) {
		for (float[] box : taken) {
			if (x - halfWidth < box[2] && x + halfWidth > box[0] && y - halfHeight < box[3] && y + halfHeight > box[1]) {
				return false;
			}
		}
		return true;
	}

	/** @return the text of an ad-lib as it is drawn: in brackets */
	static String text(String adlib) {
		String said = adlib.strip();
		return said.startsWith("(") || said.startsWith("[") ? said : "(" + said + ")";
	}
}
