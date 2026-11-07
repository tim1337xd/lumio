package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ui.Ui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

/** A one-line text field of the main menu's lists: typing, backspace (a word with Ctrl), and pasting with Ctrl + V. */
final class TitleField {
	private static final int MAX_LENGTH = 128;
	private static final long BLINK_MILLIS = 530L;
	private static final float TEXT = 1f / 9.6f;

	private String text = "";
	private final String placeholder;
	private boolean focused;
	/** Bumps when the text changes, so the lists filter again only then. */
	private int version;

	TitleField(String placeholder) {
		this.placeholder = placeholder;
	}

	String text() {
		return text;
	}

	int version() {
		return version;
	}

	void setText(String value) {
		text = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
		version++;
	}

	boolean focused() {
		return focused;
	}

	void focus(boolean value) {
		focused = value;
	}

	boolean charTyped(int codepoint) {
		if (!focused || text.length() >= MAX_LENGTH || Character.isISOControl(codepoint)) {
			return false;
		}
		setText(text + Character.toString(codepoint));
		return true;
	}

	boolean keyPressed(int key, boolean control) {
		if (!focused) {
			return false;
		}
		if (key == InputConstants.KEY_BACKSPACE && !text.isEmpty()) {
			if (control) {
				int cut = text.stripTrailing().lastIndexOf(' ');
				setText(cut < 0 ? "" : text.substring(0, cut + 1));
			} else {
				setText(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
			}
			return true;
		}
		if (ScreenInput.letter(key, 'V') && control) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replace("\n", " ").replace("\r", "").strip();
			setText(text + clip);
			return true;
		}
		return key == InputConstants.KEY_BACKSPACE;
	}

	/** Draws the field: a soft background, a search icon, and the text or the placeholder, with the cursor while focused. */
	void draw(Ui ui, float x, float y, float width, float height, float alpha) {
		ui.roundedSmooth(x, y, width, height, 12f, Ui.withOpacity(0xFFFFFFFF, (focused ? 0.12f : 0.08f) * alpha));
		if (focused) {
			ui.ring(Math.round(x), Math.round(y), Math.round(width), Math.round(height), 12f, 1, Ui.withOpacity(ui.accent, 0.8f * alpha));
		}
		ui.icon("search", Math.round(x + 12f), Math.round(y + (height - 16f) / 2f), 16, Ui.withOpacity(0xFFFFFFFF, 0.6f * alpha));
		float textX = x + 37f;
		float scale = 14f * TEXT;
		float middle = y + height / 2f - 4.5f * scale;
		float room = width - 37f - 12f;
		if (text.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(placeholder, room, scale), textX, middle, scale, Ui.withOpacity(0xFFFFFFFF, 0.55f * alpha));
		} else {
			String shown = text;
			// the end of a long text stays in view
			while (shown.length() > 1 && ui.widthScaled(shown, scale) > room - 4f) {
				shown = shown.substring(1);
			}
			ui.textAtScaled(shown, textX, middle, scale, Ui.withOpacity(0xFFFFFFFF, alpha));
			textX += ui.widthScaled(shown, scale);
		}
		if (focused && (System.currentTimeMillis() / BLINK_MILLIS) % 2 == 0) {
			ui.roundedSmooth(textX + 1f, y + height / 2f - 8f, 1.5f, 16f, 0.75f, Ui.withOpacity(0xFFFFFFFF, alpha));
		}
	}
}
