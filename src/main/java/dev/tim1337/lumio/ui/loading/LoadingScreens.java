package dev.tim1337.lumio.ui.loading;

import dev.tim1337.lumio.ModFeatures;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.gui.screens.worldselection.FileFixerProgressScreen;
import net.minecraft.network.chat.Component;

/**
 * The game's loading screens, drawn as the one loading view instead of their own way. Their logic stays the game's: the mixins only report what
 * the screen shows and replace its drawing. The buttons a screen offers (Cancel, Disconnect) are its own and keep working; they are moved below
 * the bar.
 */
public final class LoadingScreens {
	private static final int BUTTON_GAP = 4;

	private LoadingScreens() {
	}

	/** @return whether the loading screens are drawn by the mod */
	public static boolean enhanced() {
		return ModFeatures.MENUS.loadingEnhanced();
	}

	/** @return whether a screen is one of the game's loading screens */
	public static boolean isLoading(Screen screen) {
		return screen instanceof ConnectScreen || screen instanceof LevelLoadingScreen || screen instanceof GenericMessageScreen
			|| screen instanceof ProgressScreen || screen instanceof GenericWaitingScreen || screen instanceof ServerReconfigScreen
			|| screen instanceof FileFixerProgressScreen;
	}

	/**
	 * Draws a loading screen as the loading view, with what the game shows on it: its title, its step and its progress (negative for none),
	 * and its buttons below the bar.
	 */
	public static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, LoadingSession.Flow flow,
							Component title, Component step, float progress) {
		LoadingSession.report(flow, title, step, progress);
		LoadingScreenView.draw(graphics, 1f);
		int y = LoadingScreenView.buttonTop(screen.height);
		for (var child : screen.children()) {
			if (child instanceof AbstractButton button && button.visible) {
				button.setX((screen.width - button.getWidth()) / 2);
				button.setY(y);
				button.extractRenderState(graphics, mouseX, mouseY, partialTick);
				y += button.getHeight() + BUTTON_GAP;
			}
		}
	}
}
