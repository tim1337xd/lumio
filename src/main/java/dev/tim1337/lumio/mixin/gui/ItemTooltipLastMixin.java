package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.tooltip.TooltipPainter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Gui;

/** An item's tooltip goes on last, when the screen and every panel drawn after it are done. */
@Mixin(Gui.class)
public abstract class ItemTooltipLastMixin {
	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void lumio$tooltipLast(CallbackInfo info) {
		TooltipPainter.drawPending();
	}
}
