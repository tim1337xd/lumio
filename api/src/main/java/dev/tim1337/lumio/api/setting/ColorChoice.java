package dev.tim1337.lumio.api.setting;

/** A color, as 0xRRGGBB, optionally with an opacity and the option to follow the menu's accent color. */
public interface ColorChoice extends AddonSetting<Integer> {
	/** Gives the color an opacity, starting at the given one. @return this */
	ColorChoice withOpacity(float defaultOpacity);

	/** Offers to use the menu's accent color instead. @return this */
	ColorChoice followsMenu();

	/** Takes away the choice of the menu's accent color, which every color offers: for one that carries a meaning (a rarity, good or bad). @return this */
	default ColorChoice ownColorOnly() {
		return this;
	}

	/** Shows it as a small swatch in a row with other colors of the same row label. @return this */
	ColorChoice inRow(String label);

	/** @return the opacity from 0 to 1 (1 without one) */
	float opacity();

	/** @return the color with its opacity, as 0xAARRGGBB; the menu's accent while it follows the menu */
	int argb();
}
