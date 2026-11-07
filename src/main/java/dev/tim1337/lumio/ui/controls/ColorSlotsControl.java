package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.config.setting.ColorSlots;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * One to three colors as round swatches in a row, with a dashed "+" to add one, for the Colored theme's colors and the glow colors. A click on a swatch opens the color picker, a right click removes the color at once and
 * the others slide left; the last color stays. Delete removes the color whose picker is open.
 */
public class ColorSlotsControl extends Control {
	private static final int SWATCH = 18;
	private static final int MOST_SHOWN = 8;
	private static final int GAP = 6;
	private static final int HEIGHT = 20;
	private static final int PLUS_INSET = 4;
	private static final int POPUP_WIDTH = 210;
	private static final int POPUP_PAD = 16;
	private static final int REMOVE_HEIGHT = 20;
	private static final int REMOVE_GAP = 10;
	/** The slot of the Remove button among the hovers of this control, after the swatches. */
	private static final int REMOVE_HOVER = 8;
	private static final String TIP_REMOVE = "Right-click to remove";
	private static final String TIP_LAST = "The last color stays";
	private static final String TIP_ADD = "Add a color";
	private static final float GONE_RATE = 9f;

	private final ColorSlots setting;
	private final Runnable onChanged;
	private ColorControl picker;
	private final PopupPlace place = new PopupPlace();
	private final Spring unfold = Spring.smooth(Spring.SNAPPY);
	/** The color whose picker is open, or -1. */
	private int open = -1;
	private int popupX;
	private int removeY;
	/** How far each swatch and the "+" now are from the row's right end: they slide to their places. */
	private final Spring[] places = new Spring[MOST_SHOWN];
	private boolean placed;
	/** The color that was just removed, shrinking away where it was. */
	private int goneRgb;
	private float goneDistance;
	private float goneAmount;

	public ColorSlotsControl(ColorSlots setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
		for (int i = 0; i < places.length; i++) {
			places[i] = Spring.smooth(Spring.SNAPPY);
		}
	}

	/** @return how far a place in the row is from its right end */
	private int distanceOf(int index) {
		return rowWidth() - index * (SWATCH + GAP);
	}

	/** @return the left of what is at a place in the row, as it slides there */
	private int shownLeft(Ui ui, int index) {
		if (!placed) {
			places[index].snap(distanceOf(index));
		}
		return x + width - Math.round(places[index].follow(ui, distanceOf(index)));
	}

	@Override
	protected int measure(Ui ui, int width) {
		return HEIGHT;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		return Math.min(available, rowWidth());
	}

	private int rowWidth() {
		int shown = setting.count() + (setting.canAdd() ? 1 : 0);
		return shown * SWATCH + (shown - 1) * GAP;
	}

	/** @return where the swatch of a color lies; the row is at the right of the control, as the other rows' values are */
	private int swatchX(int index) {
		return x + width - rowWidth() + index * (SWATCH + GAP);
	}

	private int swatchY() {
		return y + (height - SWATCH) / 2;
	}

