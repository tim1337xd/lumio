package dev.tim1337.lumio.mixin.chat;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ChatListener;
import dev.tim1337.lumio.api.experimental.util.Guard;
import dev.tim1337.lumio.features.visual.hud.StatusBarsHudFeature;
import dev.tim1337.lumio.features.visual.screen.ChatFeature;
import dev.tim1337.lumio.features.visual.screen.chat.ChatLayout;
import dev.tim1337.lumio.features.visual.screen.chat.ChatLookAccess;
import dev.tim1337.lumio.mixin.accessor.ChatComponentAccessor;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Chat size scaling, positioning above the status bars, message timestamps and collapsing of repeated messages. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	protected abstract void refreshTrimmedMessages();

	/** Multiplies the chat scale by the mod's size factor: text, width and height all scale with it. */
	@Inject(method = "getScale()D", at = @At("RETURN"), cancellable = true)
	private void lumio$size(CallbackInfoReturnable<Double> cir) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null && chat.sizeFactor() != 1f) {
			cir.setReturnValue(cir.getReturnValue() * chat.sizeFactor());
		}
	}

	// width and height are computed in (now larger) text pixels: return them unchanged so the whole chat scales up
	@Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
	private void lumio$width(CallbackInfoReturnable<Integer> cir) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null) {
			cir.setReturnValue(chat.chatWidthSetting(cir.getReturnValue()));
		}
	}

	@Inject(method = "getHeight()I", at = @At("RETURN"), cancellable = true)
	private void lumio$height(CallbackInfoReturnable<Integer> cir) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null) {
			int lines = chat.chatLinesSetting();
			cir.setReturnValue(lines > 0 ? lines * getLineHeight() : Math.round(cir.getReturnValue() * chat.sizeFactor()));
		}
	}

	@Inject(method = "getLineHeight()I", at = @At("RETURN"), cancellable = true)
	private void lumio$lineHeight(CallbackInfoReturnable<Integer> cir) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null && chat.extraLineHeight() != 0) {
			cir.setReturnValue(cir.getReturnValue() + chat.extraLineHeight());
		}
	}

	/** The panel and what lies behind the lines come first, and what lies around their text after the game has drawn them. */
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
	private void lumio$behind(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo info) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null) {
			chat.drawBehind(graphics, font, chat.fadeTick(tick), mouseX, mouseY, mode.foreground);
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("TAIL"))
	private void lumio$around(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo info) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null) {
			chat.drawAround(graphics, font, chat.fadeTick(tick), mouseX, mouseY, mode.foreground);
		}
	}

	/** The game's rows while it draws come from its spacing option, not from {@link #getLineHeight}: they get the same height, so the text lies in the rows all else goes by. */
	@ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;doubleValue()D"))
	private double lumio$rows(double spacing) {
		ChatFeature chat = ModFeatures.CHAT;
		return chat == null || !chat.isEnabled() ? spacing : ChatLayout.gameSpacing(getLineHeight());
	}

	/** The game's fade is worked out from this tick: moved, the lines fade after the set time. */
	@ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int lumio$fade(int tick) {
		ChatFeature chat = ModFeatures.CHAT;
		return chat == null ? tick : chat.fadeTick(tick);
	}

	/** Drawing and clicks come through here: see {@link ChatLookAccess}. */
	@ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At("HEAD"), argsOnly = true)
	private ChatComponent.ChatGraphicsAccess lumio$look(ChatComponent.ChatGraphicsAccess access) {
		ChatFeature chat = ModFeatures.CHAT;
		return chat == null || !chat.isEnabled() ? access : new ChatLookAccess(access, chat.moveX(), chat.moveY() + chat.scrollShift(), chat.textLift());
	}

	/** The game moved its counter of lines scrolled, as it does for a new line while the player is scrolled up. */
	@Inject(method = "scrollChat", at = @At("TAIL"))
	private void lumio$scrolled(int lines, CallbackInfo info) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null && chat.isEnabled()) {
			chat.gameScrolled(lines);
		}
	}

	@Inject(method = "resetChatScroll", at = @At("TAIL"))
	private void lumio$scrollReset(CallbackInfo info) {
		ChatFeature chat = ModFeatures.CHAT;
		if (chat != null) {
			chat.gameScrollReset();
		}
	}

	/** A new line keeps the view when the chat is scrolled by a part of a line as well. */
	@ModifyExpressionValue(method = "addMessageToDisplayQueue", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/ChatComponent;chatScrollbarPos:I"))
	private int lumio$countedForNewLine(int counted) {
		ChatFeature chat = ModFeatures.CHAT;
		return chat == null || !chat.isEnabled() ? counted : chat.counterForNewLine(counted);
	}

	/** While the lines stand between two places, the row coming into view is drawn too. */
	@ModifyExpressionValue(method = "forEachLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"))
	private int lumio$rows(int perPage) {
		ChatFeature chat = ModFeatures.CHAT;
		return chat == null || !chat.isEnabled() ? perPage : chat.rowsDrawn(perPage);
	}

	@Shadow
	protected abstract int getWidth();

	@Shadow
	protected abstract int getHeight();

	@Shadow
	protected abstract int getLineHeight();

	/**
	 * The chat is laid out from the screen height passed here (40 above it). Where the mod's status bars are in the way, a smaller height is passed
	 * so the chat sits above them. Rendering and click handling both go through this method, so lines are clicked where they are shown.
	 */
	@ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int lumio$aboveBars(int screenHeight) {
		StatusBarsHudFeature bars = ModFeatures.STATUS;
		if (bars == null) {
			return screenHeight;
		}
		int bottom = screenHeight - 40;
		ChatFeature chat = ModFeatures.CHAT;
		return screenHeight - bars.chatLift(bottom, bottom - getHeight(), chat != null && chat.isEnabled() ? chat.liftWidth() : getWidth() + 8);
	}

	/** Every incoming chat message passes here (HEAD of addMessage): it gets a timestamp and replaces an identical earlier message. */
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component lumio$decorate(Component message) {
		// the add-ons read the lines here too, where they enter the chat, so they see exactly what the chat shows
		for (ChatListener listener : Addons.chatListeners()) {
			Guard.run("chat line reader of an add-on", () -> listener.onChatLine(message));
		}
		ChatFeature chat = ModFeatures.CHAT;
		if (chat == null) {
			return message;
		}
		chat.attach((ChatComponent) (Object) this);
		ChatFeature.Processed processed = chat.process(message);
		if (processed == null) {
			return message;
		}
		if (processed.remove() != null) {
			int index = -1;
			for (int i = 0; i < allMessages.size(); i++) {
				if (allMessages.get(i).content() == processed.remove()) {
					index = i;
					break;
				}
			}
			if (index >= 0) {
				GuiMessage removed = allMessages.get(index);
				Set<GuiMessage> gone = Collections.newSetFromMap(new IdentityHashMap<>());
				gone.add(removed);
				// The separator lines Hypixel puts above and below a message ("You cannot say the same message twice!" between two bars) arrive in the
				// same tick: remove them together with it, or every repeat leaves its two bars behind and the chat fills up with bars.
				for (int near : new int[]{index - 1, index + 1}) {
					if (near >= 0 && near < allMessages.size()) {
						GuiMessage neighbour = allMessages.get(near);
						if (chat.isSeparator(neighbour.content()) && Math.abs(neighbour.addedTime() - removed.addedTime()) <= 2) {
							gone.add(neighbour);
						}
					}
				}
				allMessages.removeIf(gone::contains);
				// Only remove their lines from the displayed lines. A full refresh re-wraps every message, which with a history of a thousand
				// messages would cost a good part of a frame for every repeated message.
				((ChatComponentAccessor) (Object) this).lumio$lines().removeIf(line -> gone.contains(line.parent()));
			}
		}
		return processed.shown();
	}
}
