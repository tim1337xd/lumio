package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

/** Two controls side by side in one row: the first as wide as it needs, the second in the rest. */
public class PairControl extends Control {
	private static final int GAP = 10;
	/** The least the second control keeps when the row is narrow, or what it wants if that is less. */
	private static final int MIN_SECOND = 90;

	private final Control first;
	private final Control second;

	public PairControl(Control first, Control second) {
		this.first = first;
		this.second = second;
	}

	@Override
	protected int measure(Ui ui, int width) {
		int firstWidth = Math.min(first.inlineWidth(ui, width), Math.max(0, width - GAP - Math.min(MIN_SECOND, second.inlineWidth(ui, width))));
		int secondX = x + firstWidth + GAP;
		int secondWidth = Math.max(0, width - firstWidth - GAP);
		first.place(ui, x, y, firstWidth);
		second.place(ui, secondX, y, secondWidth);
		int height = Math.max(first.height(), second.height());
		// the shorter one sits in the middle of the row
		first.place(ui, x, y + (height - first.height()) / 2, firstWidth);
		second.place(ui, secondX, y + (height - second.height()) / 2, secondWidth);
		return height;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, first.inlineWidth(ui, available) + GAP + second.inlineWidth(ui, available));
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		first.render(ui, mouseX, mouseY);
		second.render(ui, mouseX, mouseY);
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (first.isOverlayOpen()) {
			first.renderOverlay(ui, mouseX, mouseY);
		}
		if (second.isOverlayOpen()) {
			second.renderOverlay(ui, mouseX, mouseY);
		}
	}

	@Override
	public boolean isOverlayOpen() {
		return first.isOverlayOpen() || second.isOverlayOpen();
	}

	@Override
	public void overlayScrolled(double amount) {
		(first.isOverlayOpen() ? first : second).overlayScrolled(amount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// an open list lies over the other control and gets the click first
		if (first.isOverlayOpen()) {
			return first.mouseClicked(mouseX, mouseY, button);
		}
		if (second.isOverlayOpen()) {
			return second.mouseClicked(mouseX, mouseY, button);
		}
		return first.mouseClicked(mouseX, mouseY, button) || second.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		return first.mouseDragged(mouseX, mouseY) || second.mouseDragged(mouseX, mouseY);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		first.mouseReleased(mouseX, mouseY);
		second.mouseReleased(mouseX, mouseY);
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		return first.keyPressed(key, modifiers) || second.keyPressed(key, modifiers);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return first.charTyped(codepoint) || second.charTyped(codepoint);
	}

	@Override
	public boolean wantsKeyboard() {
		return first.wantsKeyboard() || second.wantsKeyboard();
	}

	@Override
	public void loseFocus() {
		first.loseFocus();
		second.loseFocus();
	}
}
