package dev.tim1337.lumio.mixin.chat;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.screen.chat.ChatWrap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** A wrapped message's following rows start under its text: the game's helper is left alone for everything else that uses it. */
@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin {
	@WrapOperation(method = "splitLines", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"))
	private List<FormattedCharSequence> lumio$rows(FormattedText message, int width, Font font, Operation<List<FormattedCharSequence>> original) {
		return ModFeatures.CHAT != null && ModFeatures.CHAT.isEnabled() ? ChatWrap.rows(message, width, font) : original.call(message, width, font);
	}
}
