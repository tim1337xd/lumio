package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.Cards;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;

/**
 * Server and world lists as cards: no panel behind them and no separator lines at top and bottom; every entry is a rounded card that lights up
 * on hover and takes the menu color when selected. The entry content (icon, name, ping) is left to the game.
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
	private boolean lumio$cards() {
		return ModFeatures.MENUS.listsActive() && ((Object) this instanceof ServerSelectionList || (Object) this instanceof WorldSelectionList);
	}

	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
	private void lumio$noBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (lumio$cards()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
	private void lumio$noSeparators(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (lumio$cards()) {
			ci.cancel();
		}
	}

	/** Skips the vanilla selection box; the card shows the selection. */
	@Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
	private void lumio$noSelectionBox(GuiGraphicsExtractor graphics, @Coerce LayoutElement entry, int color, CallbackInfo ci) {
		if (lumio$cards()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractItem", at = @At("HEAD"))
	private void lumio$card(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, @Coerce LayoutElement entry, CallbackInfo ci) {
		if (!lumio$cards()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		// The card is as wide and tall as the entry minus a pixel at top and bottom, with only slightly rounded corners: the world/server icon is a
		// square flush with the entry's corner, and a smaller or rounder card would clip it.
		int x = entry.getX() - 4;
		int y = entry.getY() + 1;
		int width = entry.getWidth() + 8;
		int height = entry.getHeight() - 2;
		boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		Object picked = ((AbstractSelectionList<?>) (Object) this).getSelected();
		boolean selected = picked == entry;
		// same glass as the main menu buttons: glows on hover; the selected entry is filled with the menu color and outlined in it
		float lit = Cards.lit(entry, over, selected);
		float radius = 8f;
		HudStyle.panel(ui, x, y, width, height, radius, HudStyle.opacity(), UiGlow.Target.BUTTONS, lit);
		if (lit > 0.01f) {
			ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.accent, 0.20f * lit));
		}
		if (selected) {
			ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(ui.accent, 0.95f));
		}
	}
}
