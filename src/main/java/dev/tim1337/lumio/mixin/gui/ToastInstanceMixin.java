package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.GameToasts;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Draws a toast in the mod's style where the game asks it to draw itself, after the game has placed it, so it slides as usual. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public abstract class ToastInstanceMixin {
	@Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/toasts/Toast;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;J)V"))
	private void lumio$styled(Toast toast, GuiGraphicsExtractor graphics, Font font, long time) {
		if (!GameToasts.draw(toast, graphics)) {
			toast.extractRenderState(graphics, font, time);
		}
	}
}
