package dev.tim1337.lumio.api.feature;

/** Where a key of a feature works, which decides which other keys it may clash with. */
public enum KeyPlace {
	/** While playing, with no screen open. */
	WORLD,
	/** In screens such as the inventory. */
	SCREENS
}
