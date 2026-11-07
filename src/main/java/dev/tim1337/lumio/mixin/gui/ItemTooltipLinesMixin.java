package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.tooltip.TooltipPainter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Tells the painter which item the next tooltip is for: every screen gets an item's lines here. It only notes the item and changes nothing of the lines. */
@Mixin(Screen.class)
public abstract class ItemTooltipLinesMixin {
	@Inject(method = "getTooltipFromItem", at = @At("RETURN"))
	private static void lumio$noteItem(Minecraft client, ItemStack stack, CallbackInfoReturnable<List<Component>> info) {
		TooltipPainter.arm(stack, info.getReturnValue().size());
	}
}
