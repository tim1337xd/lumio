package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

/** A button showing the current choice of a list. It opens the list over the content to pick another, unfolding from the button. */
public abstract class ListDropdownControl extends Control {
	private static final int ITEM = 22;
	private static final int LEAST_LIST_WIDTH = 120;
	private static final int TEXT_LEFT = 9;
	private static final int ARROW_ROOM = 20;
	/** Before an item's text: the room for the check mark. */
	private static final int ITEM_TEXT_LEFT = 22;
	private static final int ITEM_TEXT_RIGHT = 8;
	private static final int ROOM_BESIDE_TEXT = 40;

	private final int buttonHeight;
	private boolean open;
	/** Unfold progress from 0 (just opened) to 1: the list grows out of its button. */
	private final Spring unfold = Spring.smooth(Spring.SNAPPY);
	/** The widest choice's text, measured while the list is drawn. */
	private int widestItem;
	/** Current list placement: below or above the button, and its scroll offset. */
	private final PopupPlace place = new PopupPlace();

	protected ListDropdownControl(int buttonHeight) {
		this.buttonHeight = buttonHeight;
	}

	/** @return how many choices there are */
	protected abstract int count();

	/** @return the name of a choice */
	protected abstract String name(int index);

	/** @return the index of the current choice */
	protected abstract int selectedIndex();

	/** Applies a choice other than the current one. */
	protected abstract void pick(int index);

	@Override
	protected int measure(Ui ui, int width) {
		return buttonHeight;
	}

	private int widest(Ui ui) {
		int widest = 0;
		for (int i = 0; i < count(); i++) {
			widest = Math.max(widest, ui.width(name(i)));
		}
		return widest;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, widest(ui) + ROOM_BESIDE_TEXT);
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		boolean hovered = contains(mouseX, mouseY);
		float h = hover(ui, 0, hovered || open);
		ui.shadow(x, y, width, height, Radius.SM, 3, ui.palette.glass() ? 0f : 0.10f);
		ui.rounded(x, y, width, height, Radius.SM, ui.palette.control());
		ui.ring(x, y, width, height, Radius.SM, 1, ui.palette.controlBorder());
		if (h > 0f) {
			ui.rounded(x, y, width, height, Radius.SM, Ui.withOpacity(ui.palette.hover(), h));
			ui.ring(x, y, width, height, Radius.SM, 1, Ui.withOpacity(ui.accent, 0.6f * h));
		}
		String name = name(selectedIndex());
		String shown = ui.fit(name, width - TEXT_LEFT - ARROW_ROOM);
		ui.textMiddle(shown, x + TEXT_LEFT, y + height / 2, ui.palette.text());
		if (hovered && !open && !shown.equals(name)) {
			showFullText(name);
		}

		// a small down arrow, like macOS pop-up buttons
		ui.icon("chevron", x + width - 18, y + height / 2 - 6, 12, ui.palette.textSecondary());
	}

	private int listWidth() {
		return Math.max(Math.max(width, LEAST_LIST_WIDTH), widestItem + ITEM_TEXT_LEFT + ITEM_TEXT_RIGHT);
	}

	private int listHeight() {
		return count() * ITEM + 8;
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		widestItem = widest(ui);
		int listWidth = listWidth();
		int listX = x + width - listWidth;
		place.place(ui, y, height, listHeight());
		int listY = place.top;
		int listH = place.height;
		float grown = unfold.follow(ui, 1f);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		// the list grows from the button edge it is attached to, at its right end, so its origin is clear: downwards from below the button, or
		// upwards from above it when there is no room below
		float scaleX = 0.9f + 0.1f * grown;
		float scaleY = 0.5f + 0.5f * grown;
		float originY = listY > y ? listY : listY + listH;
		pose.translate(listX + listWidth, originY);
		pose.scale(scaleX, scaleY);
		pose.translate(-(listX + listWidth), -originY);
		ui.shadow(listX, listY, listWidth, listH, Radius.MD, 8, 0.45f * Math.min(1f, grown));
		ui.rounded(listX, listY, listWidth, listH, Radius.MD, ui.palette.popup());
		ui.ring(listX, listY, listWidth, listH, Radius.MD, 1, ui.palette.popupBorder());

		int selected = selectedIndex();
		ui.graphics.enableScissor(listX, listY + 2, listX + listWidth, listY + listH - 2);
		int first = listY + 4 - Math.round(place.scroll);
		for (int i = 0; i < count(); i++) {
			int itemY = first + i * ITEM;
			if (itemY + ITEM < listY || itemY > listY + listH) {
				continue;
			}
			boolean hovered = mouseY >= listY && mouseY < listY + listH && mouseX >= listX + 4 && mouseX < listX + listWidth - 4 && mouseY >= itemY && mouseY < itemY + ITEM;
			if (hovered) {
				ui.rounded(listX + 4, itemY, listWidth - 8, ITEM, Radius.SM, ui.accent);
			}
			int color = hovered ? 0xFFFFFFFF : ui.palette.glass() ? 0xFFFFFFFF : ui.palette.text();
			ui.textMiddle(name(i), listX + ITEM_TEXT_LEFT, itemY + ITEM / 2, color);
			if (i == selected) {
				// the menu's check mark icon, as in checkboxes, centered in the space before the name
				ui.icon("tick", listX + 7, itemY + (ITEM - 12) / 2, 12, color);
			}
		}
		ui.graphics.disableScissor();
		pose.popMatrix();
	}

	@Override
	public void overlayScrolled(double amount) {
		place.scrollBy(amount, ITEM);
	}

	@Override
	public boolean isOverlayOpen() {
		return open;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		if (open) {
			int listWidth = listWidth();
			int listX = x + width - listWidth;
			open = false;
			if (place.contains(mouseX, mouseY, listX, listWidth)) {
				int index = (int) Math.floor((mouseY - place.top - 4 + place.scroll) / ITEM);
				if (index >= 0 && index < count() && index != selectedIndex()) {
					pick(index);
				}
				return true;
			}
			return contains(mouseX, mouseY); // clicking the button again closes the list
		}
		if (contains(mouseX, mouseY)) {
			open = true;
			place.scroll = 0f;
			unfold.snap(0f);
			UiSounds.click();
			return true;
		}
		return false;
	}

	@Override
	public void loseFocus() {
		open = false;
	}
}
