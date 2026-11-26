package dev.tim1337.lumio.ui;

/**
 * What the HUD editor's label over an element says: the element's name, and its size while it is being resized. Nothing else: where an
 * element is snapped is told by the note at the bottom of the screen. Plain logic, tested without the game.
 */
final class HudEditLabel {
	static final String SEPARATOR = "  ·  ";

	private HudEditLabel() {
	}

	/**
	 * @param name     the element's name
	 * @param resizing whether the element is being resized now: by its corner, or by the wheel a moment ago
	 * @param size     its size as the editor shows it ("1.10×"), or what the element says instead of a size of its own
	 * @return the label's text
	 */
	static String text(String name, boolean resizing, String size) {
		return resizing && size != null && !size.isEmpty() ? name + SEPARATOR + size : name;
	}
}
