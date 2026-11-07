package dev.tim1337.lumio.mixin.gui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.render.ScreenFade;
import dev.tim1337.lumio.ui.MacTitleScreen;
import dev.tim1337.lumio.ui.loading.LoadingScreenView;
import dev.tim1337.lumio.ui.loading.LoadingSession;
import dev.tim1337.lumio.ui.title.StartupTransition;
import dev.tim1337.lumio.util.InputTrace;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

/** Title screen replacement and screen cross-fades. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Shadow
	private Screen screen;

	/**
	 * Replaces the vanilla title screen with the mod's main menu when enabled, and the other way round when it is not, and the game's server list (opened by the game after leaving a
	 * server) with the main menu's own, as its Multiplayer button shows it: when the menu's lists are on and the warning about online play was
	 * confirmed. Only the vanilla screens are replaced, not ones provided by another mod.
	 */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen lumio$mainMenu(Screen screen) {
		if (screen == null) {
			// outside a world "no screen" means the main menu, which the game then makes itself, after this point: the mod's is given here instead,
			// such as when the settings close that were opened without a screen to go back to
			return Minecraft.getInstance().level == null && ModFeatures.MENUS.titleActive() ? new MacTitleScreen() : null;
		}
		if (!ModFeatures.MENUS.titleActive()) {
			// a screen going back to the mod's main menu after it was switched to the game's own, such as the settings it was opened from
			return screen instanceof MacTitleScreen ? new TitleScreen() : screen;
		}
		if (screen.getClass() == TitleScreen.class) {
			return new MacTitleScreen();
		}
		if (screen.getClass() == JoinMultiplayerScreen.class && ModFeatures.MENUS.listsEnhanced() && Minecraft.getInstance().options.skipMultiplayerWarning) {
			return MacTitleScreen.withServerList();
		}
		return screen;
	}

	/** Before the screen changes, keeps the frame showing the old screen so the new one can fade in through it. */
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void lumio$fadeFrom(Screen next, CallbackInfo ci) {
		if (InputTrace.on()) {
			InputTrace.note("screen " + name(screen) + " -> " + name(next));
		}
		ScreenFade.screenChanging(screen, next);
	}

	private static String name(Screen screen) {
		return screen == null ? "none" : screen.getClass().getSimpleName();
	}

	/** Draws the old screen's image over everything, as the last element before the cursor is applied. */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"))
	private void lumio$fadeOver(GuiGraphicsExtractor graphics, Window window, Operation<Void> original) {
		// the loading view ends when no loading screen drew this frame, and over the game it fades out on its own time
		Minecraft client = Minecraft.getInstance();
		LoadingSession.endOfFrame(client.level != null && client.gui.screen() == null);
		if (LoadingSession.ending()) {
			float alpha = LoadingSession.endingAlpha();
			if (alpha > 0f) {
				graphics.nextStratum();
				LoadingScreenView.draw(graphics, alpha);
			}
		}
		StartupTransition.draw(graphics);
		ScreenFade.draw(graphics);
		original.call(graphics, window);
	}
}
