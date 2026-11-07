package dev.tim1337.lumio.render;

import dev.tim1337.lumio.ui.FeaturePreview;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.api.experimental.util.Logs;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.Minecraft;

/**
 * The live game world behind a settings page's preview: in a world the real view, as it is before the menu and the HUD are drawn over it; on the title screen the menu world. Faded, so that what the preview shows stands out. A copy of the screen is only
 * made while a preview is shown, and given back soon after; without a picture yet the preview has its painted backdrop.
 */
public final class WorldBackdrop {
	/** How dark the fade over the world is. */
	private static final int FADE = 0x4D000000;
	/** After this long without a preview the copy is given back. */
	private static final long KEEP_NANOS = 2_000_000_000L;

	private static long wantedAt = Long.MIN_VALUE;
	private static GpuTexture copy;
	private static GpuTextureView copyView;
	private static GpuTexture retired;
	private static GpuTextureView retiredView;
	private static int copyWidth;
	private static int copyHeight;
	private static boolean copied;
	private static boolean failed;

	private WorldBackdrop() {
	}

	/** Draws the world into a preview, with its corners rounded: row by row in the corners, since a picture has no corners of its own. */
	public static void draw(Ui ui, int x, int y, int width, int height, float radius) {
		wantedAt = System.nanoTime();
		GpuTextureView view = source();
		if (view == null) {
			FeaturePreview.backdrop(ui, x, y, width, height, radius);
			return;
		}
		int pictureWidth = view.getWidth(0);
		int pictureHeight = view.getHeight(0);
		// the middle of the picture, as much as fits the preview's shape, so that nothing is stretched
		float shape = width / (float) Math.max(1, height);
		float pictureShape = pictureWidth / (float) Math.max(1, pictureHeight);
		float across = shape < pictureShape ? shape / pictureShape : 1f;
		float down = shape < pictureShape ? 1f : pictureShape / shape;
		float left = (1f - across) / 2f;
		float top = (1f - down) / 2f;
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		int corner = Math.min(Math.round(radius), Math.min(width, height) / 2);
		for (int row = 0; row < corner; row++) {
			int inset = cornerInset(corner, row);
			rows(ui, view, sampler, x, y, width, height, row, 1, inset, left, top, across, down);
			rows(ui, view, sampler, x, y, width, height, height - 1 - row, 1, inset, left, top, across, down);
		}
		rows(ui, view, sampler, x, y, width, height, corner, height - 2 * corner, 0, left, top, across, down);
		ui.rounded(x, y, width, height, radius, FADE);
	}

	/** @return how far a row near the top of a rounded corner starts inside */
	static int cornerInset(int radius, int row) {
		double up = radius - row - 0.5;
		return (int) Math.ceil(radius - Math.sqrt(Math.max(0.0, (double) radius * radius - up * up)));
	}

	/** The picture's rows are upside down, as in every render target. */
	private static void rows(Ui ui, GpuTextureView view, GpuSampler sampler, int x, int y, int width, int height, int from, int count, int inset, float left, float top, float across, float down) {
		if (count <= 0 || inset * 2 >= width) {
			return;
		}
		float u0 = left + across * inset / width;
		float u1 = left + across * (width - inset) / width;
		float v0 = 1f - (top + down * from / height);
		float v1 = 1f - (top + down * (from + count) / height);
		ui.graphics.blit(view, sampler, x + inset, y + from, x + width - inset, y + from + count, u0, u1, v0, v1);
	}

	/** @return what to draw: the copy of the world, or the menu world where there is no world; null while there is neither */
	private static GpuTextureView source() {
		if (Minecraft.getInstance().level == null) {
			return MenuWorld.currentView();
		}
		return copied && !failed ? copyView : null;
	}

	/** Called before the GUI is drawn, when the screen shows the world alone and no pass of the game is open. */
	public static void capture() {
		closeRetired();
		if (failed || System.nanoTime() - wantedAt > KEEP_NANOS) {
			if (copy != null) {
				retire();
			}
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		try {
			RenderTarget main = client.gameRenderer.mainRenderTarget();
			if (copy == null || copyWidth != main.width || copyHeight != main.height) {
				if (copy != null) {
					retire();
				}
				GpuTexture source = main.getColorTexture();
				copy = RenderSystem.getDevice().createTexture("Lumio world behind a preview", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, source.getFormat(), main.width, main.height, 1, 1);
				copyView = RenderSystem.getDevice().createTextureView(copy);
				copyWidth = main.width;
				copyHeight = main.height;
			}
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), copy, 0, 0, 0, 0, 0, main.width, main.height);
			copied = true;
		} catch (RuntimeException | Error error) {
			failed = true;
			Logs.warn("The world could not be copied for the previews, they keep their painted backdrop", error);
		}
	}

	/** A texture is given back a frame later: this frame may still draw with it. */
	private static void retire() {
		retired = copy;
		retiredView = copyView;
		copy = null;
		copyView = null;
		copied = false;
	}

	private static void closeRetired() {
		if (retiredView != null) {
			retiredView.close();
			retiredView = null;
		}
		if (retired != null) {
			retired.close();
			retired = null;
		}
	}
}
