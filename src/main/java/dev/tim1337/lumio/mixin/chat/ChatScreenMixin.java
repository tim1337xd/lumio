package dev.tim1337.lumio.mixin.chat;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Chat tabs, chat search and smooth scrolling on the chat screen. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected EditBox input;
	@Shadow
	private CommandSuggestions commandSuggestions;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	/** In the chat of the mod the list of suggestions shows at most this many rows, and scrolls for the rest: it fits in the panel. */
	@ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/components/EditBox;Lnet/minecraft/client/gui/Font;ZZIIZI)V"), index = 7)
	private int lumio$suggestionRows(int game) {
		return ModFeatures.CHAT.isEnabled() ? Math.min(game, ModFeatures.CHAT.suggestionRows()) : game;
	}

	/** Draws the chat tabs first (HEAD), so what the screen draws afterwards (the command suggestions) appears over them. Tab clicks are handled before the screen sees them. */
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void lumio$tabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
		if (input != null) {
			ModFeatures.CHAT.drawInput(graphics, font, input, height);
			ModFeatures.CHAT.placeSuggestions(commandSuggestions, input, width, height);
		}
		ModFeatures.CHAT.renderTabs(graphics, mouseX, mouseY);
		// while the search field has focus, the chat input hides its cursor
		boolean searching = ModFeatures.CHAT.searchFocused();
		if (input != null && input.isFocused() == searching) {
			input.setFocused(!searching);
		}
	}

	/** The band the game draws behind the input gives way to the input in the mod's style. */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void lumio$inputBand(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color, Operation<Void> original) {
		if (!ModFeatures.CHAT.isEnabled()) {
			original.call(graphics, left, top, right, bottom, color);
		}
	}

	/** Highlights search matches over the chat lines, after everything else is drawn (TAIL). */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lumio$matches(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
		ModFeatures.CHAT.renderMatches(graphics);
		ModFeatures.CHAT.renderMoreMenu(graphics, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void lumio$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (ModFeatures.CHAT.pressScrollBar(event.x(), event.y(), ScreenInput.button(event), height) || ModFeatures.CHAT.clickTabs(event.x(), event.y(), ScreenInput.button(event)) || ModFeatures.CHAT.clickLine(event.x(), event.y(), ScreenInput.button(event), input)
			|| ModFeatures.CHAT.copyAt(event.x(), event.y(), ScreenInput.button(event))) {
			cir.setReturnValue(true);
		}
	}

	/** Search key handling (Ctrl+F, and every key while the search has focus), before the chat input and the history navigation. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void lumio$searchKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (ModFeatures.CHAT.scrollKey(event.key()) || ModFeatures.CHAT.searchKey(event.key(), ScreenInput.modifiers(event.modifiers()))) {
			cir.setReturnValue(true);
		}
	}

	/** The wheel scrolls the chat smoothly; what the command suggestions take is still theirs, and the game's wheel, a jump of 7 lines, stays out. */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void lumio$wheel(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (ModFeatures.CHAT.isEnabled()) {
			if (!commandSuggestions.mouseScrolled(Mth.clamp(scrollY, -1.0, 1.0))) {
				ModFeatures.CHAT.wheel(scrollY, Minecraft.getInstance().hasShiftDown());
			}
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return ModFeatures.CHAT.dragScrollBar(event.y(), height) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		ModFeatures.CHAT.releaseScrollBar();
		return super.mouseReleased(event);
	}

	/** Typed characters go into the search field while it has focus. */
	@Override
	public boolean charTyped(CharacterEvent event) {
		if (ModFeatures.CHAT.searchChar(event.codepoint(), event.isAllowedChatCharacter())) {
			return true;
		}
		return super.charTyped(event);
	}
}
