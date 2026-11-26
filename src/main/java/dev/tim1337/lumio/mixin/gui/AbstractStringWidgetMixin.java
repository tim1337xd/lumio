package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.TextColor;

/**
 * The titles and one-line texts of the game's screens ("Options", "Game Menu") in the font picked in Menu settings at its semi-bold weight, as the buttons under them
 * are, while the mod styles those. The text keeps its own color and stands on the middle of the place the game gave it. Texts of several lines
 * stay the game's.
 */
@Mixin(AbstractStringWidget.class)
public abstract class AbstractStringWidgetMixin {
	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$menuFont(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!((Object) this instanceof StringWidget) || !ModFeatures.MENUS.buttonsActive()) {
			return;
		}
		AbstractWidget widget = (AbstractWidget) (Object) this;
		String text = widget.getMessage().getString();
		if (!text.isEmpty()) {
			Minecraft client = Minecraft.getInstance();
			Ui.setZoom(1f);
			Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), 0f, false);
			TextColor own = widget.getMessage().getStyle().getColor();
			int color = own == null ? MacTheme.DARK.palette().text() : 0xFF000000 | own.getValue();
			ui.textCenteredBold(text, widget.getX() + widget.getWidth() / 2f, widget.getY() + widget.getHeight() / 2, color);
		}
		ci.cancel();
	}
}
