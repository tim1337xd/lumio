package dev.tim1337.lumio.mixin.inventory;

import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ScreenHooks;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

/** Container screen hooks: storage overlay input, item list keys, styled slot highlights and labels. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;
	@Shadow
	private boolean skipNextRelease;

	/** @return whether this screen uses the mod's rounded slots, so the hover highlight is rounded too */
	private boolean lumio$styled() {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if (feature == null || !feature.isEnabled()) {
			return false;
		}
		Object self = this;
		return self instanceof InventoryScreen || self instanceof ContainerScreen && feature.stylesChests()
			|| Addons.takenOver(self) != null;
	}

	/** The add-ons lay out what they draw right after init (a taken-over screen moves its slots where they are drawn). */
	@Inject(method = "init", at = @At("TAIL"))
	private void lumio$addonInit(CallbackInfo ci) {
		for (ScreenHooks hooks : Addons.screenHooks()) {
			hooks.init((Screen) (Object) this);
		}
	}

	/**
	 * The add-ons see the player's clicks first; a click one of them uses goes no further, and neither does its release: with an item on the cursor the game would send a click on the release beside a slot, one more than the player made.
	 */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void lumio$addonClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		for (ScreenHooks hooks : Addons.screenHooks()) {
			if (hooks.mouseClicked((Screen) (Object) this, hoveredSlot, event.x(), event.y(), ScreenInput.button(event))) {
				skipNextRelease = true;
				cir.setReturnValue(true);
				return;
			}
		}
	}

	/** A screen an add-on draws itself scrolls as the add-on says. */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void lumio$addonScroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		ScreenHooks hooks = Addons.takenOver(this);
		if (hooks != null) {
			cir.setReturnValue(hooks.mouseScrolled((Screen) (Object) this, scrollY));
		}
	}

	/** Keys go to the add-ons first: a search field of theirs with the focus gets them all, also the inventory key. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void lumio$addonKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		for (ScreenHooks hooks : Addons.screenHooks()) {
			if (hooks.keyPressed((Screen) (Object) this, event)) {
				cir.setReturnValue(true);
				return;
			}
		}
	}

	private Ui lumio$ui(GuiGraphicsExtractor graphics) {
		Ui.setZoom(1f);
		var client = Minecraft.getInstance();
		return new Ui(graphics, client.font, HudStyle.palette(), 0xFF000000 | MenuFeature.accentRgb(), 0f, false);
	}

	/** What add-ons draw on the slots: after the slots and before the cursor's item and the tooltip, which stay on top of it. */
	@Inject(method = "extractContents", at = @At("TAIL"))
	private void lumio$slotLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		SlotLayer.draw((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
	}

	/** Behind the item: the slot frame lights up in the accent color, following the frame's shape. */
	@Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
	private void lumio$highlightBack(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!lumio$styled()) {
			return;
		}
		ci.cancel();
		if (hoveredSlot != null && hoveredSlot.isHighlightable()) {
			var ui = lumio$ui(graphics);
			ui.rounded(hoveredSlot.x - 1, hoveredSlot.y - 1, 18, 18, Radius.SM, Ui.withOpacity(ui.accent, 0.32f));
		}
	}

	/** In front of the item: a soft light and an outline around the frame, so the slot stays visible over any item. */
	@Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
	private void lumio$highlightFront(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!lumio$styled()) {
			return;
		}
		ci.cancel();
		if (hoveredSlot != null && hoveredSlot.isHighlightable()) {
			var ui = lumio$ui(graphics);
			ui.rounded(hoveredSlot.x - 1, hoveredSlot.y - 1, 18, 18, Radius.SM, 0x22FFFFFF);
			ui.ring(hoveredSlot.x - 1, hoveredSlot.y - 1, 18, 18, Radius.SM, 1, Ui.withOpacity(ui.accent, 0.9f));
		}
	}
	/** The dark gray container labels are drawn with the glass instead when containers use the mod's style. */
	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
	private void lumio$labels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		var feature = ModFeatures.INVENTORY_SCREEN;
		if ((Object) this instanceof ContainerScreen && feature != null && feature.stylesChests() || Addons.takenOver(this) != null) {
			ci.cancel();
		}
	}
}
