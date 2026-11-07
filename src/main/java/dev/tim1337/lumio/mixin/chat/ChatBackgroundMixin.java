package dev.tim1337.lumio.mixin.chat;

import dev.tim1337.lumio.render.ColoredFlow;
import dev.tim1337.lumio.ui.HudStyle;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the Colored theme, the plain dark background of the chat's lines takes the colors, very quietly. Only that black background: the bars of a
 * message tag and the scroll bar keep their colors. The chat's lines lie above the bottom at negative heights, so the height is how far up a line is.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public abstract class ChatBackgroundMixin {
	/** How high up the chat the colors have changed completely, in GUI pixels. */
	private static final float COLOR_HEIGHT = 180f;

	@Shadow
	@Final
	private GuiGraphicsExtractor graphics;

	@Inject(method = "fill(IIIII)V", at = @At("HEAD"), cancellable = true)
	private void lumio$coloredBackground(int x0, int y0, int x1, int y1, int color, CallbackInfo info) {
		if ((color & 0xFFFFFF) != 0 || !HudStyle.colored()) {
			return;
		}
		float share = Math.clamp(-y0 / COLOR_HEIGHT, 0f, 1f);
		graphics.fill(x0, y0, x1, y1, color & 0xFF000000 | ColoredFlow.chatBackground(share));
		info.cancel();
	}
}
