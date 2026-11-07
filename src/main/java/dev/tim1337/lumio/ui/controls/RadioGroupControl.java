package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/** A vertical list of choices with radio buttons. */
public class RadioGroupControl<E extends Enum<E>> extends Control {
	private static final int ROW = 28;
	private static final int CIRCLE_RADIUS = 7;
	/** The dot of the chosen row, even like the circle so it sits in its middle. */
	private static final float DOT = 6f;

	private final EnumSetting<E> setting;
	private final Runnable onChanged;

	public RadioGroupControl(EnumSetting<E> setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return setting.choices().size() * ROW;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		List<E> choices = setting.choices();
		for (int i = 0; i < choices.size(); i++) {
			int rowY = y + i * ROW;
			int middle = rowY + ROW / 2;
			boolean selected = choices.get(i) == setting.get();
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW;

			float h = hover(ui, i, hovered);
			// every part from the same center, so the dot sits exactly in the middle of the ring
			float centerX = x + CIRCLE_RADIUS + 1;
			float diameter = CIRCLE_RADIUS * 2;
			ui.discAt(centerX, middle, diameter + 6, Ui.withOpacity(ui.accent, 0.2f * h));
			if (selected) {
				ui.discAt(centerX, middle, diameter, ui.accent);
				ui.discAt(centerX, middle, DOT, 0xFFFFFFFF);
			} else {
				ui.discAt(centerX, middle, diameter, ui.palette.control());
				ui.ringAt(centerX, middle, diameter, 1f, ui.palette.controlBorder());
				ui.discAt(centerX, middle, diameter, Ui.withOpacity(ui.palette.hover(), h));
			}
			ui.textMiddle(setting.nameOf(choices.get(i)).getString(), x + CIRCLE_RADIUS * 2 + 10, middle, ui.palette.text());
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		int index = (int) ((mouseY - y) / ROW);
		List<E> choices = setting.choices();
		if (index >= 0 && index < choices.size() && choices.get(index) != setting.get()) {
			UiSounds.tick((float) index / Math.max(1, choices.size() - 1));
			setting.choose(choices.get(index), onChanged);
		}
		return true;
	}
}
