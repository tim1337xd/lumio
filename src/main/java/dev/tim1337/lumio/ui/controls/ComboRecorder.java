package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.features.misc.keybinds.KeyCapture;
import dev.tim1337.lumio.features.misc.keybinds.KeyCombo;
import dev.tim1337.lumio.features.misc.keybinds.KeyCombos;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Records a key combination in the menu, for the key buttons and the command keybinds: the keys and mouse buttons pressed while it listens, ended by the
 * first one let go (looked at each frame, as the menu gets no key releases), or by a turn of the wheel. Esc cancels, Backspace or Delete clears.
 */
public final class ComboRecorder {
	/** The recorder listening now, for the wheel, which the menu hands to it before anything else. */
	private static ComboRecorder active;

	private KeyCapture capture = new KeyCapture();
	/** What the control does with the end of a recording. */
	private Predicate<KeyCapture.Result> finish = result -> false;
	private final List<String> letGo = new ArrayList<>(KeyCombo.MOST_KEYS);

	/** @return the recorder listening now, or null */
	public static ComboRecorder active() {
		return active;
	}

	/** @param finish what the control does with a result: binds, clears or cancels; true once the recording ended */
	public void start(Predicate<KeyCapture.Result> finish) {
		capture = new KeyCapture();
		this.finish = finish;
		active = this;
	}

	/** A turn of the wheel in the menu. @return whether a recorder took it: it ends that recording, as the trigger */
	public static boolean turnWheel(boolean up) {
		ComboRecorder recorder = active;
		if (recorder == null) {
			return false;
		}
		recorder.finish.test(recorder.wheel(up));
		return true;
	}

	public void stop() {
		if (active == this) {
			active = null;
		}
	}

	public boolean recording() {
		return active == this;
	}

	/** @return the combination recorded, after DONE */
	public KeyCombo combo() {
		return capture.combo();
	}

	/** @return what is held so far, as shown while recording: "Tab + …" */
	public String soFar() {
		KeyCombo so = capture.soFar();
		return so.bound() ? so.shown(KeyCombos::keyName) + " + …" : "";
	}

	/** A key of the keyboard went down (the game's key number). */
	public KeyCapture.Result key(int key) {
		return capture.down(InputConstants.Type.KEYSYM.getOrCreate(key).getName());
	}

	/** A mouse button went down (in {@link ScreenInput}'s numbers). */
	public KeyCapture.Result button(int button) {
		return capture.down(InputConstants.Type.MOUSE.getOrCreate(ScreenInput.gameButton(button)).getName());
	}

	/** A turn of the wheel: it ends the recording, as the trigger. */
	public KeyCapture.Result wheel(boolean up) {
		return capture.down(up ? KeyCombo.WHEEL_UP : KeyCombo.WHEEL_DOWN);
	}

	/** Looks once per frame whether a held key was let go: the first one ends the recording. */
	public KeyCapture.Result poll() {
		letGo.clear();
		for (String key : capture.held()) {
			if (!KeyCombos.down(key)) {
				letGo.add(key);
			}
		}
		for (String key : letGo) {
			KeyCapture.Result result = capture.up(key);
			if (result != KeyCapture.Result.PENDING) {
				return result;
			}
		}
		return KeyCapture.Result.PENDING;
	}
}
