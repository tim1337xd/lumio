package dev.tim1337.lumio.ui;

/**
 * The size of the menu window: the sidebar, the page, and the settings pane beside it, the same on every page. Plain numbers, so it is unit tested; all widths in menu pixels, a scale being real size per menu pixel over the GUI scale.
 */
final class MenuLayout {
	static final float MIN_ZOOM = 0.3f;
	/** The least the window takes on a small screen, and its widest, before it is scaled up. */
	static final int LEAST_WIDTH = 600;
	static final int MOST_WIDTH = 880;
	/** The share of the screen's width the window takes, the same at 1080p and at 4K: scaled up beyond its widest, narrower below it. */
	private static final float SCREEN_SHARE = 0.84f;
	/** The share of the screen's height the window takes at most, and its tallest and lowest height. */
	private static final float HEIGHT_SHARE = 0.92f;
	private static final int MOST_HEIGHT = 540;
	private static final int LEAST_HEIGHT = 400;
	private static final float MAX_AUTO_ZOOM = 2.5f;
	/** The menu's text must not get smaller than this many real pixels per menu pixel. */
	static final float MIN_REAL_SCALE = 1.2f;
	private static final float SCREEN_MARGIN = 8f;

	private MenuLayout() {
	}

	private static float clamp(float value, float least, float most) {
		return Math.max(least, Math.min(most, value));
	}

	static int width(int screenWidth) {
		return (int) clamp(Math.round(screenWidth * SCREEN_SHARE), LEAST_WIDTH, MOST_WIDTH);
	}

	static int height(int screenHeight) {
		return (int) clamp(Math.round(screenHeight * HEIGHT_SHARE), LEAST_HEIGHT, MOST_HEIGHT);
	}

	/** @return the scale at which the window takes its share of the screen's width: 1 where it fits without, more on a large screen */
	static float autoZoom(int screenWidth, int windowWidth) {
		return clamp(screenWidth * SCREEN_SHARE / windowWidth, 1f, MAX_AUTO_ZOOM);
	}

	/** @return the largest scale at which a window of this size fits on the screen */
	static float fit(int screenWidth, int screenHeight, int windowWidth, int windowHeight) {
		return Math.max(MIN_ZOOM, Math.min((screenWidth - SCREEN_MARGIN) / windowWidth, (screenHeight - SCREEN_MARGIN) / windowHeight));
	}

	/** @return the window's scale at a Window size of 100 % */
	static float defaultZoom(int screenWidth, int screenHeight) {
		int width = width(screenWidth);
		return Math.max(MIN_ZOOM, Math.min(autoZoom(screenWidth, width), fit(screenWidth, screenHeight, width, height(screenHeight))));
	}
}
