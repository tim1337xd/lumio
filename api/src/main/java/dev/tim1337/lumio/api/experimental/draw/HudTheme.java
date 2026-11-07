package dev.tim1337.lumio.api.experimental.draw;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.spi.Backend;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The look of the HUD panels as the player set it: their text colors and the glass they are drawn on. */
public final class HudTheme {
	private HudTheme() {
	}

	/** @return the main text color of panels */
	public static int text() {
		return Backend.get().hud().text();
	}

	/** @return the color of secondary text (labels, units) */
	public static int secondary() {
		return Backend.get().hud().secondary();
	}

	/** @return the color of empty slots and tracks */
	public static int slot() {
		return Backend.get().hud().slot();
	}

	/** @return how opaque the panels' glass is, from 0 to 1 */
	public static float opacity() {
		return Backend.get().hud().opacity();
	}

	/** @return the color changed just enough to be readable on the panels' glass, as 0xRRGGBB */
	public static int readable(int rgb) {
		return Backend.get().hud().readable(rgb);
	}

	/** @return the color of a thin line between a panel's parts, at an opacity, visible on every theme */
	public static int line(float opacity) {
		return Backend.get().hud().line(opacity);
	}

	/**
	 * Blurs all that lies behind what is drawn next, for an overlay over the whole screen.
	 *
	 * @param blur how much, from 0 to 1
	 * @param fade how far it has faded in, from 0 to 1
	 * @return whether it was drawn; if not, the graphics settings leave no blur to use
	 */
	public static boolean blurBehind(Canvas canvas, int width, int height, float blur, float fade) {
		return Backend.get().hud().blurBehind(canvas, width, height, blur, fade);
	}

	/** Draws a panel's glass. */
	public static void panel(Canvas canvas, int x, int y, int width, int height, float radius, float opacity) {
		Backend.get().hud().panel(canvas, x, y, width, height, radius, opacity);
	}

	/** Draws a panel's glass, with the glow around it when glow is on. */
	public static void panel(Canvas canvas, int x, int y, int width, int height, float radius, float opacity, boolean glow) {
		Backend.get().hud().panel(canvas, x, y, width, height, radius, opacity, glow);
	}

	/** Draws something in a theme of its own, such as a label in the world. */
	public static void withLook(PanelLook look, Runnable draw) {
		Backend.get().hud().withLook(look, draw);
	}

	/** Draws a live preview in a settings card, over the backdrop the previews have. */
	@FunctionalInterface
	public interface PreviewDrawer {
		void draw(GuiGraphicsExtractor graphics, Canvas canvas, int x, int y, int width, int height);
	}

	/** @return a setting that shows a live preview, as tall as asked for in GUI pixels */
	public static AddonSetting<Void> preview(String key, Component label, int height, PreviewDrawer drawer) {
		return Backend.get().hud().preview(key, label, height, drawer);
	}

	/** What the main mod provides for {@link HudTheme}. */
	public interface Provider {
		int line(float opacity);

		int text();

		int secondary();

		int slot();

		float opacity();

		int readable(int rgb);

		boolean blurBehind(Canvas canvas, int width, int height, float blur, float fade);

		void panel(Canvas canvas, int x, int y, int width, int height, float radius, float opacity);

		void panel(Canvas canvas, int x, int y, int width, int height, float radius, float opacity, boolean glow);

		void withLook(PanelLook look, Runnable draw);

		AddonSetting<Void> preview(String key, Component label, int height, PreviewDrawer drawer);
	}
}
