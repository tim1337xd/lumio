package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

/** A row of two quiet links across the page, without a card: one with an icon on the left, a smaller one on the right. */
public class LinksRowControl extends Control {
	private static final int HEIGHT = 14;
	private static final int INSET = 4;
	private static final int ICON = 10;
	private static final int ICON_GAP = 6;

	private final String icon;
	private final String left;
	private final Runnable leftAction;
	private final String right;
	private final Runnable rightAction;
	private static final String SURE = "Sure?";
	private static final long ARMED_MS = 4000L;
	/** Whether the right link asks first, and since when it is asking. */
	private boolean confirmRight;
	private long armedAt;

	public LinksRowControl confirmRight(boolean confirm) {
		confirmRight = confirm;
		return this;
	}

	private boolean armed() {
		return armedAt != 0L && System.currentTimeMillis() - armedAt < ARMED_MS;
	}

	private String rightText() {
		return armed() ? SURE : right;
	}

	/** What the links take, measured in the font the text is drawn in, from the last layout. */
	private int leftWidth;
	private int rightWidth;

	public LinksRowControl(String icon, String left, Runnable leftAction, String right, Runnable rightAction) {
		this.icon = icon;
		this.left = left;
		this.leftAction = leftAction;
		this.right = right;
		this.rightAction = rightAction;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	public boolean bare() {
		return true;
	}

	@Override
	protected int measure(Ui ui, int width) {
		leftWidth = ICON + ICON_GAP + ui.width(left);
		rightWidth = Math.round(ui.widthScaled(rightText(), TextScale.SMALL));
		return HEIGHT;
	}

	private boolean overLeft(double mouseX, double mouseY) {
		return mouseY >= y && mouseY < y + height && mouseX >= x + INSET && mouseX < x + INSET + leftWidth;
	}

	private boolean overRight(double mouseX, double mouseY) {
		return mouseY >= y && mouseY < y + height && mouseX >= x + width - INSET - rightWidth && mouseX < x + width - INSET;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		int middle = y + HEIGHT / 2;
		int leftColor = Ui.mix(ui.palette.textSecondary(), ui.palette.text(), 0.6f + 0.4f * hover(ui, 0, overLeft(mouseX, mouseY)));
		if (!left.isEmpty()) {
			ui.icon(icon, x + INSET, middle - ICON / 2, ICON, leftColor);
		}
		ui.textMiddle(left, x + INSET + ICON + ICON_GAP, middle, leftColor);
		int rightColor = Ui.mix(ui.palette.textSecondary(), ui.palette.text(), hover(ui, 1, overRight(mouseX, mouseY)));
		ui.textAtScaled(rightText(), x + width - INSET - rightWidth, middle - TextScale.SMALL * 4f, TextScale.SMALL, rightColor);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		Runnable action = overLeft(mouseX, mouseY) ? leftAction : overRight(mouseX, mouseY) ? rightAction : null;
		if (action == null) {
			return false;
		}
		UiSounds.click();
		if (action == rightAction && confirmRight && !armed()) {
			armedAt = System.currentTimeMillis();
			return true;
		}
		armedAt = 0L;
		action.run();
		return true;
	}
}
