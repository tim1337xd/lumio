package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.FancySwing;
import dev.tim1337.lumio.features.visual.world.HeldItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** Captures which item is rendered in the first person hand and its pose, for the held item outline and swing trail. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
	/**
	 * Captures the item being drawn. The item rendered in the hand is not always the held one: after
	 * switching slots, the old item lowers first while the new one is already held, so the kind of item is taken from this one.
	 */
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void lumio$arm(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial, float pitch, InteractionHand hand, float swing,
		ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HeldItem.setDrawnStack(stack);
	}

	/** What the hand reads of the swing: the game's as it is, read here for the swing values of the Debug log. */
	@ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;swingAnimation:F"))
	private float lumio$handSwing(float game, @Local(argsOnly = true, ordinal = 0) float partial) {
		return ModFeatures.HELD_ITEM_TRANSFORM.swingDrawn(game, partial);
	}

	/** A small item travels a shorter way, so that it stays on screen: only the swing's movement, the turns after it are the game's at every size. */
	@Redirect(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void lumio$swingMove(PoseStack pose, float x, float y, float z, float progress, PoseStack same, int side, HumanoidArm arm) {
		float share = ModFeatures.HELD_ITEM_TRANSFORM.swingMovement(arm);
		pose.translate(x * share, y * share, z * share);
	}

	/** And it drops a shorter way when it is changed. */
	@ModifyVariable(method = "applyItemArmTransform", at = @At("HEAD"), argsOnly = true)
	private float lumio$equip(float equip, @Local(argsOnly = true) HumanoidArm arm) {
		ModFeatures.HELD_ITEM_TRANSFORM.noteEquip(arm, equip);
		return equip * ModFeatures.HELD_ITEM_TRANSFORM.equipShare(arm);
	}

	/** The fancy swing moves the item its own way. */
	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void lumio$fancyMove(float progress, PoseStack pose, int side, HumanoidArm arm, CallbackInfo ci) {
		if (ModFeatures.HELD_ITEM_TRANSFORM.fancySwing(arm)) {
			FancySwing.move(pose, side, progress, ModFeatures.HELD_ITEM_TRANSFORM.swingMovement(arm));
			ci.cancel();
		}
	}

	/** And turns it. */
	@Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
	private void lumio$fancyTurn(PoseStack pose, HumanoidArm arm, float progress, CallbackInfo ci) {
		if (ModFeatures.HELD_ITEM_TRANSFORM.fancySwing(arm)) {
			FancySwing.turn(pose, arm == HumanoidArm.RIGHT ? 1 : -1, progress);
			ci.cancel();
		}
	}

	/** The empty hand has its own size and place. */
	@Inject(method = "renderPlayerArm", at = @At("HEAD"))
	private void lumio$handSize(PoseStack pose, SubmitNodeCollector collector, int light, float equip, float attack, HumanoidArm arm, PlayerRenderState player, CallbackInfo ci) {
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void lumio$armDone(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial, float pitch, InteractionHand hand, float swing,
		ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HeldItem.setDrawnStack(null);
	}
}
