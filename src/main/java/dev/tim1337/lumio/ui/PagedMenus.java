package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A page of a server menu opens as a new menu (the Wardrobe, the storage, the Auction House): the next page's screen comes right after the last one
 * closed, and its items only a moment later. To the player that is one screen turning a page, so until the new page's items are there the screen it
 * replaces is drawn in its place, as it was: no empty frame, no open animation, no fade. Only what is drawn changes; every packet, click and key goes
 * on as the game handles it. A menu counts as the same as the last one when their titles are the same without their page numbers and it opens within
 * {@link #CONTINUE_MILLIS} of the last one closing.
 */
public final class PagedMenus {
	/** The next page opens this soon after the last one closed. */
	static final long CONTINUE_MILLIS = 300L;
	/** The last page is drawn in the new one's place this long at most, while its items have not come. */
	static final long STAND_IN_MILLIS = 250L;
	/** Page numbers in a menu's title: "(1/3)", "Page 2", "#4". */
	private static final Pattern PAGE = Pattern.compile("\\(\\s*\\d+\\s*/\\s*\\d+\\s*\\)|\\bpage\\s*\\d+|#\\s*\\d+|\\d+");
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private static Screen previous;
	private static String previousFamily = "";
	private static long closedAt;
	private static AbstractContainerScreen<?> continuing;
	private static Screen standIn;
	private static long standUntil;
	private static boolean filled;
	private static boolean continuingIsNext;

	private PagedMenus() {
	}

	/** @return a menu's title without its page numbers and colors: what all its pages share */
	static String family(String title) {
		String plain = TextColors.noColors(title == null ? "" : title).toLowerCase(Locale.ROOT);
		return SPACES.matcher(PAGE.matcher(plain).replaceAll(" ")).replaceAll(" ").strip();
	}

	/** @return whether a menu opening now is the next page of the one closed then */
	static boolean nextPage(String closedFamily, long closedAtMillis, String openedFamily, long now) {
		return !closedFamily.isEmpty() && closedFamily.equals(openedFamily) && now - closedAtMillis <= CONTINUE_MILLIS;
	}

	/** A container screen closed: it may be the page before the next one. */
	public static void closed(AbstractContainerScreen<?> screen) {
		previous = screen;
		previousFamily = family(screen.getTitle().getString());
		closedAt = System.currentTimeMillis();
	}

	/** A container screen opened: the next page of the last one stands in for it until its items come. */
	public static void opened(AbstractContainerScreen<?> screen) {
		if (screen == continuing || screen == previous) {
			// laid out again (a resize), not opened
			return;
		}
		long now = System.currentTimeMillis();
		continuing = screen;
		filled = false;
		boolean next = previous != null && nextPage(previousFamily, closedAt, family(screen.getTitle().getString()), now);
		standIn = next ? previous : null;
		continuingIsNext = next;
		standUntil = now + STAND_IN_MILLIS;
		previous = null;
	}

	/** The server sent a menu's items: the page that waited for them is drawn as itself from now on. */
	public static void contents(int containerId) {
		if (continuing != null && continuing.getMenu().containerId == containerId) {
			filled = true;
			standIn = null;
		}
	}

	/** @return the screen to draw in place of this one while its items are still to come, or null to draw it as it is */
	public static Screen standIn(Screen screen) {
		if (standIn == null || screen != continuing) {
			return null;
		}
		if (filled || System.currentTimeMillis() > standUntil) {
			standIn = null;
			return null;
		}
		return standIn;
	}

	/** @return whether a screen is the next page of the menu before it: an add-on may leave out its opening animation */
	public static boolean nextPage(Screen screen) {
		return screen != null && screen == continuing && continuingIsNext;
	}
}
