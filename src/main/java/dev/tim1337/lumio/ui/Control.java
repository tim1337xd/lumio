package dev.tim1337.lumio.ui;

/**
 * A widget in the menu that displays or edits a setting. Its row places it, giving it a position and the available width; the control
 * determines its own height.
 */
public abstract class Control {
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	/** The visible part of the page, in window pixels, set by the menu every frame: for content kept in view or not drawn while partly hidden. */
	protected static int viewTop;
	protected static int viewBottom = Integer.MAX_VALUE;

	/** Sets the visible part of the page, from its top to its bottom. */
	public static void setView(int top, int bottom) {
		viewTop = top;
		viewBottom = bottom;
	}

	/** Sets position and width; this is also when the control computes its height. */
	public final void place(Ui ui, int x, int y, int width) {
		this.x = x;
		this.y = y;
		this.width = width;
		try {
			this.height = measure(ui, width);
		} catch (RuntimeException e) {
			// a broken row takes no room, and the rest of the page is still laid out
			RowGuard.report(getClass().getSimpleName(), e);
			this.height = 0;
			broken = true;
		}
	}

	protected abstract int measure(Ui ui, int width);

	public int width() {
		return width;
	}

	public int left() {
		return x;
	}

	public int top() {
		return y;
	}

	public int height() {
		return height;
	}

	/**
	 * Whether the control spans the full width with the setting's name above it, as lists and color pickers do. Other controls sit on the right,
	 * in the name's row.
	 */
	public boolean isBlock() {
		return false;
	}

	/** @return whether the setting's name is shown with the control; blocks that are their own visual (the feature cards) have none */
	public boolean hasLabel() {
		return true;
	}

	/** @return whether the control is drawn without its group's card: it consists of cards itself, and a card around cards looked like a box in a box */
	public boolean bare() {
		return false;
	}

	/** @return the width the control wants to the right of its setting's name */
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, 120);
	}

	/**
	 * @return whether the control needs its whole width (segments, color dots): where it does not fit beside its name, it goes on a line of its own
	 *         under the name instead of being squeezed. Others (fields, lists) get narrower.
	 */
	public boolean keepsWidth() {
		return false;
	}

	/** @return whether the control starts at the left on a line of its own under its name; others end at the right, like the rows beside their names */
	public boolean leftOnOwnLine() {
		return false;
	}

	/** Offset of a lone button from the edge on pages with switches and Edit buttons: the width of a switch plus the gap before it. */
	public static final int SWITCH_COLUMN = 44;

	/** @return whether the control has a switch at the right edge of its row and an Edit button to its left, like a feature row */
	public boolean hasEditButton() {
		return false;
	}

	/** @return whether the control is a button without a switch; on pages with switches and Edit buttons it aligns with the Edit buttons */
	public boolean isLoneButton() {
		return false;
	}

	/** The full text of a value cut to fit, while the mouse is over it; the menu shows it as a tooltip. */
	private static String fullText;

	/** Shows a value cut to fit its room in full on hover: called by a control while the mouse is over the cut value. */
	protected static void showFullText(String text) {
		fullText = text;
	}

	/** @return the full text of the value under the mouse this frame, or null, and forgets it */
	public static String takeFullText() {
		String text = fullText;
		fullText = null;
		return text;
	}

	private final float[] hovers = new float[24];

	/** @return smoothed hover amount of a part of the control, from 0 to 1; the slot identifies the part */
	protected float hover(Ui ui, int slot, boolean over) {
		int index = Math.min(slot, hovers.length - 1);
		hovers[index] = ui.approach(hovers[index], over ? 1f : 0f, Ui.HOVER_RATE);
		return hovers[index];
	}

	public abstract void render(Ui ui, int mouseX, int mouseY);

	/** Whether the control failed while it was measured; it is then not drawn. */
	private boolean broken;

	/** Draws the control; one that fails is logged once and left out, so the rest of the page is still drawn. */
	public final void renderSafely(Ui ui, int mouseX, int mouseY) {
		if (broken) {
			return;
		}
		try {
			render(ui, mouseX, mouseY);
		} catch (RuntimeException e) {
			RowGuard.report(getClass().getSimpleName(), e);
			broken = true;
		}
	}

	/** Drawn after the whole page, on top of it but still inside the visible area: for content that sticks to the top while scrolling. */
	public void renderSticky(Ui ui, int mouseX, int mouseY) {
	}

	/** Draws popups that open over the rest, such as a dropdown list. They are drawn last. */
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
	}

	/** @return whether the control has a popup open, which receives clicks before anything else */
	public boolean isOverlayOpen() {
		return false;
	}

	/** Mouse wheel input while an open popup is taller than the screen and scrolls. */
	public void overlayScrolled(double amount) {
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY) {
		return false;
	}

	public void mouseReleased(double mouseX, double mouseY) {
	}

	public boolean keyPressed(int key, int modifiers) {
		return false;
	}

	public boolean charTyped(int codepoint) {
		return false;
	}

	/** @return whether the control wants keyboard input; only the few with text fields do */
	public boolean wantsKeyboard() {
		return false;
	}

	public void loseFocus() {
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
