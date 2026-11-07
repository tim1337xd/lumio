package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.BlockBreaking;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads how far blocks are broken, for the block overlay: servers with their own mining only tell the game this way. */
@Mixin(ClientLevel.class)
public abstract class BlockBreakingMixin {
	@Inject(method = "destroyBlockProgress", at = @At("HEAD"))
	private void lumio$told(int id, BlockPos pos, int progress, CallbackInfo ci) {
		if (ModFeatures.BLOCK_OVERLAY.isEnabled()) {
			BlockBreaking.told(pos, progress);
		}
	}
}
