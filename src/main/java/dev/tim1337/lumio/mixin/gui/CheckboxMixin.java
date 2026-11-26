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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.resources.Identifier;

/**
 * The game's checkboxes on its screens in the menu's look: a small box with the menu's corners in place of the game's, filled in the menu color
 * with a check mark while it is ticked, and a ring while it has the keyboard. Only the box: its text and what a click does are the game's own.
 */
@Mixin(Checkbox.class)
public abstract class CheckboxMixin {
	@Unique
	private static final float HOVER = 0.14f;
	@Unique
	private static final float FOCUS_RING = 0.9f;
	@Unique
	private static final float DISABLED = 0.5f;
	@Unique
	private static final String CHECK = "check";

	@Shadow
	public abstract boolean selected();

	/** The box is the one sprite the checkbox draws: ours stands in its place, at the same size. */
	@WrapOperation(method = "extractContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
	private void lumio$box(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color, Operation<Void> original) {
		if (!ModFeatures.MENUS.buttonsActive()) {
			original.call(graphics, pipeline, sprite, x, y, width, height, color);
			return;
		}
		AbstractWidget widget = (AbstractWidget) (Object) this;
		Minecraft client = Minecraft.getInstance();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		float radius = VanillaLook.radius(height);
		float fade = widget.isActive() ? 1f : DISABLED;
		boolean lit = widget.isActive() && widget.isHovered();
		HudStyle.panel(ui, x, y, width, height, radius, HudStyle.opacity() * fade, UiGlow.Target.BUTTONS, lit ? fade : 0f);
		if (selected()) {
			ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.accent, fade));
			int mark = Math.round(Math.min(width, height) * VanillaLook.CHECK_SHARE);
			ui.icon(CHECK, x + (width - mark) / 2, y + (height - mark) / 2, mark, Ui.withOpacity(0xFFFFFFFF, fade));
		} else if (lit) {
			ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.accent, HOVER));
		}
		if (widget.isFocused() && widget.isActive() && client.getLastInputType().isKeyboard()) {
			ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(0xFFFFFFFF, FOCUS_RING));
		}
	}
}
