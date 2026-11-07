package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** The title at the top of the game's own menus, which the HUD's top elements have to leave room for. */
public final class ScreenTitles {
	/** How far down a menu's title reaches, in GUI pixels, and how far it reaches from the middle of the screen, as a share of its width. */
	private static final int TITLE_BOTTOM = 32;
	private static final float TITLE_HALF_WIDTH_SHARE = 0.2f;
	private static final String OWN_PACKAGE = "dev.tim1337.lumio.";

	private ScreenTitles() {
	}

	/** @return whether a screen is part of playing, with its own look: a chest, a book, a sign, the chat */
	public static boolean partOfPlaying(Screen screen) {
		return screen instanceof AbstractContainerScreen<?> || screen instanceof ChatScreen || screen instanceof BookViewScreen || screen instanceof BookEditScreen
			|| screen instanceof AbstractSignEditScreen || screen instanceof DeathScreen;
	}

	/** @return whether a menu of the game is open now, with a title at its top: the screens of the mod and its add-ons draw theirs elsewhere */
	public static boolean titleShown() {
		Screen screen = Minecraft.getInstance().gui.screen();
		return screen != null && !partOfPlaying(screen) && !screen.getClass().getName().startsWith(OWN_PACKAGE);
	}

	/**
	 * @return whether a screen is one of the game's full menus, in the mod's style: their rows and buttons reach the whole height of the screen, so the HUD
	 *         would show between and behind them. The pause menu and small questions leave room, and the HUD stays behind them.
	 */
	public static boolean hidesHud(Screen screen) {
		return (screen instanceof OptionsScreen || screen instanceof OptionsSubScreen || screen instanceof PackSelectionScreen || screen instanceof JoinMultiplayerScreen
			|| screen instanceof SelectWorldScreen || screen instanceof CreditsAndAttributionScreen) && ModFeatures.MENUS.buttonsActive();
	}

	/** @return whether a box on the screen lies on a menu's title */
	public static boolean over(int x, int y, int width, int height, int guiWidth) {
		float middle = guiWidth / 2f;
		float reach = guiWidth * TITLE_HALF_WIDTH_SHARE;
		return y < TITLE_BOTTOM && y + height > 0 && x < middle + reach && x + width > middle - reach;
	}
}
