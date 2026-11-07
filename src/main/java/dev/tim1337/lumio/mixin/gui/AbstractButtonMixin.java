package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;

/** Out-of-world screen buttons as glass: a pane with a light edge that takes the menu color, glows on hover and shrinks slightly when pressed. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Unique
	private Spring lumio$hover;
	@Unique
	private Spring lumio$press;

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void lumio$glass(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (!ModFeatures.MENUS.buttonsActive()) {
			return;
		}
		AbstractWidget widget = (AbstractWidget) (Object) this;
		Minecraft client = Minecraft.getInstance();
		// mixins do not run field initializers, so the springs are created lazily here
		if (lumio$hover == null) {
			lumio$hover = Spring.smooth(Spring.SNAPPY);
			lumio$press = Spring.bouncy(Spring.DEFAULT);
		}
		boolean lit = widget.isActive() && widget.isHoveredOrFocused();
		float h = Mth.clamp(lumio$hover.follow(lit ? 1f : 0f), 0f, 1f);
		boolean down = widget.isActive() && widget.isHovered() && client.mouseHandler.isLeftPressed();
		float squash = lumio$press.follow(down ? 1f : 0f);
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		int x = widget.getX();
		int y = widget.getY();
		int width = widget.getWidth();
		int height = widget.getHeight();
		float radius = Math.min(10f, height / 2f);
		float fade = widget.isActive() ? 1f : 0.5f;

		// pressed in as soon as the mouse goes down, not on release
		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = 1f - 0.04f * squash;
		pose.translate(x + width / 2f, y + height / 2f);
		pose.scale(scale, scale);
		pose.translate(-(x + width / 2f), -(y + height / 2f));
		// the same buttons as the main menu: a glass pane that glows and takes the menu color on hover
		float corner = Math.min(9f, height / 2f);
		HudStyle.panel(ui, x, y, width, height, corner, HudStyle.opacity() * fade, UiGlow.Target.BUTTONS, h * fade);
		if (h > 0.01f) {
			ui.rounded(x, y, width, height, corner, Ui.withOpacity(ui.accent, 0.26f * h * fade));
		}
		// the label is drawn here in the menu font; the game would draw it right after this in its own font
		String label = widget.getMessage().getString();
		if (!label.isEmpty()) {
			ui.textCentered(ui.fit(label, Math.max(8, width - 14)), x + width / 2, y + height / 2, widget.isActive() ? MacTheme.DARK.palette().text() : SystemColors.GRAY);
		}
		pose.popMatrix();
		ci.cancel();
	}

	/** Skips the vanilla label, which was already drawn in the menu font. */
	@Inject(method = "extractDefaultLabel", at = @At("HEAD"), cancellable = true)
	private void lumio$labelDone(ActiveTextCollector collector, CallbackInfo ci) {
		if (ModFeatures.MENUS.buttonsActive()) {
			ci.cancel();
		}
	}
}
