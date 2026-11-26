package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.api.experimental.debug.DebugCategory;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.ui.title.RejoinGate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.chat.Component;

/** A probe only: logs each session join the game makes, so a click can be matched with what it led to. Nothing about the login changes. */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class SessionJoinProbeMixin {
	@Inject(method = "authenticateServer", at = @At("HEAD"))
	private void lumio$sessionJoin(String serverHash, CallbackInfoReturnable<Component> cir) {
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(DebugCategory.GENERAL, "Joining: session join {} since the last Rejoin click", RejoinGate.SHARED.countSessionJoin());
		}
	}
}
