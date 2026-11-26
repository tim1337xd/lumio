package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.FeatureManager;

import net.minecraft.util.Mth;

/**
 * The Snap menu of the HUD editor, under its toolbar button: what dragged elements snap to (the screen's center and the grid, the grid only, or
 * nothing), the grid's size, and whether the other elements are targets too. Shift while dragging turns snapping off for that drag.
 */
final class HudEditSnapMenu {
	private static final int WIDTH = 300;
	private static final int PAD = 10;
	private static final int LABEL = 16;
	private static final int GAP = 8;
	private static final int ROW = 24;
	private static final String HINT = "Shift while dragging: no snapping";

	private final HudEditScreen screen;
	private final Spring shown = Spring.smooth(Spring.SNAPPY);
	private final String modeLabel = ModFeatures.MENU.hudSnapSetting().labelText();
	private final String gridLabel = ModFeatures.MENU.hudGridSizeSetting().labelText();
	private final String othersLabel = ModFeatures.MENU.hudSnapElementsSetting().labelText();
	private final Control mode = ModFeatures.MENU.hudSnapSetting().createControl(FeatureManager::saveSoon);
	private final Control grid = ModFeatures.MENU.hudGridSizeSetting().createControl(FeatureManager::saveSoon);
	private final Control others = ModFeatures.MENU.hudSnapElementsSetting().createControl(FeatureManager::saveSoon);
	private boolean open;
	private int anchorX;
	private int anchorY;
	private int x;
	private int y;
	private int height;
	/** The control a press went to, which then gets the drag and the release. */
	private Control pressed;

	HudEditSnapMenu(HudEditScreen screen) {
		this.screen = screen;
	}

	boolean isOpen() {
		return open;
	}

	void toggle(int buttonX, int buttonBottom) {
		open = !open;
		anchorX = buttonX;
		anchorY = buttonBottom + 4;
		UiSounds.play(open ? UiSounds.Cue.OPEN : UiSounds.Cue.CLOSE);
	}

	void close() {
		open = false;
	}

	boolean contains(double mouseX, double mouseY) {
		return open && mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
	}

	void draw(Ui ui, int mouseX, int mouseY) {
		float amount = Mth.clamp(shown.follow(ui, open ? 1f : 0f), 0f, 1f);
		if (amount <= 0.01f) {
			return;
		}
		x = Mth.clamp(anchorX, 4, Math.max(4, screen.width - WIDTH - 4));
		y = anchorY;
		int inner = WIDTH - 2 * PAD;
		int modeY = y + PAD + LABEL;
		mode.place(ui, x + PAD, modeY, inner);
		int gridY = modeY + mode.height() + GAP + LABEL;
		grid.place(ui, x + PAD, gridY, inner);
		int othersY = gridY + grid.height() + GAP;
		int othersWidth = others.inlineWidth(ui, inner);
		others.place(ui, x + WIDTH - PAD - othersWidth, othersY + (ROW - others.height()) / 2, othersWidth);
		int hintY = othersY + ROW + GAP / 2;
		height = hintY + LABEL + PAD - y;
		HudEditScreen.surface(ui, x, y, WIDTH, height, Radius.LG, amount);
		Palette palette = ui.palette;
		ui.textMiddle(modeLabel, x + PAD, y + PAD + LABEL / 2 - 2, palette.textSecondary());
		mode.renderSafely(ui, mouseX, mouseY);
		ui.textMiddle(gridLabel, x + PAD, gridY - LABEL / 2 - 2, palette.textSecondary());
		grid.renderSafely(ui, mouseX, mouseY);
		ui.textMiddle(othersLabel, x + PAD, othersY + ROW / 2, palette.text());
		others.renderSafely(ui, mouseX, mouseY);
		ui.textMiddle(HINT, x + PAD, hintY + LABEL / 2, palette.textSecondary());
	}

	/** @return whether the click was on the menu */
	boolean click(double mouseX, double mouseY, int button) {
		for (Control control : new Control[]{mode, grid, others}) {
			if (control.mouseClicked(mouseX, mouseY, button)) {
				pressed = control;
				return true;
			}
		}
		return contains(mouseX, mouseY);
	}

	boolean drag(double mouseX, double mouseY) {
		return pressed != null && pressed.mouseDragged(mouseX, mouseY);
	}

	void release(double mouseX, double mouseY) {
		if (pressed != null) {
			pressed.mouseReleased(mouseX, mouseY);
			pressed = null;
		}
	}
}
