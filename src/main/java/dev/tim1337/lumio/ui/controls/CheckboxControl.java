package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

/** A checkbox with the setting's name next to it, taking the whole row. */
public class CheckboxControl extends Control {
	private static final int BOX = 14;

	private final BooleanSetting setting;
	private final Runnable onChanged;

	/** Box scale around 1: it is pressed in on click and springs back past its size, like a glass button. */
	private final Spring pop = Spring.bouncy(Spring.DEFAULT);

	public CheckboxControl(BooleanSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
		pop.snap(1f);
	}

	@Override
	protected int measure(Ui ui, int width) {
		return 22;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		boolean hovered = contains(mouseX, mouseY);
		int boxX = x;
		int boxY = y + (height - BOX) / 2;
		float size = pop.follow(ui, 1f);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(boxX + BOX / 2f, boxY + BOX / 2f);
		pose.scale(size, size);
		pose.translate(-(boxX + BOX / 2f), -(boxY + BOX / 2f));
		draw(ui, boxX, boxY, setting.get(), false);
		float h = hover(ui, 0, hovered);
		if (h > 0f) {
			ui.ring(boxX - 1, boxY - 1, BOX + 2, BOX + 2, Radius.XS + 1, 1, Ui.withOpacity(ui.accent, 0.7f * h));
			if (!setting.get()) {
				ui.rounded(boxX, boxY, BOX, BOX, Radius.XS, Ui.withOpacity(ui.palette.hover(), h));
			}
		}
		pose.popMatrix();
		ui.textMiddle(setting.label().getString(), boxX + BOX + 8, y + height / 2, ui.palette.text());
	}

	/** Draws just the box, so checkbox lists draw it the same way. */
	static void draw(Ui ui, int boxX, int boxY, boolean checked, boolean hovered) {
		if (checked) {
			ui.rounded(boxX, boxY, BOX, BOX, Radius.XS, ui.accent);
			// the check mark is an icon so its strokes stay smooth at every size
			ui.icon("tick", boxX + 2, boxY + 2, BOX - 4, 0xFFFFFFFF);
		} else {
			ui.rounded(boxX, boxY, BOX, BOX, Radius.XS, ui.palette.control());
			ui.ring(boxX, boxY, BOX, BOX, Radius.XS, 1, ui.palette.controlBorder());
			if (hovered) {
				ui.rounded(boxX, boxY, BOX, BOX, Radius.XS, ui.palette.hover());
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && contains(mouseX, mouseY)) {
			setting.set(!setting.get());
			pop.snap(0.82f);
			UiSounds.toggle(setting.get());
			onChanged.run();
			return true;
		}
		return false;
	}
}
