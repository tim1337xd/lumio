package dev.tim1337.lumio.mixin.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.ItemHeight;
import dev.tim1337.lumio.features.visual.world.SwingDip;
import dev.tim1337.lumio.features.visual.world.SwingTrace;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** The height of the item in the first person hand, which from Minecraft 26.3 on has a class of its own. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonItemsMixin {
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
	private void lumio$sameItem(LocalPlayer player, CallbackInfo ci) {
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
	private void lumio$itemHeight(LocalPlayer player, CallbackInfo ci) {
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
}
