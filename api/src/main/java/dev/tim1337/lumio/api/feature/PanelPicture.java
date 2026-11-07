package dev.tim1337.lumio.api.feature;

import dev.tim1337.lumio.api.draw.Canvas;

/** Draws a small picture of a panel in one of its looks, for the look choice on the panel's settings card.  *
 * @deprecated no card is drawn any more
 */
@Deprecated
@FunctionalInterface

public interface PanelPicture<E> {
	void draw(Canvas canvas, E look, int x, int y, int width, int height, int accent);
}
