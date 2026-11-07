package dev.tim1337.lumio.api.experimental.draw;

import dev.tim1337.lumio.api.draw.Canvas;
/**
 * Text that is replaced from time to time, like the info bar numbers: only the changed characters animate (the old one up and out, the new one in
 * from below), unchanged characters stay put. Old and new text are right-aligned, as numbers are.
 */
public final class SlideText {
	private static final float SECONDS = 0.25f;

	private String current = "";
	private String previous = "";
	private long changedAt;
	private long setAt;

	/** Sets the new text. */
	public void set(String text) {
		if (!text.equals(current)) {
			previous = current;
			current = text;
			changedAt = previous.isEmpty() ? 0L : System.nanoTime();
		}
	}

	/** Sets the new text only if the last change is at least {@code millis} ago, so constantly changing numbers stay readable. */
	public void setEvery(String text, long millis) {
		long now = System.currentTimeMillis();
		if (current.isEmpty() || now - setAt >= millis) {
			setAt = now;
			set(text);
		}
	}

	public String text() {
		return current;
	}

	private float progress() {
		if (changedAt == 0L) {
			return 1f;
		}
		float p = Math.min(1f, (System.nanoTime() - changedAt) / 1_000_000_000f / SECONDS);
		return 1f - (1f - p) * (1f - p) * (1f - p);
	}

	/** Draws the text with its top-left corner at the position, at the given text scale. */
	public void draw(Canvas ui, float x, float y, float scale, int color) {
		float p = progress();
		if (p >= 1f) {
			ui.textAtScaled(current, x, y, scale, color);
			return;
		}
		int shift = current.length() - previous.length();
		float lift = 5f * scale;
		for (int i = 0; i < current.length(); i++) {
			String letter = current.substring(i, i + 1);
			float letterX = x + ui.widthScaled(current.substring(0, i), scale);
			int before = i - shift;
			boolean same = before >= 0 && before < previous.length() && previous.charAt(before) == current.charAt(i);
			if (same) {
				ui.textAtScaled(letter, letterX, y, scale, color);
				continue;
			}
			if (before >= 0 && before < previous.length()) {
				ui.textAtScaled(previous.substring(before, before + 1), letterX, y - p * lift, scale, Draw.withOpacity(color, 1f - p));
			}
			ui.textAtScaled(letter, letterX, y + (1f - p) * lift, scale, Draw.withOpacity(color, p));
		}
	}
}
