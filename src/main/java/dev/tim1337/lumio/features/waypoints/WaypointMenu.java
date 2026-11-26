package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.ui.UiSounds;
import dev.tim1337.lumio.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small popover menu of actions, opened from a "⋯" button, a map point or "+ Add area". An item can lead to another list in the same popover
 * (Color, Move to). A dangerous action asks for a second click.
 */
final class WaypointMenu {
	private static final int ITEM_HEIGHT = 18;
	private static final int PAD = 4;
	private static final int MIN_WIDTH = 96;
	private static final int DOT = 8;
	/** The room beside an item's text: its inset on the left and as much on the right. */
	private static final int TEXT_ROOM = 12;
	private static final long CONFIRM_MILLIS = 4000L;

	/** One line of the menu: a text, an optional color dot, and what a click does. */
	record Item(String text, int dotRgb, boolean danger, Runnable action) {
		static Item of(String text, Runnable action) {
			return new Item(text, -1, false, action);
		}

		static Item dot(String text, int rgb, Runnable action) {
			return new Item(text, rgb, false, action);
		}

		static Item danger(String text, Runnable action) {
			return new Item(text, -1, true, action);
		}
	}

	private final List<Item> items = new ArrayList<>();
	private final float[] hovers = new float[64];
	private int anchorX;
	private int anchorY;
	private int x;
	private int y;
	private int width;
	private boolean open;
	private int confirming = -1;
	private long confirmAt;

	boolean open() {
		return open;
	}

	/** Opens the menu with its top right corner (or top left, where there is no room) at a point. */
	void open(int anchorX, int anchorY, List<Item> items) {
		this.items.clear();
		this.items.addAll(items.subList(0, Math.min(items.size(), hovers.length)));
		this.anchorX = anchorX;
		this.anchorY = anchorY;
		open = true;
		confirming = -1;
	}

	/** Shows another list in the same place: the colors or groups of an item that leads further. */
	void replace(List<Item> items) {
		open(anchorX, anchorY, items);
	}

	/** Draws the "⋯" of a menu button: three small dots around a middle. */
	static void drawDots(Ui ui, float centerX, float centerY, int color) {
		for (int i = -1; i <= 1; i++) {
			ui.discAt(centerX + i * 3.5f, centerY, 2.2f, color);
		}
	}

	void close() {
		open = false;
		confirming = -1;
	}

	/** @return what a dangerous item says while it waits for its second click: "Click again to delete", by the first word of its text */
	static String confirmText(String text) {
		String action = text.strip();
		int space = action.indexOf(' ');
		return "Click again to " + (space < 0 ? action : action.substring(0, space)).toLowerCase(Locale.ROOT);
	}

	/**
	 * @param measure the width of a text as the items are drawn
	 * @return the menu's width: as wide as its widest item in any state, also the text that takes an item's place while it asks for a second
	 *         click, so nothing ever runs over the box
	 */
	static int width(List<Item> items, java.util.function.ToIntFunction<String> measure) {
		int width = MIN_WIDTH;
		for (Item item : items) {
			int text = measure.applyAsInt(item.text());
			if (item.danger()) {
				text = Math.max(text, measure.applyAsInt(confirmText(item.text())));
			}
			width = Math.max(width, text + 2 * PAD + TEXT_ROOM + (item.dotRgb() >= 0 ? DOT + 6 : 0));
		}
		return width;
	}

	void render(Ui ui, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		if (!open) {
			return;
		}
		width = width(items, text -> (int) Math.ceil(ui.widthScaled(text, TextScale.BODY)));
		int height = items.size() * ITEM_HEIGHT + 2 * PAD;
		x = Math.max(2, Math.min(anchorX - width, screenWidth - width - 2));
		y = anchorY + height > screenHeight - 2 ? Math.max(2, anchorY - height) : anchorY;
		ui.shadow(x, y, width, height, Radius.MD, 6, 0.3f);
		ui.rounded(x, y, width, height, Radius.MD, ui.palette.popup());
		ui.ring(x, y, width, height, Radius.MD, 1, ui.palette.popupBorder());
		if (confirming >= 0 && System.currentTimeMillis() - confirmAt > CONFIRM_MILLIS) {
			confirming = -1;
		}
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			int top = y + PAD + i * ITEM_HEIGHT;
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ITEM_HEIGHT;
			hovers[i] = ui.approach(hovers[i], over ? 1f : 0f, Ui.HOVER_RATE);
			if (hovers[i] > 0f) {
				ui.rounded(x + 3, top, width - 6, ITEM_HEIGHT, Radius.XS, Ui.withOpacity(item.danger() ? 0x33FF453A : ui.palette.hover(), hovers[i]));
			}
			int textX = x + PAD + 6;
			if (item.dotRgb() >= 0) {
				ui.discAt(textX + DOT / 2f, top + ITEM_HEIGHT / 2f, DOT, 0xFF000000 | item.dotRgb());
				textX += DOT + 6;
			}
			String text = i == confirming ? confirmText(item.text()) : item.text();
			ui.textAtScaled(text, textX, top + ITEM_HEIGHT / 2f - 4f, TextScale.BODY, item.danger() ? 0xFFFF6B5E : ui.palette.text());
		}
	}

	/** @return whether the click was the menu's: on an item, or anywhere else, which closes it */
	boolean click(double mouseX, double mouseY) {
		if (!open) {
			return false;
		}
		for (int i = 0; i < items.size(); i++) {
			int top = y + PAD + i * ITEM_HEIGHT;
			if (mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ITEM_HEIGHT) {
				Item item = items.get(i);
				if (item.danger() && confirming != i) {
					confirming = i;
					confirmAt = System.currentTimeMillis();
					UiSounds.tick(0.5f);
					return true;
				}
				close();
				UiSounds.click();
				item.action().run();
				return true;
			}
		}
		close();
		return true;
	}
}
