package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.features.misc.keybinds.KeyCapture;
import dev.tim1337.lumio.features.misc.keybinds.KeyCombo;
import dev.tim1337.lumio.features.misc.keybinds.KeyCombos;

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
 * A key as a button showing its key combination ("Tab + R"). Clicking it listens: the keys and buttons held, let go, become the new combination (up to
 * three; the last one pressed is the trigger, a wheel turn ends it). Esc, Backspace or Delete first removes the key (it shows "None"), a left click cancels; a right-click on the button removes it at any time. A conflict colors the name: red when another key of the mod would act too, amber when the game or
 * another mod uses the key.
 */
public final class KeyControl extends Control {
	private static final int MIN_WIDTH = 64;
	private static final int PAD = 16;
	/** All the field says while it waits for a key; what Esc and a click do then is the small grey line under the field (see KeySetting). */
	public static final String LISTENING = "Press now";
	private static final String UNBOUND = "None";
	/** Pulses per second of the ring while listening. */
	private static final double PULSE_RATE = 2.5;

	private final KeySetting setting;
	private final Runnable onChanged;
	private final ComboRecorder recorder = new ComboRecorder();
	/** The combination the name was taken from: the name is only built again when it changes. */
	private KeyCombo shownCombo;
	private int shownVersion = -1;
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
	public int standardNeed(Ui ui) {
		return inlineWidth(ui, Integer.MAX_VALUE);
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, Math.max(MIN_WIDTH, Math.max(ui.width(name()), ui.width(LISTENING)) + 2 * PAD));
	}

	/** @return the bound combination as shown, or that there is none */
	private String name() {
		KeyCombo combo = setting.combo();
		if (!combo.equals(shownCombo) || shownVersion != KeyCombos.version()) {
			shownCombo = combo;
			shownVersion = KeyCombos.version();
			keyName = combo.bound() ? combo.shown(KeyCombos::keyName) : UNBOUND;
		}
		return keyName;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		float hover = hover(ui, 0, contains(mouseX, mouseY));
		// while it listens its field has the focus: the key's quiet note shows then
		KeySetting.focus(setting, this, recorder.recording());
		ui.rounded(x, y, width, height, Radius.SM, palette.control());
		if (recorder.recording() && finish(recorder.poll())) {
			return;
		}
		if (recorder.recording()) {
			float pulse = 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1_000_000_000.0 * Math.PI * 2.0 * PULSE_RATE);
			ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(ui.accent, 0.18f));
			ui.ring(x, y, width, height, Radius.SM, 1, Ui.withOpacity(ui.accent, pulse));
			String soFar = recorder.soFar();
			ui.textCentered(ui.fit(soFar.isEmpty() ? LISTENING : soFar, width - 8), x + width / 2, y + height / 2, palette.text());
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
			case OWN -> palette.coloredText(0xFF000000 | SystemColors.RED_TEXT_RGB);
			case OTHER -> palette.coloredText(SystemColors.ORANGE);
			case NONE -> setting.combo().bound() ? palette.text() : palette.textSecondary();
		};
		ui.textCentered(ui.fit(name, width - 8), x + width / 2, y + height / 2, color);
	}

	public boolean listening() {
		return recorder.recording();
	}

	@Override
	public boolean wantsKeyboard() {
		return recorder.recording();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// a right-click on the button removes the key, listening or not
		if (button == ScreenInput.RIGHT && contains(mouseX, mouseY)) {
			recorder.stop();
			finish(KeyCapture.Result.CLEARED);
			return true;
		}
		if (recorder.recording()) {
			if (button == ScreenInput.LEFT) {
				// a left click cancels; left click is the game's attack and mining button
				recorder.stop();
				return contains(mouseX, mouseY);
			}
			finish(recorder.button(button));
			return true;
		}
		if (button == ScreenInput.LEFT && contains(mouseX, mouseY)) {
			recorder.start(this::finish);
			UiSounds.click();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!recorder.recording()) {
			return false;
		}
		finish(recorder.key(key));
		return true;
	}

	/** @return whether the recording ended: the combination is bound, or cleared, or nothing changed after Esc */
	private boolean finish(KeyCapture.Result result) {
		switch (result) {
			case DONE -> {
				recorder.stop();
				setting.bind(recorder.combo());
				UiSounds.play(UiSounds.Cue.ON);
				onChanged.run();
			}
			case CLEARED -> {
				recorder.stop();
				setting.bind(KeyCombo.NONE);
				UiSounds.play(UiSounds.Cue.OFF);
				onChanged.run();
			}
			case CANCELLED -> recorder.stop();
			case PENDING -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public void loseFocus() {
		recorder.stop();
		KeySetting.focus(setting, this, false);
	}
}
