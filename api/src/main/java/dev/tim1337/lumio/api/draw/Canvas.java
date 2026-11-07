package dev.tim1337.lumio.api.draw;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Where an add-on draws: the menus' and panels' own drawing, with the UI fonts, smooth rounded shapes, rings, icons and the theme's accent.
 * Every color is 0xAARRGGBB and is faded by the current window opacity (the experimental {@code Draw.setAlpha}). Coordinates are GUI pixels.
 * A canvas is handed to a panel's {@code drawPanel}; for other drawing, make one with the experimental {@code Draw.hudCanvas}.
 */
public interface Canvas {
	/** @return the game's drawing context, for items and anything the canvas does not draw */
	GuiGraphicsExtractor graphics();

	/** @return the accent color of what is drawn, as 0xAARRGGBB */
	int accent();

	/** @return whether things move: off while the menu's animations are off */
	boolean animated();

	/** @return the seconds since the last frame, for animations */
	float deltaSeconds();

	void fill(int x, int y, int width, int height, int color);

	void rounded(int x, int y, int width, int height, float radius, int color);

	/** A rounded rectangle at fractional positions, with soft edges. */
	void roundedSmooth(float x, float y, float width, float height, float radius, int color);

	/** A rounded rectangle with a color fading from the top to the bottom. */
	void roundedGradient(int x, int y, int width, int height, float radius, int top, int bottom);

	/** The outline of a rounded rectangle. */
	void ring(int x, int y, int width, int height, float radius, int thickness, int color);

	void discAt(float centerX, float centerY, float diameter, int color);

	void ringAt(float centerX, float centerY, float diameter, float thickness, int color);

	/** A part of a ring: from and to are fractions of the full circle, clockwise from the top. */
	void arc(float centerX, float centerY, float radius, float thickness, float from, float to, int color);

	/** A segment of a chart ring: flat ends, with an even gap to the next segment; pass segments touching, from where one ends to where the next begins. */
	void arcSegment(float centerX, float centerY, float radius, float thickness, float from, float to, int color);

	/** @return whether arcs are drawn smoothly; if the graphics card rejects their shader, draw a simpler chart */
	boolean arcsAvailable();

	/** A soft glow fading out from its center. */
	void softLight(float centerX, float centerY, float radiusX, float radiusY, int color);

	void softLight(float centerX, float centerY, float radius, int color);

	/** One of the mod's icons by name ("star", "pickaxe", ...), tinted. */
	void icon(String name, int x, int y, int size, int color);

	/** A grid of item slots. */
	void slotGrid(int x, int y, int columns, int rows, int step, int cell, float radius, int color);

	void textAt(String text, float x, float y, int color);

	/** Text at a scale (such as the experimental {@code TextScale} steps), sharp at every GUI scale. */
	void textAtScaled(String text, float x, float y, float scale, int color);

	void textAtScaledBold(String text, float x, float y, float scale, int color);

	/** Text whose middle is at middleY. */
	void textMiddle(String text, int x, int middleY, int color);

	void textCentered(String text, int centerX, int middleY, int color);

	/** A small capitalized label above a group. */
	void sectionLabel(String text, float x, float y, int color);

	int width(String text);

	float widthScaled(String text, float scale);

	float boldWidthScaled(String text, float scale);

	/** @return the text, shortened with "…" to fit the width */
	String fit(String text, int maxWidth);

	String fitScaled(String text, float maxWidth, float scale);
}
