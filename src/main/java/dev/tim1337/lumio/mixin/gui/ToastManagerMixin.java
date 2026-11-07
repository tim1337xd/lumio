package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.GameToasts;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;

/**
 * The game's notices (advancements, recipes) are not drawn over the cinematic camera's film or an open settings window that reaches where they show;
 * they wait and show when it ends. While only the drawing waits, their time would run out unseen, so a settings window stops the time too.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$hideInFilm(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (ModFeatures.CINEMATIC != null && ModFeatures.CINEMATIC.hidesHud() || GameToasts.waiting()) {
			ci.cancel();
		}
	}

	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void lumio$wait(CallbackInfo ci) {
		if (GameToasts.waiting()) {
			ci.cancel();
		}
	}
}
