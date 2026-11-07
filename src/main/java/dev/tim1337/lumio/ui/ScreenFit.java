package dev.tim1337.lumio.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Shows a screen laid out for a minimum size on a smaller screen, such as one produced by a large GUI scale: the layout is computed for a
 * virtual screen enlarged by the inverse of the downscale factor, drawn scaled down, and mouse coordinates are mapped back to the layout.
 */
public final class ScreenFit {
	public float fit = 1f;
	private int realWidth;
	private int realHeight;

	/** Switches the screen to the layout size and computes the downscale factor. */
	public void begin(Screen screen, int minWidth, int minHeight) {
		var window = Minecraft.getInstance().getWindow();
		realWidth = window.getGuiScaledWidth();
		realHeight = window.getGuiScaledHeight();
		float wanted = Math.min(1f, Math.min(realWidth / (float) minWidth, realHeight / (float) minHeight));
		// in steps, so text is rendered for the size it is drawn at
		fit = Math.max(0.4f, (float) Math.floor(wanted * 20f) / 20f);
		screen.width = fit < 1f ? Math.round(realWidth / fit) : realWidth;
		screen.height = fit < 1f ? Math.round(realHeight / fit) : realHeight;
	}

	/** Restores the real screen size. */
	public void end(Screen screen) {
		screen.width = realWidth;
		screen.height = realHeight;
	}

	public MouseButtonEvent event(MouseButtonEvent event) {
		return fit >= 1f ? event : new MouseButtonEvent(event.x() / fit, event.y() / fit, event.buttonInfo());
	}
}
