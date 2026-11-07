package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.util.Mth;

/** The small signs of the info bar's values: the signal bars of the ping, the status dot of the server speed, the frame rate line, the pulse. */
final class InfoBarSigns {
	// Sizes in GUI pixels at size 1, from the mockups at about 0.63 of their pixels.
	static final float LINE_WIDTH = 24f;
	static final float LINE_GAP = 4f;
	private static final float LINE_HEIGHT = 9f;
	private static final float LINE_THICKNESS = 1f;
	private static final float SIGNAL_WIDTH = 2f;
	private static final float SIGNAL_GAP = 1f;
	private static final float[] SIGNAL_HEIGHTS = {3f, 4.5f, 6f, 7.5f};
	static final float SIGNALS_WIDTH = SIGNAL_HEIGHTS.length * SIGNAL_WIDTH + (SIGNAL_HEIGHTS.length - 1) * SIGNAL_GAP;
	static final float DOT = 4f;
	private static final float DOT_GLOW = 6f;
	private static final float UNLIT_ALPHA = 0.22f;

	private InfoBarSigns() {
	}

	/** Draws the signal bars: all lit while the ping is fine, fewer and colored when it is high. */
	static void signal(Ui ui, float x, float middle, int level, int color, float alpha) {
		int lit = level == InfoBarValues.FINE ? SIGNAL_HEIGHTS.length : level == InfoBarValues.WARN ? 2 : 1;
		int unlit = Ui.withOpacity(HudStyle.text(), UNLIT_ALPHA);
		float bottom = middle + SIGNAL_HEIGHTS[SIGNAL_HEIGHTS.length - 1] / 2f;
		for (int b = 0; b < SIGNAL_HEIGHTS.length; b++) {
			float barHeight = SIGNAL_HEIGHTS[b];
			ui.roundedSmooth(x + b * (SIGNAL_WIDTH + SIGNAL_GAP), bottom - barHeight, SIGNAL_WIDTH, barHeight, 0.6f,
				Ui.withOpacity(b < lit ? color : unlit, alpha));
		}
	}

	/** Draws the status dot of the server speed, with a soft glow in its color. */
	static void dot(Ui ui, float centerX, float middle, int color, float alpha) {
		ui.softLight(centerX, middle, DOT_GLOW, Ui.withOpacity(color, 0.45f * alpha));
		ui.roundedSmooth(centerX - DOT / 2f, middle - DOT / 2f, DOT, DOT, DOT / 2f, Ui.withOpacity(color, alpha));
	}

	/**
	 * Draws the frame rate of the last ten seconds as a line, scrolling smoothly: its points move left as time passes, the newest one follows the
	 * frame rate at the right end, and the oldest leaves at the left.
	 *
	 * @param top the frame rate at the top of the line
	 */
	static void fpsLine(Ui ui, InfoBarValues values, float x, float middle, float top, int color) {
		int count = values.historyCount;
		float step = LINE_WIDTH / (InfoBarValues.HISTORY - 1);
		float shift = values.sinceHistoryStep() / InfoBarValues.HISTORY_STEP_SECONDS * step;
		float bottom = middle + LINE_HEIGHT / 2f;
		float prevX = x + LINE_WIDTH;
		float prevY = lineY(values.fpsNow(), bottom, top);
		for (int k = count - 1; k >= 0; k--) {
			float px = x + LINE_WIDTH - (count - 1 - k) * step - shift;
			float py = lineY(values.historyAt(k), bottom, top);
			if (px < x) {
				// cut where the line leaves its box on the left
				float t = (prevX - x) / Math.max(0.001f, prevX - px);
				segment(ui, prevX, prevY, x, prevY + (py - prevY) * t, color);
				break;
			}
			segment(ui, prevX, prevY, px, py, color);
			prevX = px;
			prevY = py;
		}
	}

	private static float lineY(float fps, float bottom, float top) {
		return bottom - Mth.clamp(fps / Math.max(1f, top), 0f, 1f) * LINE_HEIGHT;
	}

	/** Draws a straight piece of a line, as a thin rotated capsule so it stays smooth at any angle. */
	private static void segment(Ui ui, float x0, float y0, float x1, float y1, int color) {
		float length = (float) Math.hypot(x1 - x0, y1 - y0);
		if (length < 0.05f) {
			return;
		}
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.rotate((float) Math.atan2(y1 - y0, x1 - x0));
		ui.roundedSmooth(-LINE_THICKNESS / 2f, -LINE_THICKNESS / 2f, length + LINE_THICKNESS, LINE_THICKNESS, LINE_THICKNESS / 2f, color);
		pose.popMatrix();
	}

	/** Draws the soft orange swell around a panel when the server speed dropped. @param progress how far the swell is, 1 when over */
	static void pulse(Ui ui, float x, float y, float w, float h, float r, float progress) {
		if (progress >= 1f) {
			return;
		}
		float swell = (float) Math.sin(Math.PI * progress);
		swell *= swell;
		int color = SystemColors.ORANGE;
		ui.ring(Math.round(x) - 1, Math.round(y) - 1, Math.round(w) + 2, Math.round(h) + 2, r + 1f, 1, Ui.withOpacity(color, 0.55f * swell));
		ui.wash(x + w / 2f, y + h / 2f, w / 2f + 12f, h / 2f + 10f, Ui.withOpacity(color, 0.3f * swell));
	}
}
