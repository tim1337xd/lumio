package dev.tim1337.lumio.mixin.accessor;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The item of an entry of a recipe toast, a private type of the game. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.RecipeToast$Entry")
public interface RecipeEntryAccessor {
	@Accessor("unlockedItem")
	ItemStack lumio$unlocked();
}
