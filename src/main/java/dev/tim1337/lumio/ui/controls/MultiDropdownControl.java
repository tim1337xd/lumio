package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/**
 * A multi-select dropdown: a button summarizing the selection in one line, and a list with a checkbox per entry; clicking an entry toggles it
 * without closing the list.
 */
public class MultiDropdownControl extends Control {
	private static final int ITEM = 24;

	private final MultiSelectSetting setting;
	private final Runnable onChanged;
	private boolean open;
	private final Spring unfold = Spring.smooth(Spring.SNAPPY);

	public MultiDropdownControl(MultiSelectSetting setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
	}

	private List<BooleanSetting> options() {
		return setting.options().stream().filter(BooleanSetting::isVisible).toList();
	}

	private String summary() {
		StringBuilder text = new StringBuilder();
		for (BooleanSetting option : options()) {
			if (option.get()) {
				if (!text.isEmpty()) {
					text.append(", ");
				}
				text.append(option.label().getString());
			}
		}
		return text.isEmpty() ? "None" : text.toString();
	}

	@Override
	protected int measure(Ui ui, int width) {
		return 22;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		int widest = 0;
		for (BooleanSetting option : options()) {
			widest = Math.max(widest, ui.width(option.label().getString()));
		}
		return Math.min(available, Math.max(170, widest + 70));
	}

	private int listWidth() {
		return Math.max(width, 170);
	}

	private int listHeight() {
		return options().size() * ITEM + 8;
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
		ui.textMiddle(ui.fit(summary(), width - 32), x + 9, y + height / 2, ui.palette.text());
		ui.icon("chevron", x + width - 18, y + height / 2 - 6, 12, ui.palette.textSecondary());
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		int listWidth = listWidth();
		int listX = x + width - listWidth;
		place.place(ui, y, height, listHeight());
		int listY = place.top;
		int listH = place.height;
		boolean down = listY > y;
		float grown = unfold.follow(ui, 1f);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		// the list grows from the button edge it is attached to, at its right end: down from below, or up from above
		float originY = down ? listY : listY + listH;
		pose.translate(listX + listWidth, originY);
		pose.scale(0.9f + 0.1f * grown, 0.5f + 0.5f * grown);
		pose.translate(-(listX + listWidth), -originY);
		ui.shadow(listX, listY, listWidth, listH, Radius.MD, 8, 0.45f * Math.min(1f, grown));
		ui.rounded(listX, listY, listWidth, listH, Radius.MD, ui.palette.popup());
		ui.ring(listX, listY, listWidth, listH, Radius.MD, 1, ui.palette.popupBorder());
		List<BooleanSetting> options = options();
		// lists taller than the available room scroll; clip to the list
		ui.graphics.enableScissor(listX, listY + 2, listX + listWidth, listY + listH - 2);
		int first = listY + 4 - Math.round(place.scroll);
		for (int i = 0; i < options.size(); i++) {
			int itemY = first + i * ITEM;
			if (itemY + ITEM < listY || itemY > listY + listH) {
				continue;
			}
			boolean hovered = mouseY >= listY && mouseY < listY + listH && mouseX >= listX + 4 && mouseX < listX + listWidth - 4 && mouseY >= itemY && mouseY < itemY + ITEM;
			if (hovered) {
				ui.rounded(listX + 4, itemY, listWidth - 8, ITEM, Radius.SM, ui.palette.hover());
			}
			CheckboxControl.draw(ui, listX + 12, itemY + (ITEM - 14) / 2, options.get(i).get(), hovered);
			ui.textMiddle(options.get(i).label().getString(), listX + 34, itemY + ITEM / 2, ui.palette.glass() ? 0xFFFFFFFF : ui.palette.text());
		}
		ui.graphics.disableScissor();
		if (place.scrolls()) {
			// thin scrollbar at the right edge showing that there is more and where the view is
			float part = (float) listH / place.full;
			int barH = Math.max(12, Math.round((listH - 8) * part));
			int barY = listY + 4 + Math.round((listH - 8 - barH) * (place.scroll / Math.max(1f, place.full - listH)));
			ui.rounded(listX + listWidth - 5, barY, 2, barH, Radius.pill(2), Ui.withOpacity(ui.palette.text(), 0.35f));
		}
		pose.popMatrix();
	}

	/** Current list placement: below or above the button, and its scroll offset. */
	private final PopupPlace place = new PopupPlace();

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
			if (place.contains(mouseX, mouseY, listX, listWidth)) {
				int index = (int) Math.floor((mouseY - place.top - 4 + place.scroll) / ITEM);
				List<BooleanSetting> options = options();
				if (index >= 0 && index < options.size()) {
					BooleanSetting option = options.get(index);
					option.set(!option.get());
					UiSounds.toggle(option.get());
					onChanged.run();
				}
				return true; // keep the list open so several entries can be toggled
			}
			open = false;
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
