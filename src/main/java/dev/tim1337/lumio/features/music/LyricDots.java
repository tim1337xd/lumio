package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.features.music.MusicService.LyricLine;
import dev.tim1337.lumio.ui.Ui;

/**
 * Draws the three dots of a break in the lyrics (see {@link LyricBreaks}), the same in the HUD and the info bar: over the break they fill one after
 * another, from dim to the lit color and a quarter larger, so the third is full just before the next line; the group breathes slowly, and at the
 * end the dots shrink and fade out before the next line comes in.
 */
final class LyricDots {
	/** A dot's size against the line's height, the gap between dots against a dot, how much larger a full dot is, and how bright an empty one is. */
	private static final float DOT_SHARE = 0.32f;
	private static final float GAP_SHARE = 0.9f;
	private static final float FULL_GROWTH = 0.25f;
	private static final float EMPTY_ALPHA = 0.35f;
	/** The breathing: how much the group grows at most, and how long one breath takes. */
	private static final float BREATH = 0.08f;
	private static final float BREATH_MS = 2_000f;
	private static final int DOTS = 3;

	private LyricDots() {
	}

	/** @return how wide the dots are for a line of this height, at rest */
	static float width(float lineHeight) {
		float dot = lineHeight * DOT_SHARE;
		return DOTS * dot + (DOTS - 1) * dot * GAP_SHARE;
	}

	/** @return how large the group is now, breathing slowly between 1 and a little more, from the break's start */
	static float breath(LyricLine line, long at) {
		return 1f + BREATH * 0.5f * (1f - (float) Math.cos((at - line.timeMs()) / BREATH_MS * Math.PI * 2.0));
	}

	/** @return how much of the group is left as the next line comes: it shrinks and fades to nothing just before it */
	static float leaving(LyricLine line, long at) {
		return LyricBreaks.leaving(LyricBreaks.nextStart(MusicService.lyrics(), line), at);
	}

	/**
	 * @param x          where the line's text starts
	 * @param middleY    the middle of the line's text, where the dots stand
	 * @param lineHeight the height of the line's text
	 * @param lit        the color of a full dot, with its opacity
	 * @param at         the time in the song
	 */
	static void draw(Ui ui, LyricLine line, long at, float x, float middleY, float lineHeight, int lit) {
		long next = LyricBreaks.nextStart(MusicService.lyrics(), line);
		float progress = LyricBreaks.progress(line.timeMs(), next, at);
		float leaving = LyricBreaks.leaving(next, at);
		if (leaving <= 0f) {
			return;
		}
		float breath = breath(line, at);
		float dot = lineHeight * DOT_SHARE;
		float step = dot * (1f + GAP_SHARE);
		float alpha = (lit >>> 24) / 255f;
		for (int i = 0; i < DOTS; i++) {
			float fill = LyricBreaks.dot(i, progress);
			float size = dot * (1f + FULL_GROWTH * fill) * breath * leaving;
			float centerX = x + dot / 2f + i * step;
			int color = Ui.withOpacity(lit | 0xFF000000, alpha * (EMPTY_ALPHA + (1f - EMPTY_ALPHA) * fill) * leaving);
			ui.roundedSmooth(centerX - size / 2f, middleY - size / 2f, size, size, size / 2f, color);
		}
	}
}
