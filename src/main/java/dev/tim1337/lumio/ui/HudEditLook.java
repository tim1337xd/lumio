package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.FeatureManager;

import net.minecraft.util.Mth;

/**
 * The Look menu of the HUD editor, under its toolbar button: the theme of the whole HUD and its depth effect. An element's own choice in its card
 * goes before them. Changes show on the elements right away.
 */
final class HudEditLook {
	private static final int WIDTH = 300;
	private static final int PAD = 10;
	private static final int LABEL = 16;
	private static final int GAP = 8;
	private static final int ROW = 24;

	private final HudEditScreen screen;
	private final Spring shown = Spring.smooth(Spring.SNAPPY);
	private final String themeLabel = ModFeatures.MENU.hudThemeSetting().labelText();
	private final String depthLabel = ModFeatures.MENU.hudDepthSetting().labelText();
	private final Control theme = ModFeatures.MENU.hudThemeSetting().createControl(FeatureManager::saveSoon);
	private final Control depth = ModFeatures.MENU.hudDepthSetting().createControl(FeatureManager::saveSoon);
	private boolean open;
	private int anchorX;
	private int anchorY;
	private int x;
	private int y;
	private int height;
	/** The control a press went to, which then gets the drag and the release. */
	private Control pressed;

	HudEditLook(HudEditScreen screen) {
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
		theme.loseFocus();
	}

	boolean contains(double mouseX, double mouseY) {
		return open && (mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height || theme.isOverlayOpen());
	}

	void draw(Ui ui, int mouseX, int mouseY) {
		float amount = Mth.clamp(shown.follow(ui, open ? 1f : 0f), 0f, 1f);
		if (amount <= 0.01f) {
			return;
		}
		x = Mth.clamp(anchorX, 4, Math.max(4, screen.width - WIDTH - 4));
		y = anchorY;
		int inner = WIDTH - 2 * PAD;
		int themeY = y + PAD + LABEL;
		theme.place(ui, x + PAD, themeY, inner);
		int depthY = themeY + theme.height() + GAP;
		int depthWidth = depth.inlineWidth(ui, inner);
		depth.place(ui, x + WIDTH - PAD - depthWidth, depthY + (ROW - depth.height()) / 2, depthWidth);
		height = depthY + ROW + PAD - y;
		HudEditScreen.surface(ui, x, y, WIDTH, height, Radius.LG, amount);
		Palette palette = ui.palette;
		ui.textMiddle(themeLabel, x + PAD, y + PAD + LABEL / 2 - 2, palette.textSecondary());
		theme.renderSafely(ui, mouseX, mouseY);
		ui.textMiddle(depthLabel, x + PAD, depthY + ROW / 2, palette.text());
		depth.renderSafely(ui, mouseX, mouseY);
		if (theme.isOverlayOpen()) {
			theme.renderOverlay(ui, mouseX, mouseY);
		}
	}

	/** @return whether the click was on the menu */
	boolean click(double mouseX, double mouseY, int button) {
		if (theme.isOverlayOpen() || theme.mouseClicked(mouseX, mouseY, button)) {
			if (theme.isOverlayOpen()) {
				theme.mouseClicked(mouseX, mouseY, button);
			}
			pressed = theme;
			return true;
		}
		if (depth.mouseClicked(mouseX, mouseY, button)) {
			pressed = depth;
			return true;
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
