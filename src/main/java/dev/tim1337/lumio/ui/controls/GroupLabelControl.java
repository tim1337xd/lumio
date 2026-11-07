package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

import java.util.Locale;

/** The small title of a group of settings: in capitals, in the secondary text color. */
public class GroupLabelControl extends Control {
	private static final int HEIGHT = 12;

	private final String text;

	public GroupLabelControl(String text) {
		this.text = text.toUpperCase(Locale.ROOT);
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	/** It is its own name. */
	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return HEIGHT;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		ui.textMiddle(text, x, y + height / 2, ui.palette.textSecondary());
	}
}
