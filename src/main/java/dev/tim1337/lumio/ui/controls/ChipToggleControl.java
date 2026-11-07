package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/** The choices of a {@link MultiSelectSetting} as pills in rows: a chosen one is filled in the accent, a click switches it. */
public class ChipToggleControl extends Control {
	private static final int CHIP_HEIGHT = 20;
	private static final int GAP = 6;
	private static final int PAD = 10;

	private final MultiSelectSetting setting;
	private final Runnable onChanged;
	/** Pill places from the last layout: x, y and width, relative to the control. */
	private final int[][] places;

	public ChipToggleControl(MultiSelectSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
		this.places = new int[setting.options().size()][3];
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	protected int measure(Ui ui, int width) {
		List<BooleanSetting> options = setting.options();
		int cx = 0;
		int cy = 0;
		for (int i = 0; i < options.size(); i++) {
			int w = Math.min(width, ui.width(options.get(i).labelText()) + 2 * PAD);
			if (cx > 0 && cx + w > width) {
				cx = 0;
				cy += CHIP_HEIGHT + GAP;
			}
			places[i][0] = cx;
			places[i][1] = cy;
			places[i][2] = w;
			cx += w + GAP;
		}
		return options.isEmpty() ? 0 : cy + CHIP_HEIGHT;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		List<BooleanSetting> options = setting.options();
		for (int i = 0; i < options.size(); i++) {
			BooleanSetting option = options.get(i);
			int cx = x + places[i][0];
			int cy = y + places[i][1];
			int w = places[i][2];
			boolean over = mouseX >= cx && mouseX < cx + w && mouseY >= cy && mouseY < cy + CHIP_HEIGHT;
			float h = hover(ui, i, over);
			boolean on = option.get();
			int fill = on ? Ui.mix(ui.accent, 0xFFFFFFFF, 0.08f * h) : Ui.mix(ui.palette.control(), 0xFFFFFFFF, 0.05f * h);
			ui.rounded(cx, cy, w, CHIP_HEIGHT, CHIP_HEIGHT / 2f, fill);
			if (!on) {
				ui.ring(cx, cy, w, CHIP_HEIGHT, CHIP_HEIGHT / 2f, 1, Ui.withOpacity(ui.palette.textSecondary(), 0.3f));
			}
			ui.textCentered(option.labelText(), cx + w / 2, cy + CHIP_HEIGHT / 2, on ? 0xFFFFFFFF : ui.palette.textSecondary());
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		List<BooleanSetting> options = setting.options();
		for (int i = 0; i < options.size(); i++) {
			int cx = x + places[i][0];
			int cy = y + places[i][1];
			if (mouseX >= cx && mouseX < cx + places[i][2] && mouseY >= cy && mouseY < cy + CHIP_HEIGHT) {
				BooleanSetting option = options.get(i);
				option.set(!option.get());
				UiSounds.toggle(option.get());
				onChanged.run();
				return true;
			}
		}
		return true;
	}
}
