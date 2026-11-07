package dev.tim1337.lumio.api;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;

/**
 * Hooks into the game's container screens (inventories, chests and menus), owned by Lumio: an add-on never mixes into these methods
 * itself. A screen an add-on takes over is drawn by it (background, layout, labels) while the game's slots stay as they are. Every hook only
 * reacts to the player's own input; nothing is clicked or sent on the player's behalf.
 */
public interface ScreenHooks {
	/** @return whether the add-on draws this container screen itself */
	default boolean takesOver(Screen screen) {
		return false;
	}

	/** The screen was laid out (its init); slots of a taken-over screen can be placed where they are drawn. */
	default void init(Screen screen) {
	}

	/** Draws the background of a taken-over screen in place of the game's. */
	default void drawBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	/** @return whether the add-on used the click (the screen then ignores it); {@code hovered} is the slot under the mouse, or null */
	default boolean mouseClicked(Screen screen, Slot hovered, double mouseX, double mouseY, int button) {
		return false;
	}

	/** @return the scroll result of a taken-over screen; asked only for one */
	default boolean mouseScrolled(Screen screen, double scrollY) {
		return false;
	}

	/** @return whether the add-on used the key */
	default boolean keyPressed(Screen screen, KeyEvent event) {
		return false;
	}

	/** @return whether the add-on used the typed character (a search field of its own has the keyboard) */
	default boolean charTyped(Screen screen, int codepoint) {
		return false;
	}

	/** @return whether a text field of the add-on has the keyboard on this screen, so keybinds leave the keys alone */
	default boolean typing(Screen screen) {
		return false;
	}
}
