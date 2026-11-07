package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.ui.Ui;

import java.util.List;

/** The list of the tabs that did not fit, under the More button of the chat's bar. In the tabs' own space; only draws and finds what is under the mouse. */
final class ChatMoreMenu {
	private static final int ROW_HEIGHT = 13;
	private static final int PAD = 3;
	private static final int ROW_TEXT_PAD = 6;
	private static final float RADIUS = 5f;
	private static final float ROW_RADIUS = 3.5f;
	private static final int GAP_UNDER_BAR = 3;
	private static final int FILL = 0xF8141418;
	private static final int RING = 0x33FFFFFF;
	private static final int ROW_HOVER = 0x1FFFFFFF;
	private static final float TEXT_HEIGHT_SHARE = 4.5f;
	private static final float IDLE_TEXT = 0.8f;

	private boolean open;

	boolean isOpen() {
		return open;
	}

	void toggle() {
		open = !open;
	}

	void close() {
		open = false;
	}

	/** @return how wide the list is for its labels, at least as wide as its button */
	static int width(List<Integer> labelWidths, int button) {
		int width = button;
		for (int label : labelWidths) {
			width = Math.max(width, label + 2 * ROW_TEXT_PAD + 2 * PAD);
		}
		return width;
	}

	static int height(int rows) {
		return rows * ROW_HEIGHT + 2 * PAD;
	}

	static int top(int barTop, int barHeight) {
		return barTop + barHeight + GAP_UNDER_BAR;
	}

	/** @return which row is at a place, or -1 */
	static int rowAt(double mouseX, double mouseY, int left, int top, int width, int rows) {
		if (mouseX < left || mouseX >= left + width || mouseY < top + PAD || mouseY >= top + PAD + rows * ROW_HEIGHT) {
			return -1;
		}
		return (int) ((mouseY - top - PAD) / ROW_HEIGHT);
	}

	static void draw(Ui ui, List<String> labels, int left, int top, int width, float textScale, int mouseX, int mouseY) {
		int height = height(labels.size());
		ui.rounded(left, top, width, height, RADIUS, FILL);
		ui.ring(left, top, width, height, RADIUS, 1, RING);
		int under = rowAt(mouseX, mouseY, left, top, width, labels.size());
		for (int i = 0; i < labels.size(); i++) {
			int rowTop = top + PAD + i * ROW_HEIGHT;
			if (i == under) {
				ui.rounded(left + PAD, rowTop, width - 2 * PAD, ROW_HEIGHT, ROW_RADIUS, ROW_HOVER);
			}
			ui.textAtScaled(labels.get(i), left + PAD + ROW_TEXT_PAD, rowTop + ROW_HEIGHT / 2f - TEXT_HEIGHT_SHARE * textScale, textScale, Ui.withOpacity(0xFFFFFFFF, i == under ? 1f : IDLE_TEXT));
		}
	}
}
