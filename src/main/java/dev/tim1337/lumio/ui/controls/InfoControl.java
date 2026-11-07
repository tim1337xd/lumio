package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

import java.util.function.Supplier;

/** Read-only text to the right of a row's name; a value too long for its room is cut and shown in full on hover. */
public class InfoControl extends Control {
	private final Supplier<String> text;

	public InfoControl(Supplier<String> text) {
		this.text = text;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return 14;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, ui.width(text.get()));
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		String full = text.get();
		String shown = ui.fit(full, width);
		ui.textRight(shown, x + width, y + height / 2, ui.palette.textSecondary());
		if (shown.length() != full.length() && contains(mouseX, mouseY)) {
			showFullText(full);
		}
	}
}
