package dev.tim1337.lumio.mixin.chat;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.CommandHints;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.suggestion.Suggestion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;

/**
 * The game's list of command suggestions. In the chat of the mod it lies on a panel in the chat's style (see ChatFeature.placeSuggestions and drawSuggestions) instead of the game's dark box and
 * dotted lines; its entries, keys, scrolling and clicks stay the game's. Every entry's quiet text is drawn beside it ({@link CommandHints}); an entry without one costs one type check.
 */
@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public abstract class SuggestionsListMixin {
	private static final int HINT_COLOR = 0xFF7A7A7A;
	/** The game's color of the selected entry, and the ones in the chat's style. */
	private static final int GAME_SELECTED = 0xFFFFFF00;
	private static final int SELECTED_TEXT = 0xFFFFFFFF;
	private static final int ENTRY_TEXT = 0xFFC4C4C4;

	@Shadow
	@Final
	private Rect2i rect;
	@Shadow
	private int offset;
	@Shadow
	private int current;

	/** Whether this frame's list is drawn in the chat's style. */
	@Unique
	private boolean lumio$styled;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void lumio$panel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		lumio$styled = ModFeatures.CHAT.stylesSuggestions();
		if (lumio$styled) {
			ModFeatures.CHAT.drawSuggestions(graphics, rect, current - offset);
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lumio$panelDone(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (lumio$styled) {
			ModFeatures.CHAT.suggestionsDrawn(graphics);
		}
	}

	/** The game's dark box and the dotted lines that say there is more: the panel takes their place. */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void lumio$box(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color, Operation<Void> original) {
		if (!lumio$styled) {
			original.call(graphics, left, top, right, bottom, color);
		}
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void lumio$hint(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, Operation<Void> original, @Local Suggestion suggestion) {
		int shown = lumio$styled ? (color == GAME_SELECTED ? SELECTED_TEXT : ENTRY_TEXT) : color;
		original.call(graphics, font, text, x, y, shown);
		if (suggestion.getTooltip() instanceof CommandHints.Quiet quiet) {
			graphics.text(font, quiet.text(), x + font.width(text) + CommandHints.GAP, y, HINT_COLOR);
		}
	}
}
