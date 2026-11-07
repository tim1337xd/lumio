package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.network.chat.Component;

/**
 * A push button with centered text, in the style of the menu's theme: a solid fill with a subtle border on the dark and light themes, neutral
 * glass on the glass themes; a primary button is filled with the accent. It shrinks slightly while pressed and springs back with a small bounce,
 * and a disabled one is dimmed and takes no clicks. Screens of their own (such as the source setup) draw their buttons with it too.
 */
public class ButtonControl extends Control {
	private static final int DESTRUCTIVE = SystemColors.DANGER;
	/** Opacity of the red outline of a destructive button. */
	private static final float BORDER_DESTRUCTIVE = 0.45f;
	/** How much the button shrinks while pressed. */
	private static final float PRESS = 0.045f;
	/** The usual height, how much a disabled button is dimmed, and how much lighter a primary button gets on hover and darker while pressed. */
	private static final int HEIGHT = 22;
	private static final float DISABLED = 0.4f;
	private static final float PRIMARY_HOVER = 0.14f;
	private static final float PRIMARY_PRESSED = 0.12f;

	private final String text;
	private final boolean destructive;
	private final Runnable action;
	private boolean pressed;
	private boolean primary;
	private boolean enabled = true;
	private int buttonHeight = HEIGHT;
	private final Spring squash = Spring.bouncy(Spring.DEFAULT);

	/** Whether the button asks for confirmation: the first click shows "Sure?", a second click within a few seconds runs the action. */
	private boolean confirm;
	private long armedAt;
	private static final long ARMED_MS = 4000L;

	/** Makes the button ask for confirmation before an action that cannot be undone, such as a reset. */
	public ButtonControl confirm() {
		confirm = true;
		return this;
	}

	/** Makes this the main button of its place, filled with the accent. */
	public ButtonControl primary() {
		primary = true;
		return this;
	}

	/** Sets the button's height, for screens whose buttons are taller than the menu's rows. */
	public ButtonControl height(int height) {
		buttonHeight = height;
		return this;
	}

	/** Enables or disables the button: a disabled one is dimmed and takes no clicks. */
	public void setEnabled(boolean on) {
		enabled = on;
		if (!on) {
			pressed = false;
		}
	}

	private boolean armed() {
		return confirm && System.currentTimeMillis() - armedAt < ARMED_MS;
	}

	public ButtonControl(Component text, boolean destructive, Runnable action) {
		this.text = text.getString();
		this.destructive = destructive;
		this.action = action;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return buttonHeight;
	}

	@Override
	public boolean isLoneButton() {
		return true;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		// at least as wide as the Edit button next to a switch, so buttons with short labels (Edit, Play) share one size
		return Math.min(available, Math.max(56, ui.width(text) + 28));
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		boolean hovered = enabled && contains(mouseX, mouseY);
		float fade = enabled ? 1f : DISABLED;
		float h = hover(ui, 0, hovered);
		float down = squash.follow(ui, pressed && hovered ? 1f : 0f);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		float scale = 1f - PRESS * down;
		pose.translate(x + width / 2f, y + height / 2f);
		pose.scale(scale, scale);
		pose.translate(-(x + width / 2f), -(y + height / 2f));

		ui.shadow(x, y, width, height, Radius.SM, 3, ui.palette.glass() ? 0f : 0.10f * fade);
		if (primary) {
			int fill = Ui.mix(Ui.mix(ui.accent, 0xFFFFFFFF, PRIMARY_HOVER * h), 0xFF000000, PRIMARY_PRESSED * Math.min(1f, down));
			ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(fill, fade));
			ui.textCentered(text, x + width / 2, y + height / 2, Ui.withOpacity(0xFFFFFFFF, fade));
			pose.popMatrix();
			return;
		}
		ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(ui.palette.control(), fade));
		ui.ring(x, y, width, height, Radius.SM, 1, Ui.withOpacity(destructive ? Ui.withOpacity(DESTRUCTIVE, BORDER_DESTRUCTIVE) : ui.palette.controlBorder(), fade));
		if (h > 0f) {
			ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(ui.palette.hover(), h));
			ui.ring(x, y, width, height, Radius.SM, 1, Ui.withOpacity(ui.accent, 0.6f * h));
		}
		if (down > 0.01f) {
			ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(ui.palette.hover(), Math.min(1f, down)));
		}
		boolean armed = armed();
		ui.textCentered(armed ? "Sure?" : text, x + width / 2, y + height / 2, Ui.withOpacity(destructive || armed ? DESTRUCTIVE : ui.palette.text(), fade));
		pose.popMatrix();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && enabled && contains(mouseX, mouseY)) {
			pressed = true;
			UiSounds.click();
			return true;
		}
		return false;
	}

	/** The action runs on mouse release over the button, as in other menus. */
	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		boolean fire = pressed && contains(mouseX, mouseY);
		pressed = false;
		if (fire && confirm && !armed()) {
			armedAt = System.currentTimeMillis(); // ask first; the next click runs the action
			return;
		}
		if (fire) {
			armedAt = 0L;
			action.run();
		}
	}
}
