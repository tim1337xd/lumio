package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ui.MenuGear;
import dev.tim1337.lumio.ui.Ui;

/**
 * The pictures of the two main menu choices: the living world with a sketch of the mod's main menu over it (the song island, and the column of
 * the greeting, the title and the buttons), and a plain sketch of the game's own title screen with the mod's small settings button where the
 * real one is, before the row of small buttons. Both follow the mockup's proportions, scaled to the picture's width.
 */
final class MenuPictures {
	/** The width of the mockup's pictures: every size below is in its pixels, scaled to the picture's width. */
	private static final float MOCKUP_WIDTH = 422f;
	/** A picture's height to its width. */
	static final float ASPECT = 236f / 422f;
	static final float RADIUS = Radius.MD;
	private static final int SKETCH_DARK = 0xFF0E0A1E;
	private static final int SKY_TOP = 0xFF5D7FA8;
	private static final int SKY_HORIZON = 0xFF9BB4CF;
	private static final int GRASS_TOP = 0xFF6F8A5A;
	private static final int GRASS_BOTTOM = 0xFF4F6A3E;
	/** Where the grass starts, as a share of the picture's height. */
	private static final float HORIZON = 0.55f;
	private static final int BUTTON = 0xEB6E6E6E;
	private static final int BUTTON_EDGE = 0xE6141414;
	private static final int[] ROW_COLORS = {0xFF30D158, 0xFF0A84FF, 0xFFFF9F0A};
	private static final int[] ROW_LINES = {60, 54, 40};

	private MenuPictures() {
	}

	/** The living world, darker on the left where the main menu's column is, with a sketch of the island and the column. */
	static void livingWorld(SetupKit kit, int x, int y, int width, int height) {
		Ui ui = kit.ui;
		SetupPreview.world(kit, x, y, width, height, RADIUS);
		float s = width / MOCKUP_WIDTH;
		shadeLeft(ui, x, y, height, Math.round(width * 0.55f), Ui.withOpacity(SKETCH_DARK, 0.6f));

		int islandWidth = scaled(92, s);
		int islandHeight = scaled(16, s);
		int islandX = x + (width - islandWidth) / 2;
		int islandY = y + scaled(12, s);
		ui.rounded(islandX, islandY, islandWidth, islandHeight, islandHeight / 2f, Ui.withOpacity(SKETCH_DARK, 0.65f));
		ui.ring(islandX, islandY, islandWidth, islandHeight, islandHeight / 2f, 1, 0x2EFFFFFF);

		// the greeting, the title and the box of buttons
		int columnX = x + scaled(18, s);
		int top = y + scaled(58, s);
		ui.rounded(columnX, top, scaled(70, s), scaled(5, s), 3 * s, 0x99FFFFFF);
		top += scaled(11, s);
		ui.rounded(columnX, top, scaled(110, s), scaled(12, s), 4 * s, 0xFFFFFFFF);
		top += scaled(24, s);
		int boxWidth = scaled(130, s);
		int pad = scaled(5, s);
		int row = scaled(22, s);
		int rowGap = scaled(4, s);
		int boxHeight = 2 * pad + ROW_COLORS.length * row + (ROW_COLORS.length - 1) * rowGap;
		ui.rounded(columnX, top, boxWidth, boxHeight, 9 * s, Ui.withOpacity(SKETCH_DARK, 0.5f));
		ui.ring(columnX, top, boxWidth, boxHeight, 9 * s, 1, 0x29FFFFFF);
		int tile = scaled(14, s);
		int lineHeight = scaled(5, s);
		for (int i = 0; i < ROW_COLORS.length; i++) {
			int rowY = top + pad + i * (row + rowGap);
			if (i == 0) {
				ui.rounded(columnX + pad, rowY, boxWidth - 2 * pad, row, 6 * s, 0x1FFFFFFF);
			}
			int tileX = columnX + pad + scaled(4, s);
			ui.rounded(tileX, rowY + (row - tile) / 2, tile, tile, 4 * s, ROW_COLORS[i]);
			ui.rounded(tileX + tile + scaled(6, s), rowY + (row - lineHeight) / 2, scaled(ROW_LINES[i], s), lineHeight, lineHeight / 2f, 0xB3FFFFFF);
		}
	}

