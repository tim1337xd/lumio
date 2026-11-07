package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

/** A small switch with its name before it, for sharing a row with something else. A click on the name or the switch turns it over. */
public class SmallSwitchControl extends Control {
	private static final float SCALE = 0.7f;
	private static final int TRACK_WIDTH = Math.round(SwitchControl.TRACK_WIDTH * SCALE);
	private static final int TRACK_HEIGHT = Math.round(SwitchControl.TRACK_HEIGHT * SCALE);
	private static final int GAP = 6;

	private final BooleanSetting setting;
	private final Runnable onChanged;
	private final Spring position = Spring.bouncy(Spring.DEFAULT);

	public SmallSwitchControl(BooleanSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return TRACK_HEIGHT;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, Math.round(ui.widthScaled(setting.labelText(), TextScale.SMALL)) + GAP + TRACK_WIDTH);
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		float hover = hover(ui, 0, contains(mouseX, mouseY));
		int trackX = x + width - TRACK_WIDTH;
		ui.textAtScaled(ui.fitScaled(setting.labelText(), trackX - GAP - x, TextScale.SMALL), x, y + (TRACK_HEIGHT - TextScale.SMALL * 8f) / 2f, TextScale.SMALL,
			Ui.mix(ui.palette.textSecondary(), ui.palette.text(), hover));
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(trackX, y);
		pose.scale(SCALE, SCALE);
		SwitchControl.draw(ui, 0, 0, position.follow(ui, setting.get() ? 1f : 0f), hover, 0f);
		pose.popMatrix();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		setting.set(!setting.get());
		UiSounds.toggle(setting.get());
		onChanged.run();
		return true;
	}
}
