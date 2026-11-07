package dev.tim1337.lumio.util;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Mouse buttons as they really are. When the game reads its keys again (the mouse grabbed after a screen closed, the window focused again), it asks
 * the keyboard for every key, also for those on a mouse button, which the keyboard never has down: a held attack would be let go, and breaking a
 * block would stop until the next click. Here a key on a mouse button reads that button, so a held button stays held, as a held key does.
 */
public final class HeldButtons {
	private HeldButtons() {
	}

	/** @return whether the key is on a mouse button, and whether that button is down; null for a key of the keyboard */
	public static Boolean mouseKeyDown(KeyMapping mapping) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		if (key.getType() != InputConstants.Type.MOUSE) {
			return null;
		}
		return down(key.getValue());
	}

	/** Reads the keys on mouse buttons again, while the game has the mouse: after the window was focused again. */
	public static void resync() {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null || !client.mouseHandler.isMouseGrabbed()) {
			return;
		}
		for (KeyMapping mapping : client.options.keyMappings) {
			Boolean down = mouseKeyDown(mapping);
			if (down != null && down != mapping.isDown()) {
				mapping.setDown(down);
			}
		}
	}

	/** @return whether the mouse button with the game's number is down now, as the window system reports it */
	public static boolean down(int gameButton) {
		//#if 26.3
		//$ int sdlButton = switch (gameButton) {
		//$ 	case InputConstants.MOUSE_BUTTON_LEFT -> org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LEFT;
		//$ 	case InputConstants.MOUSE_BUTTON_RIGHT -> org.lwjgl.sdl.SDLMouse.SDL_BUTTON_RIGHT;
		//$ 	case InputConstants.MOUSE_BUTTON_MIDDLE -> org.lwjgl.sdl.SDLMouse.SDL_BUTTON_MIDDLE;
		//$ 	default -> gameButton;
		//$ };
		//$ return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer) null, null) & (1 << (sdlButton - 1))) != 0;
		//#else
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), gameButton) == InputConstants.PRESS;
		//#endif
	}
}