	/** The game's own title screen as a plain sketch: sky and grass, the logo, its buttons, and the mod's small settings button. */
	static void classic(SetupKit kit, int x, int y, int width, int height) {
		Ui ui = kit.ui;
		float s = width / MOCKUP_WIDTH;
		int r = Math.round(RADIUS);
		int horizon = y + Math.round(height * HORIZON);
		// the rounded corners come from rounded fills cut to the rows of the corners, the rest is two gradients
		ui.graphics.enableScissor(x, y, x + width, y + r);
		ui.rounded(x, y, width, 2 * r, r, SKY_TOP);
		ui.graphics.disableScissor();
		ui.verticalGradient(x, y + r, width, horizon - y - r, SKY_TOP, SKY_HORIZON);
		ui.verticalGradient(x, horizon, width, y + height - r - horizon, GRASS_TOP, GRASS_BOTTOM);
		ui.graphics.enableScissor(x, y + height - r, x + width, y + height);
		ui.rounded(x, y + height - 2 * r, width, 2 * r, r, GRASS_BOTTOM);
		ui.graphics.disableScissor();
		ui.rounded(x, y, width, height, r, 0x2E000000);
		ui.ring(x, y, width, height, r, 1, 0x1FFFFFFF);

		// the logo, three wide buttons and a row of two, centered, with the small buttons beside that row
		int logoWidth = scaled(150, s);
		int logoHeight = scaled(20, s);
		int logoGap = scaled(14, s);
		int gap = scaled(9, s);
		int buttonWidth = scaled(170, s);
		int buttonHeight = scaled(18, s);
		int half = scaled(81, s);
		int total = logoHeight + logoGap + 4 * (gap + buttonHeight);
		int middle = x + width / 2;
		int top = y + (height - total) / 2;
		ui.rounded(middle - logoWidth / 2, top, logoWidth, logoHeight, 3 * s, 0x8CFFFFFF);
		int left = middle - buttonWidth / 2;
		int buttonY = top + logoHeight + logoGap + gap;
		for (int i = 0; i < 3; i++) {
			button(ui, left, buttonY, buttonWidth, buttonHeight);
			buttonY += buttonHeight + gap;
		}
		button(ui, left, buttonY, half, buttonHeight);
		button(ui, left + buttonWidth - half, buttonY, half, buttonHeight);
		int small = buttonHeight;
		int smallGap = scaled(4, s);
		button(ui, left - smallGap - small, buttonY, small, small);
		button(ui, left + buttonWidth + smallGap, buttonY, small, small);
		MenuGear.drawModIcon(ui, left - 2 * (smallGap + small), buttonY, small, 0f);
	}

	/** A button of the game: grey with a dark edge and a light line at its top. */
	private static void button(Ui ui, int x, int y, int width, int height) {
		ui.rounded(x, y, width, height, Radius.XXS, BUTTON);
		ui.ring(x, y, width, height, Radius.XXS, 1, BUTTON_EDGE);
		ui.fill(x + 1, y + 1, width - 2, 1, 0x40FFFFFF);
	}

	/** Darkens the picture from its left edge to a reach, fading out; the left corners come from a rounded fill cut to their columns. */
	private static void shadeLeft(Ui ui, int x, int y, int height, int reach, int color) {
		int r = Math.round(RADIUS);
		ui.graphics.enableScissor(x, y, x + r, y + height);
		ui.rounded(x, y, 2 * r, height, r, color);
		ui.graphics.disableScissor();
		ui.horizontalGradient(x + r, y, reach - r, height, color, color & 0x00FFFFFF);
	}

	private static int scaled(int mockupPixels, float scale) {
		return Math.max(1, Math.round(mockupPixels * scale));
	}
}
