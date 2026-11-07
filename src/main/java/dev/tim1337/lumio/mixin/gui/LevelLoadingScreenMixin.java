package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ui.loading.LoadingScreens;
import dev.tim1337.lumio.ui.loading.LoadingSession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;

/**
 * The world loading screen (joining, respawning, changing dimension, a server's lobby switch), drawn as the loading view: no chunk map, the
 * game's own progress on the bar.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@Unique
	private static final Component LUMIO$TERRAIN = Component.translatable("multiplayer.downloadingTerrain");

	@Shadow
	private LevelLoadTracker loadTracker;

	@Shadow
	private float smoothedProgress;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$view(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (LoadingScreens.enhanced()) {
			float progress = loadTracker != null && loadTracker.hasProgress() ? smoothedProgress : -1f;
			LoadingScreens.draw((Screen) (Object) this, graphics, mouseX, mouseY, partialTick, LoadingSession.Flow.WORLD, LUMIO$TERRAIN,
				LUMIO$TERRAIN, progress);
			ci.cancel();
		}
	}
}
