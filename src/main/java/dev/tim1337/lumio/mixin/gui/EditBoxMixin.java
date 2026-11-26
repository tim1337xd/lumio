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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;

/**
 * The game's text fields on its screens (a world's name, a server's address, the search of a list) in the menu's look: the same box as a button
 * in place of the game's frame, with a ring in the menu color while it has the keyboard. Only the frame: the text, the cursor, the selection and
 * every key are the game's own.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {
	@Unique
	private static final float INSET = 0.28f;
	@Unique
	private static final float FOCUS_RING = 0.95f;
	@Unique
	private static final float DISABLED = 0.5f;

	/** The frame is the one sprite the field draws: the box stands in its place, at the same size. */
	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void lumio$box(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
		if (!ModFeatures.MENUS.buttonsActive()) {
			original.call(graphics, pipeline, sprite, x, y, width, height);
			return;
		}
		AbstractWidget widget = (AbstractWidget) (Object) this;
		Minecraft client = Minecraft.getInstance();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		float radius = VanillaLook.radius(height);
		float fade = widget.isActive() ? 1f : DISABLED;
		HudStyle.panel(ui, x, y, width, height, radius, HudStyle.opacity() * fade, UiGlow.Target.BUTTONS, 0f);
		// a field is set in: a little darker than a button
		ui.rounded(x, y, width, height, radius, Ui.withOpacity(0xFF000000, INSET * fade));
		if (widget.isFocused() && widget.isActive()) {
			ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(ui.accent, FOCUS_RING));
		}
	}
}
