package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.VanillaLook;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.resources.Identifier;

/**
 * The tabs of the game's screens (Create New World, the statistics) in the menu's look: a box with the menu's corners in place of the game's
 * tab, tinted in the menu color while it is the chosen one. Only the tab's own picture: its label, the line under it while it has the
 * keyboard, the keys and the clicks are the game's own.
 */
@Mixin(targets = "net.minecraft.client.gui.components.tabs.MenuTabBar$MenuTabButton")
public abstract class MenuTabButtonMixin {
	@Unique
	private static final float CHOSEN = 0.3f;
	@Unique
	private static final float HOVER = 0.12f;
	@Unique
	private static final float DISABLED = 0.5f;
	/** A tab keeps this far from its neighbours, in GUI pixels, so each is a box of its own. */
	@Unique
	private static final int GAP = 1;

	/** The tab is the one sprite the button draws: the box stands in its place. */
	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void lumio$box(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
		if (!ModFeatures.MENUS.buttonsActive()) {
			original.call(graphics, pipeline, sprite, x, y, width, height);
			return;
		}
		TabButton tab = (TabButton) (Object) this;
		AbstractWidget widget = tab;
		Minecraft client = Minecraft.getInstance();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		float radius = VanillaLook.radius(height);
		float fade = widget.isActive() ? 1f : DISABLED;
		boolean lit = widget.isActive() && widget.isHovered();
		HudStyle.panel(ui, x + GAP, y, width - 2 * GAP, height, radius, HudStyle.opacity() * fade, UiGlow.Target.BUTTONS, lit ? fade : 0f);
		if (tab.isSelected()) {
			ui.rounded(x + GAP, y, width - 2 * GAP, height, radius, Ui.withOpacity(ui.accent, CHOSEN * fade));
		} else if (lit) {
			ui.rounded(x + GAP, y, width - 2 * GAP, height, radius, Ui.withOpacity(ui.accent, HOVER));
		}
	}
}
