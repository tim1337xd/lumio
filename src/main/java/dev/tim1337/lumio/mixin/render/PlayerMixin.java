package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.SwingTrace;
import dev.tim1337.lumio.mixin.accessor.LivingEntityAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/** Keeps the visual swing counter running when attacks reset the attack ticker. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Unique
	private int lumio$swapBefore;

	/**
	 * Attacking and mining reset two counters: the attack strength ticker, and the one that makes the held item dip and recover. Only the second,
	 * which is purely visual, is preserved while a swing is running, so another hit does not restart the animation.
	 * The attack strength ticker is left alone, since it is gameplay rather than visuals.
	 */
	@Inject(method = "resetAttackStrengthTicker", at = @At("HEAD"))
	private void lumio$rememberSwap(CallbackInfo ci) {
		lumio$swapBefore = ((LivingEntityAccessor) this).lumio$itemSwapTicker();
	}

	@Inject(method = "resetAttackStrengthTicker", at = @At("RETURN"))
	private void lumio$keepAnimation(CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer && ModFeatures.HELD_ITEM_TRANSFORM.swingRunning()) {
			((LivingEntityAccessor) this).lumio$setItemSwapTicker(lumio$swapBefore);
			SwingTrace.ran(SwingTrace.KEPT_SWAP);
		}
	}
}
