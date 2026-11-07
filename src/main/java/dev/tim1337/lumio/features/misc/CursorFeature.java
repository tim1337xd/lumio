package dev.tim1337.lumio.features.misc;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;

/**
 * No cursor reset: when the server closes a menu and opens the next one (most SkyBlock menus, storage pages, also from the storage overlay), the
 * game briefly grabs the mouse in between and recenters the cursor. With this feature the cursor stays where it was.
 */
public class CursorFeature extends Feature {
	/** A menu opening within this time after one closed is treated as a follow-up, and the cursor is restored. */
	private static final long FOLLOW_MILLIS = 500L;

	private double savedX;
	private double savedY;
	private long savedAt;

	public CursorFeature() {
		super("no_cursor_reset", "No cursor reset", "When one menu follows another, the cursor stays where it was instead of jumping to the middle of the screen.", true);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("cursor");
		summary("Keeps the cursor in place between menus");
	}

	/** A menu closed and the game grabs the mouse: remembers the cursor position briefly. */
	public void grabbing(double x, double y) {
		savedX = x;
		savedY = y;
		savedAt = System.currentTimeMillis();
	}

	/** @return the cursor position to restore as a menu opens if it follows the one just closed; otherwise null, and it is centered */
	public double[] releasing() {
		if (!isEnabled() || savedAt == 0 || System.currentTimeMillis() - savedAt > FOLLOW_MILLIS) {
			return null;
		}
		savedAt = 0;
		return new double[]{savedX, savedY};
	}

	@Override
	public Feature homePage() {
		return ModFeatures.MENU;
	}
}
