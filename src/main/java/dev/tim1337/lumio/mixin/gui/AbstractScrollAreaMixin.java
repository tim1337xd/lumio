package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.Ui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;

/** Out-of-world list scrollbars: a faint rounded track and a rounded handle instead of the black bar and gray block. */
@Mixin(AbstractScrollArea.class)
public abstract class AbstractScrollAreaMixin {
	@Shadow
	protected abstract int scrollBarX();

	@Shadow
	public abstract int scrollBarY();

	@Shadow
	protected abstract int scrollerHeight();

	@Shadow
	public abstract int scrollbarWidth();

	@Shadow
	protected abstract boolean scrollable();

	@Inject(method = "extractScrollbar", at = @At("HEAD"), cancellable = true)
	private void lumio$glass(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		// nothing to draw when there is nothing to scroll; vanilla draws nothing then either
		if (!ModFeatures.MENUS.buttonsActive() || !scrollable()) {
			return;
		}
		AbstractWidget widget = (AbstractWidget) (Object) this;
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, Minecraft.getInstance().font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		int width = Math.max(4, scrollbarWidth() - 2);
		int x = scrollBarX() + (scrollbarWidth() - width) / 2;
		ui.rounded(x, widget.getY() + 2, width, widget.getHeight() - 4, width / 2f, Ui.withOpacity(0xFFFFFFFF, 0.07f));
		ui.rounded(x, scrollBarY(), width, scrollerHeight(), width / 2f, Ui.withOpacity(0xFFFFFFFF, 0.38f));
		ci.cancel();
	}
}
