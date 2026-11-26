package dev.tim1337.lumio.hud;

/**
 * How a HUD element shows while a menu with slots is open (the inventory, a chest, the Auction House): the rules of the setting "While a menu
 * is open" and of each element's own "In menus". The elements are drawn behind the menu, so they never cover it or its tooltips. Other screens
 * (the chat, Lumio's menu, the pause menu, the HUD editor) are not menus in this sense and keep what they did. Plain logic, tested without the
 * game.
 */
public final class HudInMenus {
	/** The setting for all elements. */
	public enum Mode {
		DIMMED("Dimmed"),
		NORMAL("Normal"),
		HIDDEN("Hidden");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** An element's own choice. */
	public enum Own {
		FOLLOW("Follow the setting"),
		SHOW("Always show"),
		HIDE("Always hide");

		private final String label;

		Own(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** How an element is drawn now. */
	public enum Look {
		/** As without a menu. */
		NORMAL,
		/** In its place, with less of its light. */
		DIMMED,
		/** Not at all. */
		HIDDEN
	}

	/** An element this much under the menu's window is not drawn at all; less of it under the window fades it by that share. */
	static final float COVERED_SHARE = 0.5f;
	/** At full strength a dimmed element keeps this much of its opacity, and gets this much black over it. */
	private static final float LEAST_OPACITY = 0.25f;
	private static final float MOST_SHADE = 0.6f;

	private HudInMenus() {
	}

	/**
	 * @param menuOpen    a menu with slots is open (never the chat, Lumio's menu, the pause menu or the HUD editor)
	 * @param usedInMenus the element is meant to be used in an inventory (a profit panel): it stays as it is and takes its clicks
	 * @return how the element is drawn
	 */
	public static Look look(Mode mode, Own own, boolean menuOpen, boolean usedInMenus) {
		if (!menuOpen || own == Own.SHOW) {
			return Look.NORMAL;
		}
		if (own == Own.HIDE) {
			return Look.HIDDEN;
		}
		if (usedInMenus) {
			return Look.NORMAL;
		}
		return switch (mode) {
			case DIMMED -> Look.DIMMED;
			case NORMAL -> Look.NORMAL;
			case HIDDEN -> Look.HIDDEN;
		};
	}

	/**
	 * @param strength the dimming's strength, from 0 to 1
	 * @param covered  the share of the element under the menu's window, from 0 to 1
	 * @return how much of its opacity an element keeps; 0 when it is not drawn
	 */
	public static float opacity(Look look, float strength, float covered) {
		if (look == Look.HIDDEN) {
			return 0f;
		}
		// one used in the menu or always shown stays whole, also over the window's edge
		if (look == Look.NORMAL && covered <= 0f) {
			return 1f;
		}
		if (covered >= COVERED_SHARE && look != Look.NORMAL) {
			return 0f;
		}
		float own = look == Look.DIMMED ? 1f - (1f - LEAST_OPACITY) * clamp(strength) : 1f;
		return look == Look.NORMAL ? own : own * (1f - clamp(covered));
	}

	/** @return how much black lies over a dimmed element, so it is darker and not only fainter */
	public static float shade(Look look, float strength) {
		return look == Look.DIMMED ? MOST_SHADE * clamp(strength) : 0f;
	}

	/** @return the share of an element's box (x, y, width, height) that lies under a window's box */
	public static float covered(int[] element, int[] window) {
		if (window == null || element[2] <= 0 || element[3] <= 0) {
			return 0f;
		}
		int width = Math.min(element[0] + element[2], window[0] + window[2]) - Math.max(element[0], window[0]);
		int height = Math.min(element[1] + element[3], window[1] + window[3]) - Math.max(element[1], window[1]);
		if (width <= 0 || height <= 0) {
			return 0f;
		}
		return width * (float) height / (element[2] * (float) element[3]);
	}

	/** @return the largest share of an element's box that lies under one of the boxes (the menu's window, the overlays of add-ons) */
	public static float covered(int[] element, int[] window, java.util.List<int[]> areas) {
		float most = covered(element, window);
		for (int i = 0; i < areas.size(); i++) {
			most = Math.max(most, covered(element, areas.get(i)));
		}
		return most;
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}
}
