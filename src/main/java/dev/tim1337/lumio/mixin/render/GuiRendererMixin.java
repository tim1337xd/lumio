package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.render.WorldBackdrop;

import net.minecraft.client.gui.render.GuiRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Just before the GUI is drawn the screen holds the world alone, without the menu and the HUD: the moment to copy it for a preview. It exits at once while no preview is shown. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void lumio$worldForPreviews(CallbackInfo ci) {
		WorldBackdrop.capture();
	}
}
