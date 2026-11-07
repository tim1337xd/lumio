package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.function.BooleanSupplier;

/** The location of a search result, right of its name, with an arrow; clicking opens it. Results that can be toggled get a switch at the end. */
public class SearchHitControl extends Control {
	private final String path;
	private final Runnable open;
	private final BooleanSupplier state;
	private final Runnable flip;
	private final Runnable onChanged;
	private final Spring position = Spring.bouncy(Spring.DEFAULT);
	/** Press progress on the switch, from 0 to 1. */
	private final Spring press = Spring.smooth(Spring.SNAPPY);
	private boolean pressed;
	private boolean known;

	public SearchHitControl(String path, Runnable open, BooleanSupplier state, Runnable flip, Runnable onChanged) {
		this.path = path;
		this.open = open;
		this.state = state;
		this.flip = flip;
		this.onChanged = onChanged;
	}

	private int switchX() {
		return x + width - SwitchControl.TRACK_WIDTH;
	}

	private boolean overSwitch(double mouseX, double mouseY) {
		return state != null && mouseX >= switchX() - 4 && mouseX < x + width + 2 && mouseY >= y && mouseY < y + height;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return state != null ? 26 : 22;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return available;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		float h = hover(ui, 0, contains(mouseX, mouseY) && !overSwitch(mouseX, mouseY));
		int middle = y + height / 2;
		int end = x + width;
		if (state != null) {
			boolean on = state.getAsBoolean();
			if (!known) {
				position.snap(on ? 1f : 0f);
				known = true;
			}
			float p = position.follow(ui, on ? 1f : 0f);
			float grip = press.follow(ui, pressed ? 1f : 0f);
			float switchHover = hover(ui, 1, overSwitch(mouseX, mouseY));
			SwitchControl.draw(ui, switchX(), middle - SwitchControl.TRACK_HEIGHT / 2, p, switchHover, grip);
			end = switchX() - 10;
		}
		ui.icon("chevron", end - 12, middle - 5, 10, Ui.mix(ui.palette.textSecondary(), ui.accent, h));
		ui.textRight(ui.fit(path, Math.max(40, end - x - 30)), end - 20, middle, Ui.mix(ui.palette.textSecondary(), ui.palette.text(), h));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		if (overSwitch(mouseX, mouseY)) {
			flip.run();
			pressed = true;
			UiSounds.toggle(state.getAsBoolean());
			onChanged.run();
			return true;
		}
		UiSounds.click();
		open.run();
		return true;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		pressed = false;
	}
}
