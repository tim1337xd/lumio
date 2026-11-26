package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.features.visual.world.HeldPose;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A bow whose own model has only a flat item's pose in the hand is held like the game's bow (see {@link HeldPose}). */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	/**
	 * After the item's model has set up its layers and their poses: here every held item of every entity passes, in first and third person.
	 * It returns at once for everything but a bow in a hand.
	 */
	@Inject(method = "appendItemLayers", at = @At("RETURN"))
	private void lumio$holdBowsAsBows(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		HeldPose.apply((ItemModelResolver) (Object) this, state, stack, context, level);
	}
}
