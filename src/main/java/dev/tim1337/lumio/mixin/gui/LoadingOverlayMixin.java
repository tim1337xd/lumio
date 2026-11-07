package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.render.MenuWarmup;
import dev.tim1337.lumio.ui.loading.LoadingScreenView;
import dev.tim1337.lumio.ui.loading.LoadingScreens;
import dev.tim1337.lumio.ui.loading.LoadingSession;
import dev.tim1337.lumio.ui.title.StartupTransition;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;

/**
 * The game's resource loading overlay. Its behavior (when loading is done, when it calls back) stays the game's.
 * <ul>
 * <li>At game start the Mojang screen is the game's own while it loads, only its bar moves smoothly (the startup transition's), and the main
 * menu is prepared a slice per frame. When it is done, the startup transition takes over: the overlay goes away at once and the transition draws
 * the same Mojang screen over the main menu, instead of the game's fade.</li>
 * <li>Later reloads (F3 + T, changing packs, a server's pack) are drawn as the mod's loading view with the reload's real progress, and fade out on
 * their own once it is done.</li>
 * </ul>
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private ReloadInstance reload;

	@Shadow
	@Final
	private boolean fadeIn;

	@Shadow
	private float currentProgress;

	@Shadow
	private long fadeOutStart;

	@Shadow
	private long fadeInStart;

	/** Whether this overlay is a reload after startup drawn as the loading view, decided once per frame. */
	@Unique
	private boolean lumio$view;

	/** How long a reload's loading view takes to come in, in milliseconds. */
	@Unique
	private static final float LUMIO$FADE_IN_MILLIS = 300f;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void lumio$frame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		lumio$view = fadeIn && LoadingScreens.enhanced();
		if (fadeIn) {
			MenuWarmup.step();
		} else {
			StartupTransition.loadingFrame(reload.getActualProgress());
		}
	}

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private boolean lumio$background(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		return !lumio$view;
	}

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private boolean lumio$logo(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
										 int a, int b, int c, int d, int e, int f, int g) {
		return !lumio$view;
	}

	@Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
	private void lumio$bar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float fade, CallbackInfo ci) {
		if (lumio$view) {
			ci.cancel();
		} else if (!fadeIn && StartupTransition.drawsBar()) {
			StartupTransition.drawBar(graphics, x0, y0, x1, y1, fade);
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lumio$after(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (fadeOutStart != -1L) {
			// done: the game would fade now; the startup transition or the loading view does instead, from the next frame on
			if ((!fadeIn && StartupTransition.takeOver()) || lumio$view) {
				minecraft.gui.setOverlay(null);
			}
			return;
		}
		if (lumio$view) {
			LoadingSession.report(LoadingSession.Flow.RELOAD, null, null, currentProgress);
			float shown = fadeInStart == -1L ? 0f : Math.min(1f, (Util.getMillis() - fadeInStart) / LUMIO$FADE_IN_MILLIS);
			LoadingScreenView.draw(graphics, shown);
		}
	}
}
