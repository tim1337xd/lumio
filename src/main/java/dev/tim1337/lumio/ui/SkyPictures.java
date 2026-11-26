package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.config.setting.EnumSetting;

/** Small pictures of the sky's styles for the cards that choose one: drawn shapes, not the sky itself, so they cost nothing and never change. */
public final class SkyPictures {
	private static final int DAY_TOP = 0xFF5A8FD8;
	private static final int DAY_BOTTOM = 0xFFBFD9F2;
	private static final int NIGHT_TOP = 0xFF0B0E22;
	private static final int NIGHT_BOTTOM = 0xFF1E2A4A;
	private static final int STARS_TOP = 0xFF120A26;
	private static final int STARS_BOTTOM = 0xFF2C1E52;
	private static final int GROUND = 0xFF1A2418;
	private static final int CLOUD = 0xF2FFFFFF;
	private static final int CLOUD_SHADE = 0x5A9FB4CC;
	private static final int AURORA = 0xFF3CE0A0;
	private static final int AURORA_TOP = 0xFF9A6AFF;
	private static final int STAR = 0xFFFFFFFF;
	private static final int MOLTEN_TOP = 0xFF1A0F06;
	private static final int MOLTEN_BOTTOM = 0xFF3A2008;
	private static final int MOLTEN_LINE = 0xFFFFC46A;
	private static final int MOLTEN_GLOW = 0x55FF9A2E;
	/** The lines: their height and swing as a share of the sky (in percent), how many waves across (in tenths) and where they start. */
	private static final int[][] MOLTEN_LINES = {{30, 14, 14, 0}, {55, 18, 9, 2}, {72, 10, 22, 4}};
	/** Where the ground starts, as a share of the height. */
	private static final float HORIZON = 0.78f;
	private static final int[][] CLOUDS = {{8, 30, 34, 10}, {30, 20, 40, 12}, {58, 34, 30, 9}, {76, 18, 26, 8}, {18, 48, 26, 7}, {64, 52, 22, 7}};
	private static final int[][] STARS = {{10, 14}, {22, 40}, {35, 22}, {48, 10}, {57, 44}, {66, 26}, {78, 12}, {88, 38}, {14, 56}, {42, 60}, {72, 58}, {92, 20}};
	private static final int RAYS = 9;

	private SkyPictures() {
	}

	/** @return the picture of a style, for its card */
	public static EnumSetting.OptionPicture of(String style) {
		return switch (style) {
			case "CLOUDS" -> SkyPictures::clouds;
			case "AURORA" -> SkyPictures::aurora;
			case "MOLTEN" -> SkyPictures::molten;
			default -> SkyPictures::nightSky;
		};
	}

	private static int horizon(int y, int height) {
		return y + Math.round(height * HORIZON);
	}

	private static void ground(Ui ui, int x, int y, int width, int height) {
		int top = horizon(y, height);
		ui.fill(x, top, width, y + height - top, GROUND);
	}

	static void clouds(Ui ui, int x, int y, int width, int height) {
		ui.verticalGradient(x, y, width, height, DAY_TOP, DAY_BOTTOM);
		for (int[] cloud : CLOUDS) {
			int cx = x + width * cloud[0] / 100;
			int cy = y + height * cloud[1] / 100;
			int w = width * cloud[2] / 100;
			int h = Math.max(4, height * cloud[3] / 100);
			ui.rounded(cx, cy + h / 3, w, h, h / 2f, CLOUD_SHADE);
			ui.rounded(cx, cy, w, h, h / 2f, CLOUD);
		}
		ground(ui, x, y, width, height);
	}

	static void aurora(Ui ui, int x, int y, int width, int height) {
		ui.verticalGradient(x, y, width, height, NIGHT_TOP, NIGHT_BOTTOM);
		// upright rays, bright at their lower edge and turning violet as they fade upward
		int bottom = horizon(y, height) - 2;
		for (int i = 0; i < RAYS; i++) {
			int rx = x + width * (6 + i * 10) / 100;
			int rayTop = y + height * (14 + (i * 37) % 24) / 100;
			ui.verticalGradient(rx, rayTop, Math.max(2, width / 22), bottom - rayTop, Ui.withOpacity(AURORA_TOP, 0.25f), Ui.withOpacity(AURORA, 0.9f));
		}
		ui.fill(x, bottom - 2, width, 2, Ui.withOpacity(AURORA, 0.6f));
		ground(ui, x, y, width, height);
	}

	/** The Molten sky: a dark warm sky with a few glowing, winding lines, each a soft halo under a bright core. */
	static void molten(Ui ui, int x, int y, int width, int height) {
		ui.verticalGradient(x, y, width, height, MOLTEN_TOP, MOLTEN_BOTTOM);
		int sky = horizon(y, height) - y;
		for (int[] line : MOLTEN_LINES) {
			for (int i = 0; i < width; i++) {
				double t = i / (double) width * Math.PI * 2.0;
				int at = y + Math.round(sky * (line[0] + line[1] * (float) Math.sin(t * line[2] / 10.0 + line[3])) / 100f);
				ui.fill(x + i, at - 1, 1, 3, MOLTEN_GLOW);
				ui.fill(x + i, at, 1, 1, MOLTEN_LINE);
			}
		}
		ground(ui, x, y, width, height);
	}

	static void nightSky(Ui ui, int x, int y, int width, int height) {
		ui.verticalGradient(x, y, width, height, STARS_TOP, STARS_BOTTOM);
		for (int[] star : STARS) {
			ui.fill(x + width * star[0] / 100, y + height * star[1] / 100, 1, 1, STAR);
		}
		// a shooting star with its fading tail
		int headX = x + width * 70 / 100;
		int headY = y + height * 34 / 100;
		for (int i = 0; i < 12; i++) {
			ui.fill(headX - i * 2, headY - i, 2, 1, Ui.withOpacity(STAR, 1f - i / 12f));
		}
		ground(ui, x, y, width, height);
	}

	static void auto(Ui ui, int x, int y, int width, int height) {
		// the day's clouds and the night's aurora, side by side
		int half = width / 2;
		ui.graphics.enableScissor(x, y, x + half, y + height);
		clouds(ui, x, y, width, height);
		ui.graphics.disableScissor();
		ui.graphics.enableScissor(x + half, y, x + width, y + height);
		aurora(ui, x, y, width, height);
		ui.graphics.disableScissor();
	}
}
