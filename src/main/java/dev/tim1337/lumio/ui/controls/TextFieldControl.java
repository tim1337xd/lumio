package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * A single-line text field. Clicking places the cursor; text can be pasted, and Ctrl+A selects all. A secret (a token) is always masked, also
 * while it is typed; the eye button at its end shows it until pressed again.
 */
public class TextFieldControl extends Control {
	private static final int HEIGHT = 24;
	private static final int MAX = 200;
	/** The eye button of a secret: its icon size and the room it takes at the field's end. */
	private static final int EYE_SIZE = 12;
	private static final int EYE_ROOM = 22;
	/** The most dots a masked secret shows. */
	private static final int MOST_DOTS = 24;
	private static final String DOTS = "•".repeat(MOST_DOTS);

	private final StringSetting setting;
	private final Runnable onChanged;
	private final TextSelection selection = new TextSelection();
	private boolean focused;
	/** Whether the secret is shown as it is, by the eye button. */
	private boolean revealed;

	public TextFieldControl(StringSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return HEIGHT;
	}

	/** A text field takes the room the name leaves (at least half the row); a long description then goes under the row. */
	@Override
	public int inlineWidth(Ui ui, int available) {
		return available;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		float h = hover(ui, 0, contains(mouseX, mouseY) || focused);
		ui.rounded(x, y, width, height, Radius.SM, ui.palette.control());
		ui.ring(x, y, width, height, Radius.SM, 1, focused ? ui.accent : Ui.mix(ui.palette.controlBorder(), ui.accent, 0.5f * h));
		String value = setting.get();
		String shown;
		int color = ui.palette.text();
		if (value.isEmpty() && !focused) {
			shown = "Not set";
			color = ui.palette.textSecondary();
		} else if (setting.secret() && !revealed) {
			// secrets are masked, also while typed; only their presence and length (up to a point) are shown
			shown = DOTS.substring(0, Math.min(MOST_DOTS, value.length()));
		} else {
			shown = value;
		}
		String fitted = ui.fit(shown, width - 20 - (setting.secret() ? EYE_ROOM : 0));
		if (focused) {
			selection.draw(ui, x + 9, y + height / 2, ui.width(fitted));
		}
		ui.textMiddle(fitted, x + 9, y + height / 2, color);
		if (focused && !selection.all() && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(x + 9 + ui.width(fitted) + 1, y + 6, 1, height - 12, ui.palette.text());
		}
		if (setting.secret()) {
			drawEye(ui, mouseX, mouseY);
		}
	}

	/** The eye button: the eye in the accent color while the secret is shown, crossed out while it is masked. */
	private void drawEye(Ui ui, int mouseX, int mouseY) {
		int eyeX = x + width - EYE_ROOM / 2 - EYE_SIZE / 2 - 4;
		int eyeY = y + height / 2 - EYE_SIZE / 2;
		int color = revealed ? ui.accent : overEye(mouseX, mouseY) ? ui.palette.text() : ui.palette.textSecondary();
		ui.icon("eye", eyeX, eyeY, EYE_SIZE, color);
		if (!revealed) {
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(eyeX + EYE_SIZE / 2f, eyeY + EYE_SIZE / 2f);
			pose.rotate((float) (Math.PI / 4.0));
			ui.fill(-EYE_SIZE / 2 - 1, 0, EYE_SIZE + 2, 1, color);
			pose.popMatrix();
		}
	}

	private boolean overEye(double mouseX, double mouseY) {
		return setting.secret() && mouseX >= x + width - EYE_ROOM - 4 && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && overEye(mouseX, mouseY)) {
			revealed = !revealed;
			UiSounds.click();
			return true;
		}
		selection.clear();
		if (button == 0 && contains(mouseX, mouseY)) {
			focused = true;
			return true;
		}
		focused = false;
		return false;
	}

	@Override
	public boolean wantsKeyboard() {
		return focused;
	}

	@Override
	public void loseFocus() {
		focused = false;
		selection.clear();
	}

	private void set(String value) {
		setting.set(value);
		onChanged.run();
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!focused || codepoint < 32) {
			return false;
		}
		String next = selection.typed(setting.get(), new String(Character.toChars(codepoint)));
		if (next.length() <= MAX) {
			set(next);
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!focused) {
			return false;
		}
		if (selection.key(key, modifiers, setting::get, this::set, MAX)) {
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE && !setting.get().isEmpty()) {
			set(setting.get().substring(0, setting.get().length() - 1));
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE) {
			focused = false;
			selection.clear();
			return true;
		}
		return false;
	}
}
