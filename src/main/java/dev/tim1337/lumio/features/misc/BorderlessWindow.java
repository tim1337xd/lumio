package dev.tim1337.lumio.features.misc;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/** What borderless fullscreen asks of the window system. The window system differs between Minecraft versions (mc/26.3/java has the other one). */
final class BorderlessWindow {
	private BorderlessWindow() {
	}

	/** @return the window's x, y, width and height */
	static int[] place(long window) {
		int[] x = new int[1];
		int[] y = new int[1];
		int[] width = new int[1];
		int[] height = new int[1];
		GLFW.glfwGetWindowPos(window, x, y);
		GLFW.glfwGetWindowSize(window, width, height);
		return new int[]{x[0], y[0], width[0], height[0]};
	}

	/** @return the x, y, width and height of the monitor with the middle of the window on it, or of the first one; null when there is none */
	static int[] monitorAt(long window, int[] place) {
		long monitor = GLFW.glfwGetPrimaryMonitor();
		PointerBuffer monitors = GLFW.glfwGetMonitors();
		int centreX = place[0] + place[2] / 2;
		int centreY = place[1] + place[3] / 2;
		if (monitors != null) {
			for (int i = 0; i < monitors.remaining(); i++) {
				long candidate = monitors.get(i);
				int[] bounds = bounds(candidate);
				if (bounds != null && centreX >= bounds[0] && centreX < bounds[0] + bounds[2] && centreY >= bounds[1] && centreY < bounds[1] + bounds[3]) {
					return bounds;
				}
			}
		}
		return bounds(monitor);
	}

	private static int[] bounds(long monitor) {
		GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
		if (mode == null) {
			return null;
		}
		int[] x = new int[1];
		int[] y = new int[1];
		GLFW.glfwGetMonitorPos(monitor, x, y);
		return new int[]{x[0], y[0], mode.width(), mode.height()};
	}

	/** Takes the frame of the window away, or gives it back. */
	static void frameless(long window, boolean frameless) {
		GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, frameless ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
		if (frameless) {
			// a window filling a monitor must not minimize when another window is clicked, also on another monitor
			GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
			GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
		}
	}

	/** Puts the window at a place and size. */
	static void put(long window, int x, int y, int width, int height) {
		GLFW.glfwSetWindowSize(window, width, height);
		GLFW.glfwSetWindowPos(window, x, y);
	}
}