	/** @return the swatch under the mouse: a color's, the number of colors for the "+", or -1 */
	private int swatchAt(double mouseX, double mouseY) {
		if (mouseY < swatchY() || mouseY >= swatchY() + SWATCH) {
			return -1;
		}
		int shown = setting.count() + (setting.canAdd() ? 1 : 0);
		for (int i = 0; i < shown; i++) {
			if (mouseX >= swatchX(i) && mouseX < swatchX(i) + SWATCH) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int at = swatchAt(mouseX, mouseY);
		int top = swatchY();
		float radius = Radius.pill(SWATCH);
		if (goneAmount > 0.01f) {
			goneAmount = ui.approach(goneAmount, 0f, GONE_RATE);
			int size = Math.max(2, Math.round(SWATCH * goneAmount));
			int left = x + width - Math.round(goneDistance) + (SWATCH - size) / 2;
			ui.rounded(left, top + (SWATCH - size) / 2, size, size, size / 2f, Ui.withOpacity(0xFF000000 | goneRgb, goneAmount));
		}
		for (int i = 0; i < setting.count(); i++) {
			int left = shownLeft(ui, i);
			float hover = hover(ui, i, at == i || open == i);
			ui.rounded(left, top, SWATCH, SWATCH, radius, 0xFF000000 | setting.slot(i).get());
			ui.ring(left, top, SWATCH, SWATCH, radius, 1, Ui.mix(palette.controlBorder(), 0xFFFFFFFF, open == i ? 1f : 0.2f + 0.6f * hover));
		}
		if (setting.canAdd()) {
			int left = shownLeft(ui, setting.count());
			float hover = hover(ui, setting.count(), at == setting.count());
			if (hover > 0.01f) {
				ui.rounded(left, top, SWATCH, SWATCH, radius, Ui.withOpacity(palette.hover(), hover));
			}
			ui.dashedRounded(left, top, SWATCH, SWATCH, radius, palette.controlBorder());
			ui.icon("plus", left + PLUS_INSET, top + PLUS_INSET, SWATCH - 2 * PLUS_INSET, palette.text());
		}
		placed = true;
		if (open < 0 && at >= 0) {
			showFullText(at == setting.count() ? TIP_ADD : setting.canRemove() ? TIP_REMOVE : TIP_LAST);
		}
	}

	/** Removes a color at once: it shrinks away, and the ones after it take their springs along, so they slide from where they are. */
	private void removeColor(int index) {
		goneRgb = setting.slot(index).get() & 0xFFFFFF;
		goneDistance = distanceOf(index);
		goneAmount = 1f;
		Spring first = places[index];
		int shown = setting.count() + (setting.canAdd() ? 1 : 0);
		for (int i = index; i + 1 < Math.max(shown, 1) && i + 1 < places.length; i++) {
			places[i] = places[i + 1];
		}
		places[Math.min(shown, places.length) - 1] = first;
		setting.remove(index);
		close();
		UiSounds.click();
		onChanged.run();
	}

	private int popupHeight() {
		return picker.height() + 2 * POPUP_PAD + (setting.canRemove() ? REMOVE_GAP + REMOVE_HEIGHT : 0);
	}

	@Override
	public void renderOverlay(Ui ui, int mouseX, int mouseY) {
		if (open < 0) {
			return;
		}
		Palette palette = ui.palette;
		picker.place(ui, 0, 0, POPUP_WIDTH - 2 * POPUP_PAD);
		place.place(ui, y, height, popupHeight());
		popupX = x + width - POPUP_WIDTH;
		int popupY = place.top;
		int popupH = place.height;
		float grown = unfold.follow(ui, 1f);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		float originY = popupY > y ? popupY : popupY + popupH;
		pose.translate(popupX + POPUP_WIDTH, originY);
		pose.scale(0.92f + 0.08f * grown, 0.6f + 0.4f * grown);
		pose.translate(-(popupX + POPUP_WIDTH), -originY);
		ui.shadow(popupX, popupY, POPUP_WIDTH, popupH, Radius.LG, 10, 0.5f * Math.min(1f, grown));
		ui.rounded(popupX, popupY, POPUP_WIDTH, popupH, Radius.LG, palette.popup());
		ui.ring(popupX, popupY, POPUP_WIDTH, popupH, Radius.LG, 1, palette.popupBorder());
		picker.place(ui, popupX + POPUP_PAD, popupY + POPUP_PAD, POPUP_WIDTH - 2 * POPUP_PAD);
		picker.render(ui, mouseX, mouseY);
		if (setting.canRemove()) {
			removeY = popupY + POPUP_PAD + picker.height() + REMOVE_GAP;
			float hover = hover(ui, REMOVE_HOVER, overRemove(mouseX, mouseY));
			int left = popupX + POPUP_PAD;
			int wide = POPUP_WIDTH - 2 * POPUP_PAD;
			ui.rounded(left, removeY, wide, REMOVE_HEIGHT, Radius.SM, Ui.mix(palette.control(), palette.hover(), hover));
			ui.ring(left, removeY, wide, REMOVE_HEIGHT, Radius.SM, 1, palette.controlBorder());
			ui.textCentered("Remove", left + wide / 2, removeY + REMOVE_HEIGHT / 2, palette.text());
		}
		pose.popMatrix();
	}

	private boolean overRemove(double mouseX, double mouseY) {
		return setting.canRemove() && mouseX >= popupX + POPUP_PAD && mouseX < popupX + POPUP_WIDTH - POPUP_PAD && mouseY >= removeY && mouseY < removeY + REMOVE_HEIGHT;
	}

	@Override
	public boolean isOverlayOpen() {
		return open >= 0;
	}

	private boolean inPopup(double mouseX, double mouseY) {
		return place.contains(mouseX, mouseY, popupX, POPUP_WIDTH);
	}

	private void openPicker(int index) {
		open = index;
		picker = setting.slot(index).createPicker(onChanged);
		unfold.snap(0f);
	}

	private void close() {
		open = -1;
		if (picker != null) {
			picker.loseFocus();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (open >= 0) {
			if (inPopup(mouseX, mouseY)) {
				if (button == ScreenInput.LEFT && overRemove(mouseX, mouseY)) {
					removeColor(open);
					return true;
				}
				picker.mouseClicked(mouseX, mouseY, button);
				return true;
			}
			int was = open;
			close();
			// another swatch opens right away; the same one closes
			int at = swatchAt(mouseX, mouseY);
			if (at >= 0 && at < setting.count() && button == ScreenInput.RIGHT) {
				rightClicked(at);
				return true;
			}
			if (at >= 0 && at != was && at < setting.count() && button == ScreenInput.LEFT) {
				openPicker(at);
				UiSounds.click();
				return true;
			}
			return contains(mouseX, mouseY);
		}
		int at = swatchAt(mouseX, mouseY);
		if (at >= 0 && at < setting.count() && button == ScreenInput.RIGHT) {
			rightClicked(at);
			return true;
		}
		if (button != ScreenInput.LEFT || at < 0) {
			return false;
		}
		UiSounds.click();
		if (at == setting.count()) {
			setting.add();
			onChanged.run();
		}
		openPicker(at);
		return true;
	}

	/** A right click removes the color; the last one stays, and says so on hover. */
	private void rightClicked(int index) {
		if (setting.canRemove()) {
			removeColor(index);
		}
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY) {
		return open >= 0 && picker.mouseDragged(mouseX, mouseY);
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		if (open >= 0) {
			picker.mouseReleased(mouseX, mouseY);
		}
	}

	@Override
	public boolean wantsKeyboard() {
		return open >= 0;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (open < 0) {
			return false;
		}
		// while a color's value is typed, Delete belongs to the text
		if (picker.wantsKeyboard()) {
			return picker.keyPressed(key, modifiers);
		}
		if (key == InputConstants.KEY_DELETE && setting.canRemove()) {
			removeColor(open);
			return true;
		}
		return picker.keyPressed(key, modifiers);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return open >= 0 && picker.charTyped(codepoint);
	}

	@Override
	public void loseFocus() {
		close();
	}
}
