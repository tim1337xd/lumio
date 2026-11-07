package dev.tim1337.lumio.mixin.inventory;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ScreenHooks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.Identifier;

/** Chest screens: storage overlay background and the glass panel style. */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
	/** A screen an add-on takes over is drawn by it instead of the chest texture; the vanilla slots are drawn on top. */
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void lumio$addonBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
										 CallbackInfo ci) {
		ScreenHooks hooks = Addons.takenOver(this);
		if (hooks != null) {
			hooks.drawBackground((Screen) (Object) this, graphics, mouseX, mouseY);
			ci.cancel();
		}
	}

	/** Replaces the chest's two texture blits (rows and player inventory) with one glass panel when containers use the mod's style. */
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void lumio$texture(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
										 int textureWidth, int textureHeight, Operation<Void> original) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (feature != null && feature.stylesChests()) {
			// the first blit is the rows: draw the panel once, covering both
			if (v == 0f) {
				feature.drawChestBackground((ContainerScreen) (Object) this, graphics, x, y, width);
			}
		} else {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		}
	}
}
