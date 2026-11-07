package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;

/** The server a connection belongs to, which is known from the start of its configuration, before the game's own current server is set. */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerAccessor {
	@Accessor("serverData")
	ServerData lumio$serverData();
}
