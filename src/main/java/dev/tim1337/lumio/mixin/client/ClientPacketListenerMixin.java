package dev.tim1337.lumio.mixin.client;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ChatListener;
import dev.tim1337.lumio.api.experimental.util.Guard;
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

	/** The action bar text as sent by the server: health and mana are read from it before other mods can modify it. */
	@Inject(method = "setActionBarText", at = @At("HEAD"))
	private void lumio$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		if (ModFeatures.STATUS != null) {
			ModFeatures.STATUS.observe(packet.text());
		}
		for (ChatListener listener : Addons.chatListeners()) {
			Guard.run("action bar reader of an add-on", () -> listener.onActionBar(packet.text()));
		}
	}

	/** Some servers send the action bar as a system chat message flagged as overlay. */
	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void lumio$overlayChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
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
		// The game calls this first on the network thread, which forwards it to the main thread where it is called again: the add-ons only
		// read it once, on the main thread, otherwise every sack message would be counted twice.
		if (!Minecraft.getInstance().isSameThread()) {
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

	/** The server sends the time about once per second, from which its tick rate (TPS) is derived. */
	@Inject(method = "handleSetTime", at = @At("HEAD"))
	private void lumio$timeFromServer(ClientboundSetTimePacket packet, CallbackInfo ci) {
		InfoBarFeature.onServerTime(packet.gameTime());
	}
}
