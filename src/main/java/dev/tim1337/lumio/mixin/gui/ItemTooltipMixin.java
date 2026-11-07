package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.tooltip.TooltipPainter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hands the tooltips of items to the painter. At the head of the method that draws a tooltip, because that is where all of them come by, also those of other
 * screens and mods, with the components the game made: they stay as they are. Any other tooltip returns after a cheap check, and the game draws it.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemTooltipMixin {
	@Inject(method = "extractDeferredElements", at = @At("TAIL"))
	private void lumio$endOfTooltips(int mouseX, int mouseY, float partialTick, CallbackInfo info) {
		TooltipPainter.endFrame();
	}

	//#if 26.3
	//$ @Inject(method = "tooltip", at = @At("HEAD"), cancellable = true)
	//$ private void lumio$itemTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, Identifier style, boolean flag, CallbackInfo info) {
	//$ 	if (TooltipPainter.draw((GuiGraphicsExtractor) (Object) this, font, lines, x, y, positioner, style)) {
	//$ 		info.cancel();
	//$ 	}
	//$ }
	//#else
	@Inject(method = "tooltip", at = @At("HEAD"), cancellable = true)
	private void lumio$itemTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, Identifier style, CallbackInfo info) {
		if (TooltipPainter.draw((GuiGraphicsExtractor) (Object) this, font, lines, x, y, positioner, style)) {
			info.cancel();
		}
	}
	//#endif
}
