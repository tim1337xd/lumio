package dev.tim1337.lumio.api.experimental.draw;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.draw.Canvas;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Select-all state of a text field, as Ctrl+A works in any program: typing replaces the selection, Backspace and Delete remove it, and Ctrl+C,
 * Ctrl+X and Ctrl+V copy, cut and paste. Each field owns one and consults it first for every key and character.
 */
public final class TextSelection {
	private boolean all;

	public boolean all() {
		return all;
	}

	public void clear() {
		all = false;
	}

	/** Selects everything, as Ctrl+A does; also used initially (a number that gets typed over). */
	public void selectAll() {
		all = true;
	}

	/**
	 * Handles a key: Ctrl+A, C, X, V, and Backspace or Delete over the selection. @return whether the key was consumed; unhandled keys keep the
	 * selection, except cursor movement keys, which end it
	 */
	public boolean key(int key, int modifiers, Supplier<String> get, Consumer<String> set, int max) {
		boolean control = (modifiers & (ScreenInput.CONTROL | ScreenInput.SUPER)) != 0;
		if (control && ScreenInput.letter(key, 'A')) {
			all = !get.get().isEmpty();
			return true;
		}
		if (control && (ScreenInput.letter(key, 'C') || ScreenInput.letter(key, 'X'))) {
			if (all) {
				Minecraft.getInstance().keyboardHandler.setClipboard(get.get());
				if (ScreenInput.letter(key, 'X')) {
					set.accept("");
					all = false;
				}
			}
			return true;
		}
		if (control && ScreenInput.letter(key, 'V')) {
			String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (pasted != null) {
				String base = all ? "" : get.get();
				String joined = base + pasted.strip().replaceAll("[\\r\\n]+", " ");
				set.accept(joined.substring(0, Math.min(max, joined.length())));
			}
			all = false;
			return true;
		}
		if (all && (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE)) {
			set.accept("");
			all = false;
			return true;
		}
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_HOME || key == InputConstants.KEY_END) {
			all = false;
		}
		return false;
	}

	/** @return the text after typing: the typed characters replace everything while it is selected */
	public String typed(String current, String letters) {
		if (all) {
			all = false;
			return letters;
		}
		return current + letters;
	}

	/** Draws the selection highlight behind the text, from its start, as wide as the text. */
	public void draw(Canvas ui, int x, int middle, int textWidth) {
		if (all && textWidth > 0) {
			ui.rounded(x - 1, middle - 6, textWidth + 2, 12, Radius.XS, Draw.withOpacity(ui.accent(), 0.4f));
		}
	}
}
