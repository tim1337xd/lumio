package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.loading.LoadingScreens;
import dev.tim1337.lumio.ui.loading.LoadingSession;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A screen that waits for something with a message, drawn as the loading view; its button stays the game's. */
@Mixin(GenericWaitingScreen.class)
public abstract class GenericWaitingScreenMixin {
	@Shadow
	@Final
	private Component messageText;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$view(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (LoadingScreens.enhanced()) {
			Screen self = (Screen) (Object) this;
			LoadingScreens.draw(self, graphics, mouseX, mouseY, partialTick, LoadingSession.Flow.WORLD, self.getTitle(), messageText, -1f);
			ci.cancel();
		}
	}
}
