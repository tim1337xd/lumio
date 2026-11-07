package dev.tim1337.lumio.ui;

/**
 * A live preview drawn beside a page of the menu, showing what its settings do as they change. It is drawn every frame while the page is
 * shown, so it must not allocate or measure text it could keep.
 */
@FunctionalInterface
public interface PagePreview {
	/** Draws the preview into its panel, in menu pixels. */
	void draw(Ui ui, int x, int y, int width, int height);

	/** @return whether the panel takes the height of the page and not that of a miniature of the screen; it then draws its own background */
	default boolean tall() {
		return false;
	}

	/** @return the line under the panel */
	default String note() {
		return "Updates live while you change the settings.";
	}

	/** @return the line under the panel while a tab of the page is shown, by its title */
	default String note(String tab) {
		return note();
	}
}
