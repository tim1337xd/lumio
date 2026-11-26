package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.VanillaLook;

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
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.util.Mth;

/**
 * Out-of-world screen buttons as glass: a pane with a light edge that takes the menu color, glows on hover and shrinks slightly when pressed. A
 * button that switches a value (Subtitles, Graphics, the sound device) has its name on the left and the value on the right.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Unique
	private Spring lumio$hover;
	@Unique
	private Spring lumio$press;
	/** How strong the ring of the keyboard's focus is, and how far a switching button's value is from the menu color toward white. */
	@Unique
	private static final float FOCUS_RING = 0.9f;
	@Unique
	private static final float VALUE_WHITER = 0.55f;

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
		// a box with small corners, as every control of the menu: never a pill
		float radius = VanillaLook.radius(height);
		float fade = widget.isActive() ? 1f : 0.5f;

		// pressed in as soon as the mouse goes down, not on release
		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = 1f - 0.04f * squash;
		pose.translate(x + width / 2f, y + height / 2f);
		pose.scale(scale, scale);
		pose.translate(-(x + width / 2f), -(y + height / 2f));
		// the same buttons as the main menu: a glass pane that glows and takes the menu color on hover
		HudStyle.panel(ui, x, y, width, height, radius, HudStyle.opacity() * fade, UiGlow.Target.BUTTONS, h * fade);
		if (h > 0.01f) {
			ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.accent, 0.26f * h * fade));
		}
		// the keyboard's focus shows as a light ring, apart from the pointer's glow
		if (widget.isFocused() && widget.isActive() && client.getLastInputType().isKeyboard()) {
			ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(0xFFFFFFFF, FOCUS_RING));
		}
		// the label is drawn here in the menu font; the game would draw it right after this in its own font
		String label = widget.getMessage().getString();
		int text = widget.isActive() ? MacTheme.DARK.palette().text() : SystemColors.GRAY;
		String value = (Object) this instanceof CycleButton<?> ? VanillaLook.sliderValue(label) : "";
		if (!value.isEmpty()) {
			// a button that switches a value: its name on the left, the value it has now on the right in a lighter tone of the menu color
			int valueWidth = ui.width(value);
			String name = ui.fit(VanillaLook.sliderName(label), VanillaLook.nameRoom(width, valueWidth));
			ui.textMiddle(name, x + VanillaLook.SLIDER_SIDE, y + height / 2, text);
			ui.textRight(value, x + width - VanillaLook.SLIDER_SIDE, y + height / 2, Ui.withOpacity(Ui.mix(ui.accent, 0xFFFFFFFF, VALUE_WHITER), fade));
		} else if (!label.isEmpty()) {
			ui.textCentered(ui.fit(label, Math.max(8, width - 14)), x + width / 2, y + height / 2, text);
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
