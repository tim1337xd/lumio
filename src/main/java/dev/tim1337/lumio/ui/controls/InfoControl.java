package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

import java.util.List;
import java.util.function.Supplier;

/**
 * Read-only text to the right of a row's name. A text too long for the room beside the name goes under it and wraps onto as many lines as
 * it needs: a help text is never cut with "…".
 */
public final class InfoControl extends Control {
	private static final int LINE = 14;
	private static final int WRAPPED_LINE = 11;
	private static final int WRAPPED_PAD = 2;
	private static final int MOST_LINES = 12;

	private final Supplier<String> text;
	/** The lines of the text for the width they were wrapped to; wrapped again only when the text or the width changes. */
	private List<String> lines = List.of();
	private String wrappedText;
	private int wrappedFor = -1;

	/** Whether the row has no name: the text then stands alone across the row, from its left. */
	private final boolean alone;

	public InfoControl(Supplier<String> text) {
		this(text, false);
	}

	public InfoControl(Supplier<String> text, boolean alone) {
		this.text = text;
		this.alone = alone;
	}

	@Override
	public boolean isBlock() {
		return alone;
	}

	@Override
	public boolean hasLabel() {
		return !alone;
	}

	private List<String> lines(Ui ui, int width) {
		String now = text.get();
		if (width != wrappedFor || !now.equals(wrappedText)) {
			wrappedFor = width;
			wrappedText = now;
			lines = ui.wrap(now, Math.max(40, width), MOST_LINES);
		}
		return lines;
	}

	@Override
	protected int measure(Ui ui, int width) {
		int count = lines(ui, width).size();
		return count <= 1 ? LINE : count * WRAPPED_LINE + 2 * WRAPPED_PAD;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, ui.width(text.get()));
	}

	/** A text wider than the room beside the name keeps its width and so goes under the name, where it wraps. */
	@Override
	public boolean keepsWidth() {
		return true;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		List<String> shown = lines(ui, width);
		if (shown.size() <= 1 && !alone) {
			ui.textRight(shown.isEmpty() ? "" : shown.get(0), x + width, y + height / 2, ui.palette.textSecondary());
			return;
		}
		for (int i = 0; i < shown.size(); i++) {
			ui.textMiddle(shown.get(i), x, y + WRAPPED_PAD + i * WRAPPED_LINE + WRAPPED_LINE / 2, ui.palette.textSecondary());
		}
	}
}
