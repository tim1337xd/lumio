package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.util.Mth;

/**
 * The lyrics of the main menu: the line that is sung, large (on two lines when it is too long for one), and the next one, smaller and
 * quieter, centered with a soft shadow. A new line rises in while the old one rises out and fades.
 */
public final class MenuLyrics {
	/** The weight of the text a shadow is for. */
	public static final int REGULAR_FONT = 0;
	public static final int BOLD_FONT = 1;
	public static final int TITLE_FONT = 2;
	/** A line's height, in text units of its scale. */
	private static final float LINE = 11.52f;
	/** The gap between the two lines and how far a line rises when it changes, in text units of the sung line's scale. */
	private static final float GAP = 3f;
	private static final float LIFT = 5.4f;
	private static final float NEXT_OPACITY = 0.55f;
	private static final int SHADOW = 0xFF0A061E;

	private String now;
	private String before;
	private final Spring change = Spring.smooth(Spring.DEFAULT);
	private final Spring shown = Spring.smooth(Spring.DEFAULT);
	// the sung line broken into lines, kept until the line or the room changes
	private String splitText;
	private float splitRoom;
	private String[] split;

	/**
	 * Draws the lyrics, the lines centered on a point and resting on a bottom edge.
	 *
	 * @param line      the line that is sung, or null for none (the lyrics fade out)
	 * @param next      the line after it, or empty
	 * @param nowScale  the text scale of the sung line; the gaps follow it
	 * @param nextScale the text scale of the next line
	 */
	public void draw(Ui ui, String line, String next, float centerX, float bottom, float room, float nowScale, float nextScale, float alpha) {
		if (line != null && !line.equals(now)) {
			before = now;
			now = line;
			change.snap(0f);
		}
		float visible = Mth.clamp(shown.follow(ui, line != null ? 1f : 0f), 0f, 1f) * alpha;
		if (now == null || visible <= 0.01f) {
			return;
		}
		float changed = Mth.clamp(change.follow(ui, 1f), 0f, 1f);
		float nextTop = bottom - nextScale * LINE;
		float nowTop = nextTop - GAP * nowScale - nowScale * LINE;
		float lift = LIFT * nowScale;
		if (before != null && changed < 1f) {
			sung(ui, split(ui, before, room, nowScale), centerX, nowTop - changed * lift, room, nowScale, visible * (1f - changed));
		}
		if (!now.equals(splitText) || room != splitRoom) {
			splitText = now;
			splitRoom = room;
			split = split(ui, now, room, nowScale);
		}
		sung(ui, split, centerX, nowTop + (1f - changed) * lift, room, nowScale, visible * changed);
		if (next != null && !next.isEmpty()) {
			line(ui, next, centerX, nextTop + (1f - changed) * lift * 0.6f, room, nextScale, visible * changed * NEXT_OPACITY, false);
		}
	}

	/** Draws the sung line, its last part resting where a single line would be and a first part above it. */
	private void sung(Ui ui, String[] parts, float centerX, float top, float room, float textScale, float alpha) {
		float above = (parts.length - 1) * textScale * LINE;
		for (int i = 0; i < parts.length; i++) {
			line(ui, parts[i], centerX, top - above + i * textScale * LINE, room, textScale, alpha, true);
		}
	}

	/** @return the line as it is, or broken in two at the space nearest its middle when it is too wide for the room */
	private static String[] split(Ui ui, String text, float room, float textScale) {
		if (ui.boldWidthScaled(text, textScale) <= room) {
			return new String[]{text};
		}
		int middle = text.length() / 2;
		int best = -1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == ' ' && (best < 0 || Math.abs(i - middle) < Math.abs(best - middle))) {
				best = i;
			}
		}
		return best < 0 ? new String[]{text} : new String[]{text.substring(0, best), text.substring(best + 1)};
	}

	private void line(Ui ui, String text, float centerX, float top, float room, float textScale, float alpha, boolean bold) {
		if (alpha <= 0.01f) {
			return;
		}
		String fitted = ui.fitScaled(text, room, textScale);
		float x = centerX - (bold ? ui.boldWidthScaled(fitted, textScale) : ui.widthScaled(fitted, textScale)) / 2f;
		softShadow(ui, fitted, x, top + 2f, textScale, Ui.withOpacity(SHADOW, alpha), 2.5f, bold ? BOLD_FONT : REGULAR_FONT);
		if (bold) {
			ui.textAtScaledBold(fitted, x, top, textScale, Ui.withOpacity(0xFFFFFFFF, alpha));
		} else {
			ui.textAtScaled(fitted, x, top, textScale, Ui.withOpacity(0xFFFFFFFF, alpha));
		}
	}

	/**
	 * A soft shadow under text, in the text's own font so it lies exactly under it: the text drawn faintly a few times in a small circle. The
	 * blurred text of the toolkit is in the regular font, which peeked out beside bold text.
	 */
	public static void softShadow(Ui ui, String text, float x, float y, float textScale, int color, float radius, int font) {
		int faint = Ui.withOpacity(color, ((color >>> 24) / 255f) * 0.11f);
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI / 4.0 * i;
			float sx = x + (float) Math.cos(angle) * radius;
			float sy = y + (float) Math.sin(angle) * radius;
			switch (font) {
				case BOLD_FONT -> ui.textAtScaledBold(text, sx, sy, textScale, faint);
				case TITLE_FONT -> ui.textAtScaledTitle(text, sx, sy, textScale, faint);
				default -> ui.textAtScaled(text, sx, sy, textScale, faint);
			}
		}
	}
}
