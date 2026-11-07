package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

/**
 * A color as a small swatch at the end of its row, or as a chip with a name in a row of several colors. A click opens the full color picker
 * in a popup attached to it, which stays open while the color is edited and closes on a click outside or Escape.
 */
public class ColorSwatchControl extends Control {
	private static final int SWATCH_WIDTH = 26;
	static final int HEIGHT = 18;
	/** Size of the color square inside a chip. */
	private static final int CHIP_SWATCH = 12;
	private static final int CHIP_PAD = 4;
	private static final int CHIP_TEXT_GAP = 6;
	private static final int POPUP_WIDTH = 210;
	private static final int POPUP_PAD = 16;

	private final ColorSetting setting;
	/** Name shown in the chip, or null for a plain swatch. */
	private final String name;
	private final ColorControl picker;
	private final PopupPlace place = new PopupPlace();
	private final Spring unfold = Spring.smooth(Spring.SNAPPY);
	private boolean open;
	private int popupX;

	public ColorSwatchControl(ColorSetting setting, String name, Runnable onChanged) {
		this.setting = setting;
		this.name = name;
		this.picker = setting.createPicker(onChanged);
	}

	public ColorSetting setting() {
		return setting;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return HEIGHT;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, naturalWidth(ui));
	}

	/** @return the width the swatch or chip needs */
	int naturalWidth(Ui ui) {
		return name == null ? SWATCH_WIDTH : CHIP_PAD + CHIP_SWATCH + CHIP_TEXT_GAP + ui.width(name) + CHIP_PAD + 2;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		float hover = hover(ui, 0, open || contains(mouseX, mouseY));
		int border = Ui.mix(palette.controlBorder(), ui.accent, open ? 1f : 0.5f * hover);
		if (name == null) {
			swatch(ui, x, y, width, height);
			ui.ring(x, y, width, height, Radius.XS, 1, border);
			return;
		}
		ui.rounded(x, y, width, height, Radius.SM, palette.control());
		ui.ring(x, y, width, height, Radius.SM, 1, border);
		int swatchY = y + (height - CHIP_SWATCH) / 2;
		swatch(ui, x + CHIP_PAD, swatchY, CHIP_SWATCH, CHIP_SWATCH);
		ui.ring(x + CHIP_PAD, swatchY, CHIP_SWATCH, CHIP_SWATCH, Radius.pill(CHIP_SWATCH), 1, Ui.withOpacity(0xFF000000, 0.25f));
		int textX = x + CHIP_PAD + CHIP_SWATCH + CHIP_TEXT_GAP;
		ui.textMiddle(ui.fit(name, x + width - CHIP_PAD - textX), textX, y + height / 2, palette.text());
	}

	/** Fills the color, over a light and a dark half when it is translucent, so its opacity shows. */
	private void swatch(Ui ui, int sx, int sy, int sw, int sh) {
		if (setting.hasOpacity() && setting.opacity() < 1f) {
			ui.rounded(sx, sy, sw, sh, Radius.pill(sh), 0xFFFFFFFF);
			ui.rounded(sx + sw / 2, sy, sw - sw / 2, sh, Radius.pill(sh), 0xFF2C2C2E);
		}
		ui.rounded(sx, sy, sw, sh, Radius.pill(sh), setting.hasOpacity() ? setting.argb() : 0xFF000000 | setting.get());
	}

	private int popupHeight() {
		return picker.height() + 2 * POPUP_PAD;
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		Palette palette = ui.palette;
		// the picker's height does not depend on its position, so it is measured first and then placed in the popup
		picker.place(ui, 0, 0, POPUP_WIDTH - 2 * POPUP_PAD);
		place.place(ui, y, height, popupHeight());
		popupX = x + width - POPUP_WIDTH;
		int popupY = place.top;
		int popupH = place.height;
		float grown = unfold.follow(ui, 1f);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		// it grows from the edge it is attached to, like a dropdown list
		float originY = popupY > y ? popupY : popupY + popupH;
		pose.translate(popupX + POPUP_WIDTH, originY);
		pose.scale(0.92f + 0.08f * grown, 0.6f + 0.4f * grown);
		pose.translate(-(popupX + POPUP_WIDTH), -originY);
		ui.shadow(popupX, popupY, POPUP_WIDTH, popupH, Radius.LG, 10, 0.5f * Math.min(1f, grown));
		ui.rounded(popupX, popupY, POPUP_WIDTH, popupH, Radius.LG, palette.popup());
		ui.ring(popupX, popupY, POPUP_WIDTH, popupH, Radius.LG, 1, palette.popupBorder());
		picker.place(ui, popupX + POPUP_PAD, popupY + POPUP_PAD, POPUP_WIDTH - 2 * POPUP_PAD);
		picker.render(ui, mouseX, mouseY);
		pose.popMatrix();
	}

	@Override
	public boolean isOverlayOpen() {
		return open;
	}

	private boolean inPopup(double mouseX, double mouseY) {
		return place.contains(mouseX, mouseY, popupX, POPUP_WIDTH);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (open) {
			if (inPopup(mouseX, mouseY)) {
				picker.mouseClicked(mouseX, mouseY, button);
				return true;
			}
			close();
			return contains(mouseX, mouseY); // clicking the swatch again closes the picker
		}
		if (button == 0 && contains(mouseX, mouseY)) {
			open = true;
			unfold.snap(0f);
			UiSounds.click();
			return true;
		}
		return false;
	}

	private void close() {
		open = false;
		picker.loseFocus();
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		return open && picker.mouseDragged(mouseX, mouseY);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		if (open) {
			picker.mouseReleased(mouseX, mouseY);
		}
	}

	@Override
	public boolean wantsKeyboard() {
		return open && picker.wantsKeyboard();
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		return open && picker.keyPressed(key, modifiers);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return open && picker.charTyped(codepoint);
	}

	@Override
	public void loseFocus() {
		close();
	}
}
