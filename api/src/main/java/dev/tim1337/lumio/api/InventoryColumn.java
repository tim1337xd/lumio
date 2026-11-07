package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.draw.Canvas;

/** A column of items an add-on shows in the restyled inventory, next to the player (SkyBlock's equipment): 20 wide and 74 high. */
public interface InventoryColumn {
	/** @return whether it is shown now; the player's box gets narrower for it */
	boolean shown();

	/** Draws the column with its top left corner here, on the inventory's canvas (the panels' look and the menu's accent). */
	void draw(Canvas canvas, int x, int y);
}
