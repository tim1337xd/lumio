package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ui.Ui;

/**
 * The small pictures of the music page's tiles: a scene of the game (sky over grass) with the choice in it, drawn with shapes, so they follow the
 * accent color and need no images. Text is shown as rounded bars.
 */
final class MusicPictures {
	private static final int SKY_TOP = 0xFF2E4A73;
	private static final int SKY_BOTTOM = 0xFF6F92BC;
	private static final int GRASS_TOP = 0xFF4E7A38;
	private static final int GRASS_BOTTOM = 0xFF33521F;
	private static final int PANEL = 0xE614161C;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0x8CFFFFFF;
	private static final float HORIZON = 0.62f;
	private static final int BAR = 2;

	private MusicPictures() {
	}

	/** Draws the scene: the sky down to the horizon, the grass below it. */
	static void scene(Ui ui, int x, int y, int w, int h) {
		int horizon = Math.round(h * HORIZON);
		ui.roundedGradient(x, y, w, h, Radius.SM, SKY_TOP, SKY_BOTTOM);
		ui.roundedGradient(x, y + horizon, w, h - horizon, Radius.SM, GRASS_TOP, GRASS_BOTTOM);
		ui.fill(x, y + horizon, w, Math.round(Radius.SM), GRASS_TOP);
	}

	/** A text as a rounded bar. */
	private static void text(Ui ui, float x, float y, float w, int color) {
		ui.roundedSmooth(x, y, w, BAR, BAR / 2f, color);
	}

	// ------------------------------------------------------------------------------------------------- where the song shows

	static void infoBar(Ui ui, int x, int y, int w, int h) {
		scene(ui, x, y, w, h);
		float pw = w * 0.34f;
		float px = x + (w - pw) / 2f;
		float py = y + h * 0.1f;
		ui.roundedSmooth(px, py, pw, 7f, 3.5f, PANEL);
		float cx = px + 5f;
		for (int i = 0; i < 3; i++) {
			text(ui, cx, py + 2.5f, 4f, DIM);
			cx += 6f;
		}
		ui.circle(Math.round(cx + 1f), Math.round(py + 3.5f), 1, ui.accent);
		text(ui, cx + 4f, py + 2.5f, px + pw - cx - 8f, TEXT);
	}

	static void smallPlayer(Ui ui, int x, int y, int w, int h) {
		scene(ui, x, y, w, h);
		float cw = w * 0.2f;
		float cx = x + w - cw - 5f;
		float cy = y + h - 15f;
		ui.roundedSmooth(cx, cy, cw, 9f, 2.5f, PANEL);
		ui.roundedSmooth(cx + 2f, cy + 2f, 5f, 5f, 1.5f, ui.accent);
		text(ui, cx + 9f, cy + 2.5f, cw - 12f, TEXT);
		text(ui, cx + 9f, cy + 5.5f, (cw - 12f) * 0.6f, DIM);
	}

	static void window(Ui ui, int x, int y, int w, int h) {
		scene(ui, x, y, w, h);
		float cw = w * 0.24f;
		float ch = h * 0.66f;
		float cx = x + (w - cw) / 2f;
		float cy = y + (h - ch) / 2f;
		ui.roundedSmooth(cx, cy, cw, ch, 3f, PANEL);
		float cover = Math.min(cw - 8f, ch * 0.45f);
		ui.roundedSmooth(cx + (cw - cover) / 2f, cy + 4f, cover, cover, 2f, ui.accent);
		text(ui, cx + 5f, cy + ch - 9f, cw - 10f, TEXT);
	}

	// ------------------------------------------------------------------------------------------------- where the lyrics show

	static void barLyrics(Ui ui, int x, int y, int w, int h) {
		scene(ui, x, y, w, h);
		float pw = w * 0.34f;
		float px = x + (w - pw) / 2f;
		float py = y + h * 0.12f;
		ui.roundedSmooth(px, py, pw, 7f, 3.5f, PANEL);
		float cx = px + 5f;
		for (int i = 0; i < 3; i++) {
			text(ui, cx, py + 2.5f, 4f, DIM);
			cx += 6f;
		}
		float room = px + pw - cx - 4f;
		text(ui, cx, py + 2.5f, room * 0.55f, TEXT);
		text(ui, cx + room * 0.55f + 2f, py + 2.5f, room * 0.4f, DIM);
	}

	static void inWorld(Ui ui, int x, int y, int w, int h) {
		scene(ui, x, y, w, h);
		float lw = w * 0.36f;
		float lx = x + (w - lw) / 2f;
		float ly = y + h * 0.28f;
		ui.roundedSmooth(lx - 3f, ly - 2f, lw + 6f, 6f, 3f, Ui.withOpacity(0xFFFFFFFF, 0.12f));
		text(ui, lx, ly, lw * 0.55f, TEXT);
		text(ui, lx + lw * 0.55f + 2f, ly, lw * 0.45f - 2f, DIM);
	}
}
