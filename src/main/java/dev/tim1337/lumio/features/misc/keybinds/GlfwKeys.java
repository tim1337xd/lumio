package dev.tim1337.lumio.features.misc.keybinds;

/**
 * The numbers of keys in a config from before the game's key numbers changed: up to Minecraft 26.2 they are GLFW's, from 26.3 on SDL's. A keybind saved
 * since then has the key's name beside the number; one without it was saved with GLFW's number.
 */
final class GlfwKeys {
	/** Single keys, as pairs of GLFW's number and SDL's. */
	private static final int[][] SINGLE = {
		{32, 44}, {39, 52}, {44, 54}, {45, 45}, {46, 55}, {47, 56}, {48, 39}, {59, 51}, {61, 46}, {91, 47}, {92, 49}, {93, 48}, {96, 53},
		{256, 41}, {257, 40}, {258, 43}, {259, 42}, {260, 73}, {261, 76}, {262, 79}, {263, 80}, {264, 81}, {265, 82}, {266, 75}, {267, 78}, {268, 74}, {269, 77},
		{280, 57}, {281, 71}, {282, 83}, {283, 70}, {284, 72}, {320, 98}, {330, 99}, {331, 84}, {332, 85}, {333, 86}, {334, 87}, {335, 88}, {336, 103},
		{340, 225}, {341, 224}, {342, 226}, {343, 227}, {344, 229}, {345, 228}, {346, 230}, {347, 231}, {348, 101}
	};

	private GlfwKeys() {
	}

	/** @return the game's number for a key saved with GLFW's number; 0 for a number that is no key */
	static int toGame(int glfwKey) {
		//#if 26.3
		//$ return toSdl(glfwKey);
		//#else
		return glfwKey;
		//#endif
	}

	/** @return SDL's number for GLFW's number of a key; 0 for a number that is no key */
	static int toSdl(int key) {
		if (key >= 'A' && key <= 'Z') {
			return 4 + key - 'A';
		}
		if (key >= '1' && key <= '9') {
			return 30 + key - '1';
		}
		// F1 to F12, F13 to F24
		if (key >= 290 && key <= 301) {
			return 58 + key - 290;
		}
		if (key >= 302 && key <= 313) {
			return 104 + key - 302;
		}
		// 1 to 9 of the number block
		if (key >= 321 && key <= 329) {
			return 89 + key - 321;
		}
		for (int[] pair : SINGLE) {
			if (pair[0] == key) {
				return pair[1];
			}
		}
		return 0;
	}
}
