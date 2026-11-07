package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.loading.LoadingScreens;
import dev.tim1337.lumio.ui.loading.LoadingSession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** The server connection screen, drawn as the loading view: its status (connecting, encrypting, logging in, joining) is the step. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
	@Shadow
	private Component status;

	/** Remembers the server being joined, for the title and the bottom line. Nothing about connecting changes. */
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void lumio$joining(Screen parent, Minecraft client, ServerAddress address, ServerData data, boolean quickPlay,
												TransferState transfer, CallbackInfo ci) {
		LoadingSession.joining(data == null ? null : data.name, address.toString());
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$view(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (LoadingScreens.enhanced()) {
			LoadingScreens.draw((Screen) (Object) this, graphics, mouseX, mouseY, partialTick, LoadingSession.Flow.SERVER, null, status, -1f);
			ci.cancel();
		}
	}
}
