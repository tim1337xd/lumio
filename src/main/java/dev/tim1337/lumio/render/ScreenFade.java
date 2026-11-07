package dev.tim1337.lumio.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.mixin.accessor.GuiGraphicsExtractorAccessor;
import dev.tim1337.lumio.ui.loading.LoadingScreens;
import dev.tim1337.lumio.ui.title.StartupTransition;

import org.joml.Matrix3x2f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;

/**
 * Cross-fades between screens: when the screen changes outside a world (main menu, lists, loading screens), or a world appears after its loading
 * screen, an image of the previous screen is kept and drawn over the new one while fading out. Nothing cuts; the new screen emerges through the
 * old one.
 * <p>
 * The image is the last frame, copied on the GPU when the screen changes and drawn the way the game draws its own texture images.
 */
public final class ScreenFade {
	/** Fade duration in seconds: between two menus, and from a loading screen into the world, which is a bigger change. */
	private static final float MENU_SECONDS = 0.32f;
	private static final float WORLD_SECONDS = 0.7f;

	private static GpuTexture picture;
	private static GpuTextureView pictureView;
	private static int pictureWidth;
	private static int pictureHeight;
	private static GpuFormat pictureFormat;
	private static long startedAt;
	private static float seconds;
	private static boolean active;
	private static boolean failed;

	private ScreenFade() {
	}

	/**
	 * @return whether a screen change fades: outside a world, and from a loading screen into the world. Not for in-game screens (inventory,
	 *         chat). With the mod's loading view, the view itself carries on between loading steps and fades into the world, and a loading screen
	 *         that comes up in a world (a dimension change, a server switch) fades in from the last frame of the game.
	 */
	private static boolean fades(Screen from, Screen to) {
		Minecraft client = Minecraft.getInstance();
		if (from == to || client.gui.overlay() != null) {
			return false;
		}
		if (LoadingScreens.enhanced()) {
			if (LoadingScreens.isLoading(to)) {
				return from == null ? client.level != null : !LoadingScreens.isLoading(from);
			}
			if (to == null && LoadingScreens.isLoading(from)) {
				return false;
			}
		}
		if (to == null) {
			return loading(from);
		}
		return client.level == null && from != null;
	}

	private static boolean loading(Screen screen) {
		return screen instanceof LevelLoadingScreen || screen instanceof ConnectScreen || screen instanceof ProgressScreen || screen instanceof GenericMessageScreen;
	}

	/** Called right before the screen changes: keeps the last frame (showing the old screen) if this change fades. */
	public static void screenChanging(Screen from, Screen to) {
		if (failed || !ModFeatures.MENU.animations() || !fades(from, to)) {
			return;
		}
		// under the Mojang screen at game start nothing of the screens shows: the startup transition takes over from it, a fade would only cost
		if (StartupTransition.drawsBar() || StartupTransition.coversMenu()) {
			return;
		}
		capture(to == null ? WORLD_SECONDS : MENU_SECONDS);
	}

	/** Fades from the frame on screen now over a time in seconds, whatever comes next: skipping the startup transition. */
	public static void fadeFromNow(float duration) {
		if (!failed) {
			capture(duration);
		}
	}

	private static void capture(float duration) {
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			GpuFormat format = main.getColorTexture().getFormat();
			if (picture == null || pictureWidth != main.width || pictureHeight != main.height || pictureFormat != format) {
				close();
				picture = RenderSystem.getDevice().createTexture("Lumio screen fade", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, format,
					main.width, main.height, 1, 1);
				pictureView = RenderSystem.getDevice().createTextureView(picture);
				pictureWidth = main.width;
				pictureHeight = main.height;
				pictureFormat = format;
			}
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(main.getColorTexture(), picture, 0, 0, 0, 0, 0, main.width, main.height);
			seconds = duration;
			startedAt = System.nanoTime();
			active = true;
		} catch (RuntimeException | Error e) {
			// a GPU that cannot copy the screen gets hard cuts, as in vanilla
			failed = true;
			active = false;
			Logs.warn("The screens can not fade into each other on this graphics card", e);
		}
	}

	/** Called at the very end of GUI rendering: draws the old screen's image over everything, with its remaining opacity. */
	public static void draw(GuiGraphicsExtractor graphics) {
		if (!active || pictureView == null) {
			return;
		}
		float t = (System.nanoTime() - startedAt) / 1_000_000_000f / seconds;
		if (t >= 1f) {
			active = false;
			return;
		}
		// smootherstep easing, so neither the start nor the end of the fade is visible as a step
		float eased = t * t * t * (t * (t * 6f - 15f) + 10f);
		int alpha = Math.round((1f - eased) * 255f);
		if (alpha <= 0) {
			return;
		}
		graphics.nextStratum();
		var state = ((GuiGraphicsExtractorAccessor) graphics).lumio$state();
		state.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(pictureView,
			RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)), new Matrix3x2f(), 0, 0, graphics.guiWidth(), graphics.guiHeight(),
			0f, 1f, 1f, 0f, alpha << 24 | 0xFFFFFF, null, null));
	}

	private static void close() {
		if (pictureView != null) {
			pictureView.close();
			pictureView = null;
		}
		if (picture != null) {
			picture.close();
			picture = null;
		}
	}
}
