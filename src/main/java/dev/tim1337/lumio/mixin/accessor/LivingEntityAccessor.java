package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;

/** Exposes the hand swing counter that makes the held item dip on hits; it only affects how the hand looks. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("itemSwapTicker")
	int lumio$itemSwapTicker();

	@Accessor("itemSwapTicker")
	void lumio$setItemSwapTicker(int value);
}
