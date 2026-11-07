package dev.tim1337.lumio.mixin.inventory;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.Slot;

/** Disables the offhand slot in the styled inventory. */
@Mixin(Slot.class)
public abstract class SlotMixin {
	/** Inactive slots are not drawn, hovered or clicked; this is how the offhand slot is removed from the inventory. */
	@Inject(method = "isActive", at = @At("RETURN"), cancellable = true)
	private void lumio$offHand(CallbackInfoReturnable<Boolean> cir) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (cir.getReturnValue() && feature != null && feature.hidesOffHand((Slot) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
