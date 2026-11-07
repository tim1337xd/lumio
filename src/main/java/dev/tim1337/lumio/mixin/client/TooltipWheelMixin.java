package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ui.tooltip.TooltipPainter;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the wheel to a long tooltip while one is shown, before any screen sees it, so that what lies behind it does not scroll as well. */
@Mixin(MouseHandler.class)
public abstract class TooltipWheelMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void lumio$wheelOnTooltip(long window, double xOffset, double yOffset, CallbackInfo info) {
		if (TooltipPainter.wheel(yOffset)) {
			info.cancel();
		}
	}
}
