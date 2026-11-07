package dev.tim1337.lumio.mixin.inventory;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/** Keeps the inventory recipe book hidden. */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
	/** Keeps the inventory recipe book closed regardless of its saved state: the screen neither reserves room for it nor draws it. */
	@Inject(method = "isVisible", at = @At("RETURN"), cancellable = true)
	private void lumio$hidden(CallbackInfoReturnable<Boolean> cir) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (cir.getReturnValue() && feature != null && feature.hidesRecipeBook()) {
			cir.setReturnValue(false);
		}
	}
}
