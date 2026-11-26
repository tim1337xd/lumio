package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.title.RejoinGate;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** The end of a connection opens Rejoin again; a login Mojang refused as too many gets one line under the game's text, and Rejoin waits. */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin {
	@Unique
	private static final int LUMIO_SIDE_MARGIN = 50;

	@Shadow
	@Final
	private DisconnectionDetails details;

	@Shadow
	@Final
	private LinearLayout layout;

	/** The screen is laid out anew on every resize: the wait starts only once. */
	@Unique
	private boolean lumio$counted;

	/** Right after the game's own reason (the title is the first child, the reason the second), so the line stands between it and the button. */
	@Inject(method = "init", at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private void lumio$busyLine(CallbackInfo ci) {
		boolean busy = RejoinGate.busyReason(details.reason().getString());
		if (!lumio$counted) {
			lumio$counted = true;
			if (busy) {
				RejoinGate.SHARED.loginBusy(Util.getMillis());
			} else {
				RejoinGate.SHARED.connectEnded();
			}
		}
		if (busy) {
			int width = ((Screen) (Object) this).width - LUMIO_SIDE_MARGIN;
			layout.addChild(new MultiLineTextWidget(Component.literal(RejoinGate.BUSY_LINE), Minecraft.getInstance().font).setMaxWidth(width).setCentered(true));
		}
	}
}
