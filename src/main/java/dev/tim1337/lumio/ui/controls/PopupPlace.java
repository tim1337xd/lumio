package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.ui.Ui;

import org.joml.Vector2f;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Placement of a dropdown list: below its button if it fits on screen, otherwise above if it fits there, otherwise on the side with more room,
 * limited to that room and scrollable with the wheel. Previously long lists always opened downwards and ran off the screen.
 */
final class PopupPlace {
	/** Margin kept free at the screen edge. */
	private static final int MARGIN = 6;
	private static final int GAP = 4;

	/** List top and height in control coordinates, the full content height, and the scroll offset. */
	int top;
	int height;
	int full;
	float scroll;

	/** Computes the placement of a list of height {@code wanted} attached to a button, as the button is currently drawn (in a possibly zoomed window). */
	void place(Ui ui, int buttonY, int buttonHeight, int wanted) {
		var pose = ui.graphics.pose();
		Vector2f under = pose.transformPosition(0f, buttonY + buttonHeight + GAP, new Vector2f());
		Vector2f over = pose.transformPosition(0f, buttonY - GAP, new Vector2f());
		float scale = Math.max(0.01f, Math.abs(pose.m11()));
		int screen = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int below = (int) ((screen - MARGIN - under.y) / scale);
		int above = (int) ((over.y - MARGIN) / scale);
		full = wanted;
		if (wanted <= below || below >= above) {
			height = Math.max(Math.min(wanted, below), Math.min(wanted, 40));
			top = buttonY + buttonHeight + GAP;
		} else {
			height = Math.min(wanted, above);
			top = buttonY - GAP - height;
		}
		scroll = Mth.clamp(scroll, 0f, Math.max(0, full - height));
	}

	/** @return whether the list is shorter than its content and therefore scrolls */
	boolean scrolls() {
		return full > height;
	}

	void scrollBy(double amount, int step) {
		scroll = Mth.clamp(scroll - (float) amount * step, 0f, Math.max(0, full - height));
	}

	boolean contains(double mouseX, double mouseY, int left, int width) {
		return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
	}
}
