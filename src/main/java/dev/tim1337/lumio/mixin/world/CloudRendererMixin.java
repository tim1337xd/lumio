package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.CloudRenderer;

/** Hides vanilla clouds under mod skies they do not fit. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	/** Skips the vanilla clouds where a mod sky is shown that they do not fit into. */
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void lumio$hideClouds(CallbackInfo ci) {
		if (ModFeatures.SKY.hidesClouds()) {
			ci.cancel();
		}
	}
}
