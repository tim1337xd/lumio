package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Visual Filter: the bits flying off a block are not made, when that is on. The game makes them in these two methods only (while the player
 * mines, and when a block breaks, whoever broke it) and adds them to the particle engine itself, so no other particle passes here: block
 * particles the server sends and the readers of particles are untouched, and so are the cracks on the block and the sounds.
 */
@Mixin(ClientLevel.class)
public abstract class VisualFilterBreakingMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void lumio$skipBrokenBits(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hidesBreakingParticles()) {
			ci.cancel();
		}
	}

	//#if 26.3
	//$ @Inject(method = "addBreakingParticles", at = @At("HEAD"), cancellable = true)
	//$ private void lumio$skipMiningBits(BlockPos pos, Direction side, BlockState state, CallbackInfo ci) {
	//#else
	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
	private void lumio$skipMiningBits(BlockPos pos, Direction side, CallbackInfo ci) {
	//#endif
		if (ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hidesBreakingParticles()) {
			ci.cancel();
		}
	}
}
