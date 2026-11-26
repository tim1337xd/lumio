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

	// ------------------------------------------------------------------------------------------------- the presets of the lyrics in the world

	private static final int NIGHT_TOP = 0xFF0B1230;
	private static final int NIGHT_BOTTOM = 0xFF27386B;
	private static final int LAND_TOP = 0xFF18263A;
	private static final int LAND_BOTTOM = 0xFF0C1420;
	private static final int STAR = 0xFFFFFFFF;
	/** The night scene of the presets' pictures: a dark sky over dark land. */
	private static void night(Ui ui, int x, int y, int w, int h) {
		int horizon = Math.round(h * 0.74f);
		ui.roundedGradient(x, y, w, h, Radius.SM, NIGHT_TOP, NIGHT_BOTTOM);
		ui.roundedGradient(x, y + horizon, w, h - horizon, Radius.SM, LAND_TOP, LAND_BOTTOM);
		ui.fill(x, y + horizon, w, Math.round(Radius.SM), LAND_TOP);
		// a few far stars
		for (int i = 0; i < 7; i++) {
			float sx = x + 4f + (w - 8f) * CutoutWords.hash(i * 7 + 1);
			float sy = y + 3f + (horizon - 8f) * CutoutWords.hash(i * 13 + 5);
			ui.roundedSmooth(sx, sy, 1f, 1f, 0.5f, Ui.withOpacity(STAR, 0.25f + 0.3f * CutoutWords.hash(i * 3 + 2)));
		}
	}

	static void presetClassic(Ui ui, int x, int y, int w, int h) {
		night(ui, x, y, w, h);
		float lw = w * 0.5f;
		float lx = x + (w - lw) / 2f;
		float ly = y + h * 0.32f;
		ui.roundedSmooth(lx - 3f, ly - 3f, lw * 0.6f + 5f, BAR + 6f, 4f, Ui.withOpacity(ui.accent, 0.3f));
		text(ui, lx, ly, lw * 0.58f, TEXT);
		text(ui, lx + lw * 0.58f + 2f, ly, lw * 0.42f - 2f, DIM);
	}

	/** The tones of the cut-out picture: a warm paper tone, as from a cover. */
	private static final int CUT_WORD = 0xFFE8B79A;
	private static final int CUT_DIM = 0x99C89A84;
	private static final int CUT_TILE = 0xFF7A4A3E;
	private static final int CUT_PALE = 0xFFF6E1D6;
	private static final int CUT_ON_PALE = 0xFF8A5446;

	static void presetCutout(Ui ui, int x, int y, int w, int h) {
		night(ui, x, y, w, h);
		float middle = x + w / 2f;
		float top = y + h * 0.1f;
		float unit = Math.max(3f, h * 0.085f);
		// an older word with a small one beside it, the word being sung on a tile, and the newest on a pale tile with its symbol
		ui.roundedSmooth(middle - unit * 1.6f, top, unit * 2.2f, unit * 1.5f, 1f, CUT_DIM);
		ui.roundedSmooth(middle + unit * 0.9f, top, unit * 1.2f, unit * 0.7f, 0.8f, CUT_DIM);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(middle, top + unit * 3.6f);
		ui.roundedSmooth(-unit * 3.3f, -unit * 1.6f, unit * 6.6f, unit * 3.2f, 0.8f, CUT_TILE);
		ui.roundedSmooth(-unit * 2.4f, -unit * 0.95f, unit * 4.8f, unit * 1.9f, 1f, CUT_WORD);
		pose.popMatrix();
		pose.pushMatrix();
		pose.translate(middle + unit * 0.8f, top + unit * 6.6f);
		ui.roundedSmooth(-unit * 2.3f, -unit * 1.05f, unit * 4.6f, unit * 2.1f, 0.8f, CUT_PALE);
		ui.roundedSmooth(-unit * 1.7f, -unit * 0.55f, unit * 3.4f, unit * 1.1f, 0.8f, CUT_ON_PALE);
		pose.popMatrix();
		ui.roundedSmooth(middle - unit * 3.2f, top + unit * 6.1f, unit * 1.2f, unit * 1.0f, unit * 0.3f, CUT_WORD);
	}
}
