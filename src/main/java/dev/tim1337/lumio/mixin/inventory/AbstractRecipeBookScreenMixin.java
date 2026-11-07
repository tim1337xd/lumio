package dev.tim1337.lumio.mixin.inventory;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Hides the recipe book button when the recipe book is hidden. */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	/** Skips creating the recipe book button in the inventory when the book is hidden. */
	@Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
	private void lumio$noButton(CallbackInfo ci) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (feature != null && (Object) this instanceof InventoryScreen && feature.wantsRecipeBookHidden()) {
			ci.cancel();
		}
	}
}
