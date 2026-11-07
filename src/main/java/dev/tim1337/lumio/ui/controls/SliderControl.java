package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** A slider: a thin track filled up to a round knob, with the value on the right. */
public class SliderControl extends Control {
	private static final int VALUE_WIDTH = 34;
	private static final int KNOB_RADIUS = 7;
	/** Number of notches on the track; a tick sound plays when the knob passes one. */
	private static final int NOTCHES = 10;

	private final FloatSetting setting;
	private final Runnable onChanged;
	private boolean dragging;
	private final Spring hold = Spring.bouncy(Spring.DEFAULT);
	/** The notch the knob was at last, to tick when it passes the next one. */
	private int lastNotch = -1;
	/** Whether the value is being typed: clicking the number turns it into a field, applied on Enter or when clicking elsewhere, Esc cancels. */
	private boolean editing;
	private String typed = "";
	/** Where the caret is in the typed text. */
	private int caret;
	/** The whole text is selected (after the click and Ctrl+A): the next keystroke replaces it. */
	private final TextSelection selection = new TextSelection();
	/** The knob's shown place, which glides to a typed value instead of jumping there. */
	private final Spring knob = Spring.smooth(Spring.SNAPPY);
	private boolean knobPlaced;
	/** The most characters a typed value has. */
	private static final int MOST_TYPED = 9;
	/** The units an amount of coins may be typed with. */
	private static final String COIN_UNITS = "kKmMbB";
	/** Two presses this close together are a double click, as the game counts one. */
	private static final long DOUBLE_CLICK_MILLIS = 250L;
	/** When the track was last pressed, for a double click. */
	private long lastPress;

