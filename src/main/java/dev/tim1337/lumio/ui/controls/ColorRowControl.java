package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

import java.util.List;

/**
 * Several colors of one group in one row, each as a chip with its swatch and name; a click on a chip opens its picker. A few chips sit to the
 * right of the row's name, more of them wrap onto lines below it.
 */
public class ColorRowControl extends Control {
	/** Up to this many chips sit beside the row's name; more make the row a block with the chips on lines of their own. */
	private static final int INLINE_CHIPS = 3;
	private static final int GAP = 6;
	private static final int LINE_GAP = 6;

	private final List<ColorSwatchControl> chips;

	public ColorRowControl(List<ColorSwatchControl> chips) {
		this.chips = chips;
	}

	@Override
	public boolean isBlock() {
		return chips.size() > INLINE_CHIPS;
	}

	@Override
	public boolean keepsWidth() {
		return true;
	}

	@Override
	public boolean leftOnOwnLine() {
		return true;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		int total = -GAP;
		for (ColorSwatchControl chip : chips) {
			total += chip.naturalWidth(ui) + GAP;
		}
		return Math.min(available, Math.max(total, 1));
	}

	/** Places the chips from the left (a block) or ending at the right edge (inline), wrapping onto new lines where the width runs out. */
	@Override
	protected int measure(Ui ui, int width) {
		int lineX = x;
		int lineY = y;
		if (!isBlock()) {
			lineX = Math.max(x, x + width - inlineWidth(ui, Integer.MAX_VALUE));
		}
		for (ColorSwatchControl chip : chips) {
			int chipWidth = Math.min(chip.naturalWidth(ui), width);
			if (lineX + chipWidth > x + width && lineX > x) {
				lineX = x;
				lineY += ColorSwatchControl.HEIGHT + LINE_GAP;
			}
			chip.place(ui, lineX, lineY, chipWidth);
			lineX += chipWidth + GAP;
		}
		return lineY - y + ColorSwatchControl.HEIGHT;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		for (ColorSwatchControl chip : chips) {
			chip.render(ui, mouseX, mouseY);
		}
	}

	/** @return the chip whose picker is open, or null */
	private ColorSwatchControl openChip() {
		for (ColorSwatchControl chip : chips) {
			if (chip.isOverlayOpen()) {
				return chip;
			}
		}
		return null;
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		ColorSwatchControl chip = openChip();
		if (chip != null) {
			chip.renderOverlay(ui, mouseX, mouseY);
		}
	}

	@Override
	public boolean isOverlayOpen() {
		return openChip() != null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		ColorSwatchControl open = openChip();
		if (open != null) {
			boolean handled = open.mouseClicked(mouseX, mouseY, button);
			// a click on another chip closes this picker and opens that one
			if (!handled) {
				for (ColorSwatchControl chip : chips) {
					if (chip != open && chip.contains(mouseX, mouseY)) {
						return chip.mouseClicked(mouseX, mouseY, button);
					}
				}
			}
			return handled;
		}
		for (ColorSwatchControl chip : chips) {
			if (chip.contains(mouseX, mouseY)) {
				return chip.mouseClicked(mouseX, mouseY, button);
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		ColorSwatchControl open = openChip();
		return open != null && open.mouseDragged(mouseX, mouseY);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		ColorSwatchControl open = openChip();
		if (open != null) {
			open.mouseReleased(mouseX, mouseY);
		}
	}

	@Override
	public boolean wantsKeyboard() {
		ColorSwatchControl open = openChip();
		return open != null && open.wantsKeyboard();
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		ColorSwatchControl open = openChip();
		return open != null && open.keyPressed(key, modifiers);
	}

	@Override
	public boolean charTyped(int codepoint) {
		ColorSwatchControl open = openChip();
		return open != null && open.charTyped(codepoint);
	}

	@Override
	public void loseFocus() {
		for (ColorSwatchControl chip : chips) {
			chip.loseFocus();
		}
	}
}
