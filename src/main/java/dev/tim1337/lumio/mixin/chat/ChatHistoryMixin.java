package dev.tim1337.lumio.mixin.chat;

import dev.tim1337.lumio.ModFeatures;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.components.ChatComponent;

import java.util.List;

/**
 * Raises the chat history limit from the vanilla 100 messages to the configured size, so older messages can be scrolled back to.
 * <p>
 * The game checks "size > 100" and drops the oldest entry when true. The constant 100 is not modified here, because other mods (SkySoft) modify
 * it and only one modification can apply. Instead the size being compared is wrapped: it is reported as is only when it exceeds this mod's limit,
 * otherwise as 0. That way it cooperates with a mod that changes the constant, whatever value it uses.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHistoryMixin {
	/**
	 * Displayed lines, trimmed to the limit after a message arrives. A message can span several lines (long ones wrap, and Hypixel messages have
	 * separator bars above and below): trimming lines at the message limit left far fewer scrollable messages than were stored. Room is reserved
	 * for four lines per stored message.
	 */
	@WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 2), require = 0)
	private int lumio$shownLines(List<?> list, Operation<Integer> original) {
		return lumio$keep(original.call(list), Math.min(40_000, lumio$messages() * 4));
	}

	/** The stored messages, trimmed to the configured count. */
	@WrapOperation(method = "addMessageToQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 0), require = 0)
	private int lumio$messages(List<?> list, Operation<Integer> original) {
		return lumio$keep(original.call(list), lumio$messages());
	}

	private static int lumio$messages() {
		return ModFeatures.CHAT == null ? 100 : ModFeatures.CHAT.historyLines();
	}

	/** @return the size unchanged if it exceeds the mod's limit, so the oldest entry is dropped; otherwise 0, so nothing is */
	private static int lumio$keep(int size, int keep) {
		return size > keep ? size : 0;
	}
}
