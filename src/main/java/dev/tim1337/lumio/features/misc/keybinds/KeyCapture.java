package dev.tim1337.lumio.features.misc.keybinds;

import java.util.ArrayList;
import java.util.List;

/**
 * Records a key combination as the player sets a bind: they hold the keys they want and let go; the keys, in the order pressed, become the combination
 * (the last one pressed is the trigger). Esc alone cancels; Backspace or Delete alone clears the bind. A wheel turn ends it at once, as the trigger.
 * Plain data, so it is unit tested.
 */
public final class KeyCapture {
	public static final String ESCAPE = "key.keyboard.escape";
	public static final String BACKSPACE = "key.keyboard.backspace";
	public static final String DELETE = "key.keyboard.delete";

	/** What the recording came to. */
	public enum Result {
		/** Still listening. */
		PENDING,
		/** A combination was recorded. */
		DONE,
		/** Esc: nothing changes. */
		CANCELLED,
		/** Backspace or Delete: the bind is cleared. */
		CLEARED
	}

	private final List<String> pressed = new ArrayList<>();
	private final List<String> down = new ArrayList<>();
	private KeyCombo combo = KeyCombo.NONE;

	/** @return the combination recorded, after DONE */
	public KeyCombo combo() {
		return combo;
	}

	/** @return the keys pressed so far, as the button shows them while recording */
	public KeyCombo soFar() {
		return KeyCombo.pressed(pressed);
	}

	/** @return the keys held down so far, to look whether one was let go */
	public List<String> held() {
		return down;
	}

	/** A key or button went down. */
	public Result down(String key) {
		if (pressed.isEmpty() && down.isEmpty()) {
			// Esc first clears the key, as Backspace and Delete do: a key field waiting for a key is how a key is removed
			if (ESCAPE.equals(key) || BACKSPACE.equals(key) || DELETE.equals(key)) {
				return Result.CLEARED;
			}
		}
		if (KeyCombo.wheel(key)) {
			pressed.add(key);
			return finish();
		}
		if (!down.contains(key)) {
			down.add(key);
		}
		if (!pressed.contains(key) && pressed.size() < KeyCombo.MOST_KEYS) {
			pressed.add(key);
		}
		return Result.PENDING;
	}

	/** A key or button went up: the first key let go ends the recording with what was held. */
	public Result up(String key) {
		if (!down.remove(key) || pressed.isEmpty()) {
			return Result.PENDING;
		}
		return finish();
	}

	private Result finish() {
		combo = KeyCombo.pressed(pressed);
		pressed.clear();
		down.clear();
		return combo.bound() ? Result.DONE : Result.PENDING;
	}
}
