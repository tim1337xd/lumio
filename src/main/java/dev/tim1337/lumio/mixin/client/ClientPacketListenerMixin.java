package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ChatListener;
import dev.tim1337.lumio.api.experimental.util.Guard;
import dev.tim1337.lumio.api.experimental.util.ServerParticles;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.command.ModCommands;
import dev.tim1337.lumio.features.visual.hud.InfoBarFeature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;

/** Reads incoming packets (action bar, chat, pong, world time) for the HUD and the add-ons' trackers, without modifying them; and keeps /lumio on the client. */
@Mixin(value = ClientPacketListener.class, priority = 500)
public abstract class ClientPacketListenerMixin {
	/**
	 * The mod's own command never leaves the client. Handled here, with a priority that runs before other mods, because a mod that rewrites commands on
	 * their way out could otherwise change it before the client commands get to see it, and it would go to the server as an unknown command.
	 */
	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void lumio$ownCommand(String command, CallbackInfo ci) {
		if (ModCommands.isOurs(command)) {
			ModCommands.run(command);
			ci.cancel();
		}
	}

	/**
	 * The action bar text as sent by the server: health and mana are read from it before other mods can modify it. The game calls this first on the
	 * network thread, which hands it on to the main thread where it is called again: it is read once, on the main thread.
	 */
	@Inject(method = "setActionBarText", at = @At("HEAD"))
	private void lumio$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		if (!Minecraft.getInstance().isSameThread()) {
			return;
		}
		if (ModFeatures.STATUS != null) {
			ModFeatures.STATUS.observe(packet.text());
		}
		for (ChatListener listener : Addons.chatListeners()) {
			Guard.run("action bar reader of an add-on", () -> listener.onActionBar(packet.text()));
		}
	}

	/** A menu's items came: the next page that waited for them is drawn as itself (see {@link dev.tim1337.lumio.ui.PagedMenus}). On the main thread only, past the hand-over. */
	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void lumio$pageFilled(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		dev.tim1337.lumio.ui.PagedMenus.contents(packet.containerId());
	}

	/** Some servers send the action bar as a system chat message flagged as overlay. */
	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void lumio$overlayChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		// The game calls this first on the network thread, which forwards it to the main thread where it is called again: it is read once, on the
		// main thread, otherwise the action bar would be read twice and every sack message counted twice.
		if (!Minecraft.getInstance().isSameThread()) {
			return;
		}
		Component message = packet.content();
		if (packet.overlay()) {
			if (ModFeatures.STATUS != null) {
				Guard.run("action bar reader", () -> ModFeatures.STATUS.observe(message));
			}
			for (ChatListener listener : Addons.chatListeners()) {
				Guard.run("action bar reader of an add-on", () -> listener.onActionBar(message));
			}
			return;
		}
		for (ChatListener listener : Addons.chatListeners()) {
			Guard.run("chat reader of an add-on", () -> listener.onServerMessage(message));
		}
	}

	/** The answer to the info bar's ping request: the time until it arrived is the ping. The game handles it on the network thread. */
	@Inject(method = "handlePongResponse", at = @At("HEAD"))
	private void lumio$pong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
		InfoBarFeature.onPong(packet.time());
	}

	/**
	 * The server sends the time about once per second, from which its tick rate (TPS) is derived. Taken once, on the network thread, as it
	 * arrives: the second call, on the main thread, comes a frame later.
	 */
	@Inject(method = "handleSetTime", at = @At("HEAD"))
	private void lumio$timeFromServer(ClientboundSetTimePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) {
			return;
		}
		InfoBarFeature.onServerTime(packet.gameTime());
	}

	/**
	 * The server's particles for add-ons ({@link ServerParticles}), once per packet: after the hand-off to the game's thread; skipped without a listener.
	 * A packet an add-on hides (the player chose not to see those particles) is not shown: only the view, nothing is sent.
	 */
	@Inject(method = "handleParticleEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
	private void lumio$serverParticles(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (ServerParticles.listened()) {
			//#if 26.3
			//$ boolean hidden = ServerParticles.received(packet.particle(), packet.x(), packet.y(), packet.z(), packet.count());
			//#else
			boolean hidden = ServerParticles.received(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ(), packet.getCount());
			//#endif
			if (hidden) {
				ci.cancel();
			}
		}
	}

	/** The server's game time for {@link ServerTicks}, once per packet: after the hand-off to the game's thread (as the tick pings). */
	@Inject(method = "handleSetTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void lumio$serverGameTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerTicks.serverTime(packet.gameTime());
	}
}
