package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;


import com.mojang.blaze3d.platform.InputConstants;

/**
 * A key as a button showing the key's name. Clicking it listens for the next key or mouse button, which becomes the new key; Escape removes the
 * key, a left click anywhere else cancels. A conflict colors the name: red when another key of the mod would act too, amber when the game or
 * another mod uses the key.
 */
public class KeyControl extends Control {
	private static final int MIN_WIDTH = 64;
	private static final int PAD = 16;
	private static final String LISTENING = "Press a key";
	private static final String UNBOUND = "Not bound";
	/** Pulses per second of the ring while listening. */
	private static final double PULSE_RATE = 2.5;

	private final KeySetting setting;
	private final Runnable onChanged;
	private boolean listening;
	/** The key the name was taken from: the name is only built again when the key changes. */
	private InputConstants.Key shownKey;
	private String keyName = "";

	public KeyControl(KeySetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return 22;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, Math.max(MIN_WIDTH, Math.max(ui.width(name()), ui.width(LISTENING)) + 2 * PAD));
	}

	/** @return the name of the bound key, or that there is none */
	private String name() {
		InputConstants.Key key = setting.boundKey();
		if (key != shownKey) {
			shownKey = key;
			keyName = InputConstants.UNKNOWN.equals(key) ? UNBOUND : key.getDisplayName().getString();
		}
		return keyName;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		float hover = hover(ui, 0, contains(mouseX, mouseY));
		ui.rounded(x, y, width, height, Radius.SM, palette.control());
		if (listening) {
			float pulse = 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1_000_000_000.0 * Math.PI * 2.0 * PULSE_RATE);
			ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(ui.accent, 0.18f));
			ui.ring(x, y, width, height, Radius.SM, 1, Ui.withOpacity(ui.accent, pulse));
			ui.textCentered(LISTENING, x + width / 2, y + height / 2, palette.text());
			return;
		}
		int border = switch (setting.conflict()) {
			case OWN -> SystemColors.RED;
			case OTHER -> SystemColors.ORANGE;
			case NONE -> Ui.mix(palette.controlBorder(), ui.accent, 0.6f * hover);
		};
		ui.ring(x, y, width, height, Radius.SM, 1, border);
		if (hover > 0f) {
			ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(palette.hover(), hover));
		}
		String name = name();
		int color = switch (setting.conflict()) {
			case OWN -> 0xFF000000 | SystemColors.RED_TEXT_RGB;
			case OTHER -> SystemColors.ORANGE;
			case NONE -> InputConstants.UNKNOWN.equals(setting.boundKey()) ? palette.textSecondary() : palette.text();
		};
		ui.textCentered(ui.fit(name, width - 8), x + width / 2, y + height / 2, color);
	}

	public boolean listening() {
		return listening;
	}

	@Override
	public boolean wantsKeyboard() {
		return listening;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (listening) {
			if (button == ScreenInput.LEFT) {
				listening = false; // a left click cancels; left click is the game's attack and mining button
				return contains(mouseX, mouseY);
			}
			bind(InputConstants.Type.MOUSE.getOrCreate(ScreenInput.gameButton(button)));
			return true;
		}
		if (button == ScreenInput.LEFT && contains(mouseX, mouseY)) {
			listening = true;
			UiSounds.click();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!listening) {
			return false;
		}
		bind(key == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(key));
		return true;
	}

	private void bind(InputConstants.Key key) {
		listening = false;
		setting.bind(key);
		UiSounds.play(InputConstants.UNKNOWN.equals(key) ? UiSounds.Cue.OFF : UiSounds.Cue.ON);
		onChanged.run();
	}

	@Override
	public void loseFocus() {
		listening = false;
	}
}
