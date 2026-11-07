package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.menu.MenuFeature;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A dedicated screen for positioning notifications by dragging two sample notifications: the HUD editor is for permanent elements, while
 * notifications only appear briefly. Snaps to the edges and the center; Done (or Escape) returns to the settings.
 */
public class NotificationEditScreen extends Screen {
	private static final int SNAP = 6;

	private final Screen parent;
	private boolean dragging;
	private double grabX;
	private double grabY;
	/** Notification position as stored in the settings: offsets from the right edge and from the top. */
	private float right;
	private float top;
	private int boxWidth;
	private int boxHeight;
	private int doneX;
	private int doneY;
	private static final int DONE_W = 90;
	private static final int DONE_H = 24;

	public NotificationEditScreen(Screen parent) {
		super(Component.literal("Notifications"));
		this.parent = parent;
		right = ModFeatures.NOTIFICATIONS.fromRight();
		top = ModFeatures.NOTIFICATIONS.fromTop();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private float scale() {
		return ModFeatures.NOTIFICATIONS.scale();
	}

	private float left() {
		return width - right - boxWidth;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, font, HudStyle.palette(), 0xFF000000 | MenuFeature.accentRgb(), 0f, true);
		graphics.fill(0, 0, width, height, 0x66000000);
		// two sample notifications; their size and position define the drag box
		float scale = scale();
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(left(), top);
		pose.scale(scale, scale);
		int first = Toasts.drawSample(ui, graphics, "Notification", "Drag me to where the notices are to be.");
		pose.translate(0, first + 8);
		int second = Toasts.drawSample(ui, graphics, "Pests", "Pests can spawn in 5 seconds");
		pose.popMatrix();
		boxWidth = Math.round(Toasts.width() * scale);
		boxHeight = Math.round((first + 8 + second) * scale);
		boolean over = mouseX >= left() && mouseX < left() + boxWidth && mouseY >= top && mouseY < top + boxHeight;
		ui.ring(Math.round(left()) - 3, Math.round(top) - 3, boxWidth + 6, boxHeight + 6, Radius.XXL, 1, Ui.withOpacity(ui.accent, dragging || over ? 0.9f : 0.45f));

		// instructions and Done, centered, away from where notifications usually are
		String hint = "Drag the notices to where they are to be  ·  Escape or Done to keep it";
		ui.textCentered(hint, width / 2, height / 2 + 22, 0xFFFFFFFF);
		doneX = width / 2 - DONE_W / 2;
		doneY = height / 2 + 36;
		boolean overDone = mouseX >= doneX && mouseX < doneX + DONE_W && mouseY >= doneY && mouseY < doneY + DONE_H;
		ui.rounded(doneX, doneY, DONE_W, DONE_H, DONE_H / 2f, Ui.mix(ui.accent, 0xFFFFFFFF, overDone ? 0.14f : 0f));
		ui.textCentered("Done", doneX + DONE_W / 2, doneY + DONE_H / 2, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (ScreenInput.button(event) == 0 && mx >= doneX && mx < doneX + DONE_W && my >= doneY && my < doneY + DONE_H) {
			UiSounds.click();
			onClose();
			return true;
		}
		if (ScreenInput.button(event) == 0 && mx >= left() && mx < left() + boxWidth && my >= top && my < top + boxHeight) {
			dragging = true;
			grabX = mx - left();
			grabY = my - top;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!dragging) {
			return false;
		}
		float newLeft = (float) Mth.clamp(event.x() - grabX, 0, width - boxWidth);
		float newTop = (float) Mth.clamp(event.y() - grabY, 0, height - boxHeight);
		// snap to nearby edges or the center
		if (newLeft < SNAP + 10) {
			newLeft = 10;
		} else if (width - newLeft - boxWidth < SNAP + 10) {
			newLeft = width - boxWidth - 10;
		} else if (Math.abs(newLeft + boxWidth / 2f - width / 2f) < SNAP) {
			newLeft = width / 2f - boxWidth / 2f;
		}
		if (newTop < SNAP + 10) {
			newTop = 10;
		} else if (height - newTop - boxHeight < SNAP + 10) {
			newTop = height - boxHeight - 10;
		}
		right = width - newLeft - boxWidth;
		top = newTop;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			ModFeatures.NOTIFICATIONS.place(Math.round(right), Math.round(top));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		ModFeatures.NOTIFICATIONS.place(Math.round(right), Math.round(top));
		minecraft.gui.setScreen(parent);
	}
}
