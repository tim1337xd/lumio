package dev.tim1337.lumio.mixin.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.render.LyricOverlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;

/** The game's vignette: off in the cinematic camera; while lyrics stand in the world it is drawn around them ({@link LyricOverlay}). */
@Mixin(Hud.class)
public abstract class HudVignetteMixin {
	@Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
	private void lumio$noVignetteInTheFilm(CallbackInfo ci) {
		if (ModFeatures.CINEMATIC != null && ModFeatures.CINEMATIC.active()) {
			ci.cancel();
		}
	}

	@Redirect(method = "extractVignette", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIII)V"))
	private void lumio$vignette(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
								int textureWidth, int textureHeight, int color) {
		if (!LyricOverlay.takeOver(texture, color)) {
			graphics.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
		}
	}
}
