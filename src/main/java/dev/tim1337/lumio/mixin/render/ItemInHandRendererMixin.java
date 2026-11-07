package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.FancySwing;
import dev.tim1337.lumio.features.visual.world.ItemHeight;
import dev.tim1337.lumio.features.visual.world.SwingDip;
import dev.tim1337.lumio.features.visual.world.SwingTrace;
import dev.tim1337.lumio.features.visual.world.HeldItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** Captures which item is rendered in the first person hand and its pose, for the held item outline and swing trail. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	private float mainHandHeight;
	@Shadow
	private float oMainHandHeight;
	@Shadow
	private ItemStack mainHandItem;
	@Unique
	private final SwingDip lumio$dip = new SwingDip();
	@Unique
	private final ItemHeight lumio$height = new ItemHeight();

	/** The server only rewrote the data of the item in the hand, as it does while a tool is used: the game would drop the item and raise it again in the middle of a swing. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void lumio$sameItem(CallbackInfo ci) {
		SwingTrace.newTick();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		boolean shownIsHeld = mainHandItem == held;
		if (lumio$height.changedInPlace(player.getInventory().getSelectedSlot(), shownIsHeld, ItemStack.isSameItem(mainHandItem, held))
			&& ModFeatures.HELD_ITEM_TRANSFORM.handlesSwing()) {
			mainHandItem = held;
			SwingTrace.ran(SwingTrace.SAME_ITEM);
		}
	}

	/**
	 * How high the item is drawn: it stays at rest after a hit unless the dip is wanted, and then the dip runs at the swing's speed (see {@link SwingDip}). Another item coming into the hand keeps the game's drop and rise.
	 * Only the look: the timers are only read, and nothing else changes.
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void lumio$itemHeight(CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		var feature = ModFeatures.HELD_ITEM_TRANSFORM;
		float scale = player.getItemSwapScale(1f);
		float height = lumio$height.after(mainHandItem == player.getMainHandItem(), scale, player.isHandsBusy(), false);
		float speed = feature.swingSpeedNow();
		float timer = lumio$dip.step(scale, feature.swingRunning(), speed, player.getCurrentItemAttackStrengthDelay());
		if (!feature.handlesSwing()) {
			return;
		}
		if (height == ItemHeight.REST) {
			mainHandHeight = ItemHeight.REST;
		} else if (timer >= 0f && !lumio$height.changing() && !player.isHandsBusy()) {
			mainHandHeight = SwingDip.height(oMainHandHeight, timer, speed);
			SwingTrace.ran(SwingTrace.DIP);
		}
	}

	/** What the hand reads of the swing: on 26.2 the game's as it is, read here for the swing values of the Debug log. */
	@ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"))
	private float lumio$handSwing(float game, @Local(argsOnly = true) float partial) {
		return ModFeatures.HELD_ITEM_TRANSFORM.swingDrawn(game, partial);
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

	/**
	 * Captures the item being drawn. The item rendered in the hand is not always the held one: after
	 * switching slots, the old item lowers first while the new one is already held, so the kind of item is taken from this one.
	 */
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void lumio$armBase(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand,
		float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HeldItem.setDrawnStack(stack);
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void lumio$armDone(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand,
		float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		HeldItem.setDrawnStack(null);
	}
}
