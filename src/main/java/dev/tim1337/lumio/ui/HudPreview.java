package dev.tim1337.lumio.ui;


import net.minecraft.client.Minecraft;


/**
 * Helpers for page previews that are a miniature of the game screen: the screen's aspect ratio, and everything at its real place and size,
 * multiplied by one scale. Drawing happens in screen coordinates, after the preview has scaled the pose.
 */
public final class HudPreview {

	private HudPreview() {
	}

	/** @return the height of a preview this wide, in the game screen's aspect ratio */
	public static int heightFor(int width) {
		var window = Minecraft.getInstance().getWindow();
		return Math.round(width * window.getGuiScaledHeight() / (float) Math.max(1, window.getGuiScaledWidth()));
	}

}
