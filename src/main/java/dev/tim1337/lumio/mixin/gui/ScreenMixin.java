package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.MenuBackground;
import dev.tim1337.lumio.ui.PagedMenus;
import dev.tim1337.lumio.ui.loading.LoadingScreens;
import dev.tim1337.lumio.ui.loading.LoadingSession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;

/** The mod's animated menu background behind out-of-world screens, and the loading view for the loading screens that draw no more than a message. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	/**
	 * The next page of a server menu whose items have not come yet: the page before is drawn in its place, as it was, so a page switch shows no empty
	 * frame and no opening (see {@link PagedMenus}). Clicks and keys still go to the new page.
	 */
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
	private void lumio$pageStandIn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Screen standIn = PagedMenus.standIn((Screen) (Object) this);
		if (standIn != null && standIn != (Object) this) {
			standIn.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, partialTick);
			ci.cancel();
		}
	}

	/**
	 * The message screens ("Saving world", "Joining world") and the server's reconfiguring screen have no drawing of their own beyond this one, so
	 * the loading view takes over here. Every other screen passes after one type check.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$loading(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Object self = this;
		if ((self instanceof GenericMessageScreen || self instanceof ServerReconfigScreen) && LoadingScreens.enhanced()) {
			Screen screen = (Screen) self;
			LoadingSession.Flow flow = self instanceof ServerReconfigScreen ? LoadingSession.Flow.SERVER : LoadingSession.Flow.WORLD;
			LoadingScreens.draw(screen, graphics, mouseX, mouseY, partialTick, flow, screen.getTitle(), screen.getTitle(), -1f);
			ci.cancel();
		}
	}

	/** Out-of-world screens get the mod's animated background instead of the panorama. */
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void lumio$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (ModFeatures.MENUS.backgroundActive()) {
			MenuBackground.draw(graphics, graphics.guiWidth(), graphics.guiHeight());
			ci.cancel();
		}
	}
}
