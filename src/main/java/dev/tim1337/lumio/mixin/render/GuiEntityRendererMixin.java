package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.features.visual.world.HeldItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;

/** Tells the outline feature that an entity is being rendered into a screen, such as the player in the inventory, which has no world camera. */
@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {
	@Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("HEAD"))
	private void lumio$guiStart(GuiEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
		HeldItem.setGuiEntity(true);
	}

	@Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("RETURN"))
	private void lumio$guiEnd(GuiEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
		HeldItem.setGuiEntity(false);
	}
}
