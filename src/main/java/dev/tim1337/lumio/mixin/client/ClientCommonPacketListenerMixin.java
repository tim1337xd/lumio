package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.api.experimental.util.ServerTicks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;

/**
 * Counts server ticks from the ping the server sends every tick ({@link ServerTicks}). Only observed: the game answers it as always. The count is
 * taken after the packet was handed to the game's thread: the game calls this method first on the network thread, where the hand-off
 * throws, and once more on the game's thread, so this point is reached exactly once per packet. Pings with id 0 are skipped in {@link ServerTicks}.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
	@Inject(method = "handlePing", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void lumio$serverTick(ClientboundPingPacket packet, CallbackInfo ci) {
		ServerTicks.ping(packet.getId());
	}
}
