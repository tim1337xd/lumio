package dev.tim1337.lumio.ui;

/** A color choice, for enum settings shown as a row of colored circles. */
public interface Swatch {
	/** @return the color as 0xRRGGBB */
	int rgb();

	/** @return whether the choice is a color of the player's own, set with a color picker: it is drawn with a rainbow ring, like a color well */
	default boolean picker() {
		return false;
	}
}
