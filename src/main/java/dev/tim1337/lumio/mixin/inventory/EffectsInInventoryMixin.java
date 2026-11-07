package dev.tim1337.lumio.mixin.inventory;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.InventoryEffects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;

/**
 * The list of potion effects beside the inventory can be left out; the game has it there and in the creative inventory, not by chests. The game asks whether there is room for the list, which also decides whether the HUD's
 * effect icons stay, and draws it apart from that: both are answered here, once per frame while such a screen is open.
 */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
	@Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
	private void lumio$hide(CallbackInfoReturnable<Boolean> cir) {
		if (ModFeatures.INVENTORY_SCREEN.hidesEffects() || InventoryEffects.hidden()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$hideList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (ModFeatures.INVENTORY_SCREEN.hidesEffects() || InventoryEffects.hidden()) {
			ci.cancel();
		}
	}
}
