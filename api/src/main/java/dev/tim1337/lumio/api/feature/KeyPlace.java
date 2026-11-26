package dev.tim1337.lumio.api.feature;

/**
 * Where a key of a feature works, which decides which other keys it may clash with: two keys on the same key clash only where both would act.
 * A key that acts only in one menu is listed with {@link AddonFeature#addMenuKey}.
 */
public enum KeyPlace {
	/** While playing, with no screen open. */
	WORLD,
	/** In screens such as the inventory and other menus. */
	SCREENS,
	/** Only while a text field has the keyboard, where no other key acts. */
	TYPING
}
