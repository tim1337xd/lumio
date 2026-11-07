package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * The length of the local player's swing at the chosen speed. The first person hand draws the game's swing as it is, so only its length is
 * changed; it is only the look, whether and when a hit counts is the game's.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyReturnValue(method = "getModifiedSwingDuration", at = @At("RETURN"))
	private int lumio$swingLength(int game, @Local(argsOnly = true) SwingAnimation animation) {
		if (!((Object) this instanceof LocalPlayer) || !ModFeatures.HELD_ITEM_TRANSFORM.customSwing()) {
			return game;
		}
		return ModFeatures.HELD_ITEM_TRANSFORM.swingDuration(game, animation::duration);
	}
}
