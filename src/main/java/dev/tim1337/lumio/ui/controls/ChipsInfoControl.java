package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;

import java.util.List;
import java.util.function.Supplier;

/** Chips in lines, as wide as there is room, that only show. */
public class ChipsInfoControl extends Control {
	private static final int LINE = 18;
	private static final int GAP = 4;

	private final Supplier<List<String>> chips;

	public ChipsInfoControl(Supplier<List<String>> chips) {
		this.chips = chips;
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
		int lines = 1;
		int used = 0;
		for (String chip : chips.get()) {
			int wide = ui.chipWidth(chip) + GAP;
			if (used + wide > width && used > 0) {
				lines++;
				used = 0;
			}
			used += wide;
		}
		return lines * LINE;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		int chipX = x;
		int middle = y + LINE / 2;
		for (String chip : chips.get()) {
			int wide = ui.chipWidth(chip) + GAP;
			if (chipX + wide > x + width && chipX > x) {
				chipX = x;
				middle += LINE;
			}
			ui.chip(chip, chipX, middle);
			chipX += wide;
		}
	}
}