	public SliderControl(FloatSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return 20;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, 200);
	}

	private int trackLeft() {
		return x + KNOB_RADIUS;
	}

	private int trackWidth() {
		return width - VALUE_WIDTH - KNOB_RADIUS * 2;
	}

	private float fraction() {
		return setting.fraction(setting.get());
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		int middle = y + height / 2;
		int left = trackLeft();
		int length = trackWidth();

		ui.rounded(left, middle - 2, length, 4, Radius.pill(4), ui.palette.trackOff());
		// the knob follows a drag at once and glides to a typed value
		float shownFraction = dragging || !knobPlaced || !ui.animated ? knob.snap(fraction()) : knob.follow(ui, fraction());
		knobPlaced = true;
		int knobX = left + Math.round(shownFraction * length);
		if (knobX > left) {
			ui.rounded(left, middle - 2, knobX - left, 4, Radius.pill(4), ui.accent);
		}
		float h = hover(ui, 0, contains(mouseX, mouseY) || dragging);
		// the knob grows slightly while held and springs back with a small bounce when released
		float grip = hold.follow(ui, dragging ? 1f : 0f);
		float radius = KNOB_RADIUS + 1.5f * grip;
		ui.circle(knobX, middle, KNOB_RADIUS + 4, Ui.withOpacity(ui.accent, 0.18f * h));
		ui.roundedSmooth(knobX - radius, middle + 1 - radius, radius * 2, radius * 2, radius, Ui.KNOB_SHADOW);
		ui.roundedSmooth(knobX - radius, middle - radius, radius * 2, radius * 2, radius, ui.palette.knob());
		if (grip > 0.02f) {
			ui.circle(knobX, middle, KNOB_RADIUS - 3, Ui.withOpacity(ui.accent, 0.25f * Math.min(1f, grip)));
		}

		if (editing) {
			// the number as a text field on the right, with a cursor
			int boxWidth = Math.max(VALUE_WIDTH + 12, ui.width(typed) + 18);
			int boxX = x + width - boxWidth;
			ui.rounded(boxX, middle - 10, boxWidth, 20, Radius.SM, ui.palette.control());
			ui.ring(boxX, middle - 10, boxWidth, 20, Radius.SM, 1, ui.accent);
			selection.draw(ui, boxX + 7, middle, ui.width(typed));
			ui.textMiddle(typed, boxX + 7, middle, ui.palette.text());
			if (!selection.all() && (System.currentTimeMillis() / 500) % 2 == 0) {
				ui.fill(boxX + 7 + ui.width(typed.substring(0, caret)), middle - 6, 1, 12, ui.palette.text());
			}
			return;
		}
		// at zero the setting's effect is off, which is shown instead of the number
		boolean off = setting.get() == 0f && setting.min() <= 0f;
		boolean overValue = valueContains(mouseX, mouseY);
		float v = hover(ui, 1, overValue);
		int valueColor = off ? Ui.withOpacity(ui.palette.textSecondary(), 0.7f) : Ui.mix(ui.palette.textSecondary(), ui.palette.text(), v);
		String shown = off ? "Off" : setting.format(setting.get());
		if (v > 0.02f) {
			// clicking the number edits it; it is highlighted slightly to hint at that
			ui.rounded(x + width - ui.width(shown) - 7, middle - 9, ui.width(shown) + 14, 18, Radius.SM, Ui.withOpacity(ui.accent, 0.16f * v));
		}
		ui.textRight(shown, x + width, middle, valueColor);
	}

	private boolean valueContains(double mouseX, double mouseY) {
		return mouseX >= x + width - VALUE_WIDTH - 8 && mouseX < x + width + 4 && mouseY >= y && mouseY < y + height;
	}

	private void commit() {
		if (!editing) {
			return;
		}
		editing = false;
		float value = setting.parseTyped(typed);
		// unparsable input keeps the value
		if (!Float.isNaN(value)) {
			// within the range and rounded to the displayed precision, so the stored value is the one shown; the knob glides there
			setting.set(setting.rounded(value));
			onChanged.run();
		}
	}

	@Override
	public boolean wantsKeyboard() {
		return editing;
	}

	@Override
	public void loseFocus() {
		commit();
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!editing) {
			return false;
		}
		if (allowed(codepoint)) {
			insert(String.valueOf((char) codepoint));
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!editing) {
			return false;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			commit();
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			editing = false;
			return true;
		}
		boolean control = (modifiers & (ScreenInput.CONTROL | ScreenInput.SUPER)) != 0;
		if (control && ScreenInput.letter(key, 'V')) {
			// pasted text keeps only what may be typed
			String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
			StringBuilder kept = new StringBuilder();
			for (int i = 0; pasted != null && i < pasted.length(); i++) {
				if (allowed(pasted.charAt(i))) {
					kept.append(pasted.charAt(i));
				}
			}
			insert(kept.toString());
			return true;
		}
		if (selection.key(key, modifiers, () -> typed, value -> {
			typed = value;
			caret = typed.length();
		}, MOST_TYPED)) {
			return true;
		}
		switch (key) {
			case InputConstants.KEY_BACKSPACE -> {
				if (caret > 0) {
					typed = typed.substring(0, caret - 1) + typed.substring(caret);
					caret--;
				}
			}
			case InputConstants.KEY_DELETE -> {
				if (caret < typed.length()) {
					typed = typed.substring(0, caret) + typed.substring(caret + 1);
				}
			}
			case InputConstants.KEY_LEFT -> caret = Math.max(0, caret - 1);
			case InputConstants.KEY_RIGHT -> caret = Math.min(typed.length(), caret + 1);
			case InputConstants.KEY_HOME -> caret = 0;
			case InputConstants.KEY_END -> caret = typed.length();
			default -> {
			}
		}
		// every other key belongs to the field while it has the focus, so no shortcut of the page fires
		return true;
	}

	/** @return whether a character may be typed: digits, a decimal point, a minus where the range has negative values, and k, M or B for coins */
	private boolean allowed(int codepoint) {
		return codepoint >= '0' && codepoint <= '9' || codepoint == '.' || codepoint == ',' || codepoint == '-' && setting.min() < 0f
			|| setting.isCoins() && COIN_UNITS.indexOf(codepoint) >= 0;
	}

	/** Types text at the caret, in place of the whole text while it is selected. */
	private void insert(String text) {
		if (selection.all()) {
			selection.clear();
			typed = "";
			caret = 0;
		}
		String next = typed.substring(0, caret) + text + typed.substring(caret);
		if (next.length() <= MOST_TYPED) {
			typed = next;
			caret += text.length();
		}
	}

	private void setFromMouse(double mouseX) {
		float fraction = Mth.clamp((float) ((mouseX - trackLeft()) / trackWidth()), 0f, 1f);
		// on the slider's scale, resting on a snap value nearby, and rounded to the displayed precision, so the stored value is the one shown
		setting.set(setting.valueAt(fraction));
		onChanged.run();
		// a tick at every tenth, pitched higher further along, like the sounds of the other controls
		int notch = Math.round(fraction * NOTCHES);
		if (notch != lastNotch) {
			if (lastNotch >= 0) {
				UiSounds.tick((float) notch / NOTCHES);
			}
			lastNotch = notch;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && valueContains(mouseX, mouseY)) {
			// clicked the number: start typing
			editing = true;
			dragging = false;
			typed = setting.format(setting.get());
			caret = typed.length();
			selection.selectAll();
			return true;
		}
		if (editing) {
			commit();
		}
		if (button == 0 && contains(mouseX, mouseY)) {
			long now = System.currentTimeMillis();
			boolean second = now - lastPress <= DOUBLE_CLICK_MILLIS;
			lastPress = now;
			if (second && setting.resetsOnDoubleClick()) {
				// no drag starts from this press: the next small move would take the value back from where it was reset to
				lastPress = 0L;
				setting.reset();
				UiSounds.click();
				onChanged.run();
				return true;
			}
			dragging = true;
			lastNotch = -1;
			setFromMouse(mouseX);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		if (dragging) {
			setFromMouse(mouseX);
			return true;
		}
		return false;
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		dragging = false;
	}
}
