package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.debug.DebugPass;
import dev.tim1337.lumio.render.EchoFeedback;
import dev.tim1337.lumio.render.GlassPanes;
import dev.tim1337.lumio.render.HighlightOutlines;
import dev.tim1337.lumio.render.LyricOverlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/** Frame hooks for the glass panes and Radiance's Echo, hiding the hand in cinematic mode, and the portal's wobble (Camera). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** The hand is not rendered in front of the cinematic camera, which is not at the player's eyes. */
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void lumio$noHand(CallbackInfo ci) {
		if (ModFeatures.CINEMATIC != null && ModFeatures.CINEMATIC.active()) {
			ci.cancel();
		}
	}

	/** The hand pass begins: Radiance's Echo needs its field of view, to find the item between two frames on the screen. */
	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	private void lumio$handFov(CallbackInfo ci, @Local(argsOnly = true) CameraRenderState camera) {
		EchoFeedback.handFov(camera.hudFov);
	}

	/** The hand pass is done: its depth is the silhouette of the item and the hands, which Radiance's Echo glows after. */
	@Inject(method = "renderItemInHand", at = @At("RETURN"))
	private void lumio$echo(CallbackInfo ci) {
		EchoFeedback.render();
	}

	/**
	 * The wobble of standing in a Nether portal, when the Camera module hides the portal: its strength reads as 0. The game takes the larger of it
	 * and nausea from an effect, so that one still wobbles.
	 */
	//#if 26.3
	//$ @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;portalEffectIntensity:F"))
	//$ private float lumio$portalWobble(float intensity) {
	//$ 	return ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hidePortal() ? 0f : intensity;
	//$ }
	//#else
	@ModifyExpressionValue(method = "renderLevel", at = {
		@At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;portalEffectIntensity:F"),
		@At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;oPortalEffectIntensity:F")})
	private float lumio$portalWobble(float intensity) {
		return ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hidePortal() ? 0f : intensity;
	}
	//#endif

	/** New frame: drops the glass panes of the previous one. */
	@Inject(method = "extract", at = @At("HEAD"))
	private void lumio$newFrame(DeltaTracker tracker, boolean tick, CallbackInfo ci) {
		GlassPanes.newFrame();
		HighlightOutlines.newFrame();
		dev.tim1337.lumio.ui.tooltip.TooltipPainter.newFrame();
	}

	/** Before rendering the frame, uploads what the glass panes need to the GPU. */
	@Inject(method = "render", at = @At("HEAD"))
	private void lumio$beforeRender(CallbackInfo ci) {
		GlassPanes.beforeRender();
		DebugHooks.frameBegin();
		DebugHooks.passBegin(DebugPass.FRAME);
	}

	/** The whole frame's GPU time, for the Debug overlay; returns at once while it is off. */
	@Inject(method = "render", at = @At("RETURN"))
	private void lumio$afterRender(CallbackInfo ci) {
		DebugHooks.passEnd(DebugPass.FRAME);
	}

	/** The world and the hand are done, the HUD comes next: the vignette around the lyrics goes on here, while the depth still holds the hand. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void lumio$lyricVignette(CallbackInfo ci) {
		LyricOverlay.finish(Minecraft.getInstance().gameRenderer.mainRenderTarget());
	}

	/** Replaces the vanilla blur at the stratum split with the glass blur when a glass pane requested the split. */
	@Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
	private void lumio$glassBlur(CallbackInfo ci) {
		if (GlassPanes.wantsBackdrop()) {
			GlassPanes.capture();
			ci.cancel();
			return;
		}
		GlassPanes.gameBlurBegin();
	}

	/** The game's own blur is done: Auto has timed it, when the menu asked for it. */
	@Inject(method = "processBlurEffect", at = @At("RETURN"))
	private void lumio$gameBlurTimed(CallbackInfo ci) {
		GlassPanes.gameBlurEnd();
	}
}
