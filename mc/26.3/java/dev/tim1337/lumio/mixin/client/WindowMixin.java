package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

/** While borderless fullscreen is on, the fullscreen key switches it: from Minecraft 26.3 on the game itself handles that key, no longer the window. */
@Mixin(Minecraft.class)
public abstract class WindowMixin {
	@Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
	private void lumio$borderless(CallbackInfo ci) {
		if (ModFeatures.BORDERLESS != null && ModFeatures.BORDERLESS.takesFullscreenKey()) {
			ModFeatures.BORDERLESS.switchBorderless();
			ci.cancel();
		}
	}
}
