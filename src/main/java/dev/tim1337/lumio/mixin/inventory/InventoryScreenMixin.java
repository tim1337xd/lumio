package dev.tim1337.lumio.mixin.inventory;

import dev.tim1337.lumio.ModFeatures;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;

/** Player inventory: the glass panel style and more spacing between sections. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	/** Replaces only the vanilla inventory texture with the glass panel when the inventory uses the mod's style. */
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void lumio$texture(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height,
										 int textureWidth, int textureHeight, Operation<Void> original) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (feature != null && feature.isEnabled()) {
			feature.drawBackground((InventoryScreen) (Object) this, graphics, x, y, width, height);
		} else {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		}
	}

	/** More space between the sections: the player inventory slots move down below the separator, and the screen grows by the same amount. */
	@Inject(method = "init", at = @At("TAIL"))
	private void lumio$room(CallbackInfo ci) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (feature != null && feature.isEnabled()) {
			feature.makeRoom((InventoryScreen) (Object) this);
		}
	}

	/** The dark gray crafting label does not suit the glass: it is drawn with the rest of the glass instead. */
	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
	private void lumio$labels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (feature != null && feature.isEnabled()) {
			ci.cancel();
		}
	}
}
