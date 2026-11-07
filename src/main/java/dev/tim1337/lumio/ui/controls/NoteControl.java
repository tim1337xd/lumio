package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

import java.util.List;

/** A note across the row: an info icon and secondary text, wrapped to at most three lines instead of being cut. */
public class NoteControl extends Control {
	private static final int ICON = 9;
	private static final int ICON_GAP = 6;
	private static final int MOST_LINES = 3;
	private static final int LINE_HEIGHT = 11;
	private static final int PAD = 4;

	private final String text;
	/** The lines for the width they were wrapped to, wrapped again only when the width changes. */
	private List<String> lines = List.of();
	private int wrappedFor = -1;

	public NoteControl(String text) {
		this.text = text;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	protected int measure(Ui ui, int width) {
		if (width != wrappedFor) {
			wrappedFor = width;
			lines = ui.wrapScaled(text, width - ICON - ICON_GAP, TextScale.SMALL, MOST_LINES);
		}
		return 2 * PAD + Math.max(1, lines.size()) * LINE_HEIGHT;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		int color = ui.palette.textSecondary();
		ui.icon("info", x, y + PAD + 1, ICON, color);
		for (int i = 0; i < lines.size(); i++) {
			ui.textAtScaled(lines.get(i), x + ICON + ICON_GAP, y + PAD + i * LINE_HEIGHT + 1f, TextScale.SMALL, color);
		}
	}
}
