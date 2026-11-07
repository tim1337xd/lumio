package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.loading.LoadingScreens;
import dev.tim1337.lumio.ui.loading.LoadingSession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The progress screen (preparing or creating a world), drawn as the loading view with its header, stage and percentage. Once it is done the
 * game's own drawing runs, which closes it.
 */
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin {
	@Shadow
	private Component header;

	@Shadow
	private Component stage;

	@Shadow
	private int progress;

	@Shadow
	private boolean stop;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$view(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!stop && LoadingScreens.enhanced()) {
			LoadingScreens.draw((Screen) (Object) this, graphics, mouseX, mouseY, partialTick, LoadingSession.Flow.WORLD, header, stage,
				progress > 0 ? progress / 100f : -1f);
			ci.cancel();
		}
	}
}
