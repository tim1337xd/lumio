package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.render.EchoFeedback;
import dev.tim1337.lumio.render.GlassPanes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.llamalad7.mixinextras.sugar.Local;

/** Frame hooks for the glass panes and Radiance's Echo, and hiding the hand in cinematic mode. */
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

	/** New frame: drops the glass panes of the previous one. */
	@Inject(method = "extract", at = @At("HEAD"))
	private void lumio$newFrame(DeltaTracker tracker, boolean tick, CallbackInfo ci) {
		GlassPanes.newFrame();
	}

	/** Before rendering the frame, uploads what the glass panes need to the GPU. */
	@Inject(method = "render", at = @At("HEAD"))
	private void lumio$beforeRender(CallbackInfo ci) {
		GlassPanes.beforeRender();
	}

	/** Replaces the vanilla blur at the stratum split with the glass blur when a glass pane requested the split. */
	@Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
	private void lumio$glassBlur(CallbackInfo ci) {
		if (GlassPanes.wantsBackdrop()) {
			GlassPanes.capture();
			ci.cancel();
		}
	}
}
