package dev.tim1337.lumio.mixin.chat;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.SuggestionProvider;
import dev.tim1337.lumio.api.experimental.util.CommandHints;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Adds the add-ons' entries ({@link SuggestionProvider}) to the game's command suggestions. The hook is the one call that asks the command tree (and through it the server) for a
 * command's suggestions, so the game's list, its look and its keys stay as they are; plain chat messages are never touched.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

	@Shadow
	@Final
	private EditBox input;
	@Shadow
	@Final
	Font font;
	@Shadow
	private CommandSuggestions.SuggestionsList suggestions;
	@Shadow
	@Final
	private Screen screen;
	@Shadow
	@Final
	private List<FormattedCharSequence> commandUsage;

	/** In the chat the input is where the chat is, and what a command takes goes with it: the game has it at the screen's end. */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;extractUsage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
	private void lumio$usagePlace(CommandSuggestions self, GuiGraphicsExtractor graphics, Operation<Void> original) {
		if (!(screen instanceof ChatScreen) || commandUsage.isEmpty()) {
			original.call(self, graphics);
			return;
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0f, ModFeatures.CHAT.usageShift(commandUsage.size(), screen.height));
		original.call(self, graphics);
		pose.popMatrix();
	}

	/** The list is as wide as its entries with their quiet text. */
	@ModifyExpressionValue(method = "showSuggestions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
	private int lumio$hintWidth(int width, @Local Suggestion suggestion) {
		return suggestion.getTooltip() instanceof CommandHints.Quiet quiet ? width + CommandHints.GAP + font.width(quiet.text()) : width;
	}

	/** With nothing to suggest, what the command takes next stands quietly after the cursor, as the game's own greyed text does. */
	@Inject(method = "updateUsageInfo", at = @At("RETURN"))
	private void lumio$usageHint(ParseResults<ClientSuggestionProvider> parsed, Suggestions found, CallbackInfo ci) {
		if (suggestions == null && found.isEmpty() && input.getCursorPosition() == input.getValue().length()) {
			String usage = CommandHints.usage(input.getValue());
			if (usage != null) {
				input.setSuggestion(usage);
			}
		}
	}

	@ModifyExpressionValue(method = "updateCommandInfo", at = @At(value = "INVOKE",
		target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<Suggestions> lumio$addonSuggestions(CompletableFuture<Suggestions> server) {
		CompletableFuture<Suggestions> merged = server;
		for (SuggestionProvider provider : Addons.suggestions()) {
			merged = provider.add(merged, input.getValue(), input.getCursorPosition());
		}
		return merged;
	}

	/** The game sorts the entries that start with the typed text first (minding case); the merged ones are already in their order. */
	@Inject(method = "sortSuggestions", at = @At("HEAD"), cancellable = true)
	private void lumio$keepOrder(Suggestions suggestions, CallbackInfoReturnable<List<Suggestion>> cir) {
		for (SuggestionProvider provider : Addons.suggestions()) {
			if (provider.merged(suggestions)) {
				cir.setReturnValue(new ArrayList<>(suggestions.getList()));
				return;
			}
		}
	}
}
