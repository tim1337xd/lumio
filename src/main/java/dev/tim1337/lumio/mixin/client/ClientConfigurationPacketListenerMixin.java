package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.mixin.accessor.ClientCommonPacketListenerAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;

/**
 * Keep server resource packs: tells it which server is being joined as its configuration starts (the game's current server is only known later,
 * after the server sent its packs), and removes packs kept from another server once the configuration ended.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerMixin {
	// the listener is made on the network thread; the game's thread takes it before any packet of the configuration, which come after it
	@Inject(method = "<init>", at = @At("RETURN"))
	private void lumio$joining(CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ServerData server = ((ClientCommonPacketListenerAccessor) this).lumio$serverData();
			Minecraft.getInstance().execute(() -> ModFeatures.KEEP_SERVER_PACKS.joining(server));
		}
	}

	// at the end, on the game's thread: the packet handler first hands itself over to it, and only that call returns normally
	@Inject(method = "handleConfigurationFinished", at = @At("RETURN"))
	private void lumio$configurationFinished(ClientboundFinishConfigurationPacket packet, CallbackInfo ci) {
		if (ModFeatures.KEEP_SERVER_PACKS != null) {
			ModFeatures.KEEP_SERVER_PACKS.configurationFinished();
		}
	}
}
