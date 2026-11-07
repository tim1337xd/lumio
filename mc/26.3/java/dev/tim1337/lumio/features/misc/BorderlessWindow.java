package dev.tim1337.lumio.features.misc;

import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/** What borderless fullscreen asks of the window system, here SDL, which the game uses from Minecraft 26.3 on. */
final class BorderlessWindow {
	private BorderlessWindow() {
	}

	/** @return the window's x, y, width and height */
	static int[] place(long window) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1);
			IntBuffer y = stack.mallocInt(1);
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);
			SDLVideo.SDL_GetWindowPosition(window, x, y);
			SDLVideo.SDL_GetWindowSize(window, width, height);
			return new int[]{x.get(0), y.get(0), width.get(0), height.get(0)};
		}
	}

	/** @return the x, y, width and height of the monitor with the most of the window on it; null when there is none */
	static int[] monitorAt(long window, int[] place) {
		int display = SDLVideo.SDL_GetDisplayForWindow(window);
		if (display == 0) {
			return null;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Rect bounds = SDL_Rect.malloc(stack);
			if (!SDLVideo.SDL_GetDisplayBounds(display, bounds)) {
				return null;
			}
			return new int[]{bounds.x(), bounds.y(), bounds.w(), bounds.h()};
		}
	}

	/** Takes the frame of the window away, or gives it back. */
	static void frameless(long window, boolean frameless) {
		SDLVideo.SDL_SetWindowBordered(window, !frameless);
		if (frameless) {
			SDLVideo.SDL_SetWindowAlwaysOnTop(window, false);
		}
	}

	/** Puts the window at a place and size. */
	static void put(long window, int x, int y, int width, int height) {
		SDLVideo.SDL_SetWindowSize(window, width, height);
		SDLVideo.SDL_SetWindowPosition(window, x, y);
	}
}
