package dev.tim1337.lumio.ui;

/**
 * Where the parts of the menu's top bar go: the search field in the middle of the window, as wide as it wants where there is room, narrower
 * between the name on the left and the close button on the right where there is not, and never over either. Plain layout math, tested without
 * the game.
 */
final class MenuBar {
	/** The gap the search keeps to the name and to the close button. */
	static final int GAP = 12;

	private MenuBar() {
	}

	/** Where the search field is: its left edge and width; a width of 0 when it has no room at all. */
	record Search(int x, int width) {
	}

	/**
	 * @param leftEnd  where the name and version on the left end
	 * @param rightEnd where the close button on the right starts
	 * @param center   the middle of the window
	 * @param wanted   the width the search wants
	 * @param least    the narrowest it may get before it is left out
	 * @return the search field: centered on the window where it fits so, else as close to it as the room allows
	 */
	static Search search(int leftEnd, int rightEnd, int center, int wanted, int least) {
		int left = leftEnd + GAP;
		int right = rightEnd - GAP;
		int room = right - left;
		if (room < least) {
			return new Search(left, 0);
		}
		int width = Math.min(wanted, room);
		int x = Math.max(left, Math.min(center - width / 2, right - width));
		return new Search(x, width);
	}
}
