package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.ui.Glass;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;

/**
 * The game's sliders as glass like the buttons: the filled part takes the menu color and the handle is a slim light pill. The label is written inside the
 * track, so where the handle reaches it, the handle fades behind the label and the label takes a soft shadow: no letter is ever lost.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	/** How close to the label the handle starts to fade, in GUI pixels. */
	@Unique
	private static final float HANDLE_FADE_REACH = 6f;
	/** How much of the handle shows behind the label. */
	@Unique
	private static final float HANDLE_BEHIND_LABEL = 0.28f;
	@Unique
	private static final float LABEL_SHADOW_OPACITY = 0.7f;
	@Unique
	private static final int LABEL_SIDES = 28;

	@Shadow
	protected double value;

	@Unique
	private float lumio$hover;
	@Unique
	private long lumio$stamp;

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$glass(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!ModFeatures.MENUS.buttonsActive()) {
			return;
		}
		AbstractWidget widget = (AbstractWidget) (Object) this;
		Minecraft client = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = lumio$stamp == 0 ? 0.1f : Math.min(0.1f, (now - lumio$stamp) / 1_000_000_000f);
		lumio$stamp = now;
		boolean lit = widget.isActive() && widget.isHoveredOrFocused();
		lumio$hover += ((lit ? 1f : 0f) - lumio$hover) * (1f - (float) Math.exp(-dt * 12f));
		float h = Mth.clamp(lumio$hover, 0f, 1f);
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, ModFeatures.MENU.theme().palette(), ModFeatures.MENU.accentArgb(), dt, false);
		int x = widget.getX();
		int y = widget.getY();
		int width = widget.getWidth();
		int height = widget.getHeight();
		float radius = Math.min(10f, height / 2f);
		float fade = widget.isActive() ? 1f : 0.5f;
		float amount = Mth.clamp((float) value, 0f, 1f);

		Glass.button(ui, x, y, width, height, radius, h, fade);
		// pane, edge light and glow are the same as the buttons'
		// The handle travels between both ends with room for its width at each, and the fill reaches its center. The fill is the full capsule
		// clipped at the handle, so its ends keep the capsule shape at any value and nothing sticks out past the handle.
		int handleWidth = 3;
		int travel = width - 2 * Math.round(radius) - handleWidth;
		int handleX = x + Math.round(radius) + Math.round(amount * Math.max(0, travel));
		int filled = Mth.clamp(handleX + handleWidth / 2 - x, 0, width);
		graphics.enableScissor(x, y, x + filled, y + height);
		ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.accent, (0.38f + 0.14f * h) * fade));
		// highlight along the top of the fill
		ui.rounded(x + 2, y + 1, Math.max(0, width - 4), 1, Radius.pill(1), Ui.withOpacity(0xFFFFFFFF, 0.22f * fade));
		graphics.disableScissor();
		String message = widget.getMessage().getString();
		String label = message.isEmpty() ? message : ui.fit(message, Math.max(8, width - LABEL_SIDES));
		int labelHalf = ui.width(label) / 2;
		int center = x + width / 2;
		// 0 where the handle reaches the label, 1 from a little beside it
		float apart = label.isEmpty() ? 1f : Mth.clamp(Math.max(center - labelHalf - (handleX + handleWidth), handleX - (center + labelHalf)) / HANDLE_FADE_REACH, 0f, 1f);
		float handle = Mth.lerp(apart, HANDLE_BEHIND_LABEL, 1f) * fade;
		ui.rounded(handleX - 1, y + 3, handleWidth + 2, height - 6, (handleWidth + 2) / 2f, Ui.withOpacity(ui.accent, 0.25f * (0.5f + h) * handle));
		ui.rounded(handleX, y + 4, handleWidth, height - 8, handleWidth / 2f, Ui.withOpacity(0xFFFFFFFF, (0.8f + 0.2f * h) * handle));
		if (!label.isEmpty()) {
			if (apart < 1f) {
				ui.textCentered(label, center, y + height / 2 + 1, Ui.withOpacity(0xFF000000, LABEL_SHADOW_OPACITY * (1f - apart) * fade));
			}
			ui.textCentered(label, center, y + height / 2, widget.isActive() ? MacTheme.DARK.palette().text() : SystemColors.GRAY);
		}
		ci.cancel();
	}
}
