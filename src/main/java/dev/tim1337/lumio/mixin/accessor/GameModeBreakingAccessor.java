package dev.tim1337.lumio.mixin.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far the player has broken a block: only read, for the block overlay. */
@Mixin(MultiPlayerGameMode.class)
public interface GameModeBreakingAccessor {
	@Accessor("destroyProgress")
	float lumio$destroyProgress();

	@Accessor("destroyBlockPos")
	BlockPos lumio$destroyPos();
}
