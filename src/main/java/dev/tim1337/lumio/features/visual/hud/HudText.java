package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;

import java.util.List;

/**
 * A text from the server with its colors (a scoreboard line, a player's name), laid out in the menu's font built for its size, so it is drawn
 * crisp on whole screen pixels, and cut with "…" to a width. It is laid out again only when the font, the zoom or the width changes, not every
 * frame.
 */
final class HudText {
	private static final String ELLIPSIS = "…";

	private final FormattedText source;
	/** Its size (1 is the menu's body text) and whether it is in the semibold face. */
	private final float size;
	private final boolean semibold;
	/** The text laid out: runs in the menu font, and the symbols it lacks in the game's font (see {@link Ui#inMenuFontPieces}). */
	private List<Ui.Piece> laidOut;
	private float width;
	/** The glyph scale from font units to panel pixels, for the size. */
	private float unit = -1f;
	private float room = -1f;
	private boolean gameFont;

	HudText(FormattedText source) {
		this(source, 1f, false);
	}

	HudText(FormattedText source, float size, boolean semibold) {
		this.source = source;
		this.size = size;
		this.semibold = semibold;
	}

	FormattedText source() {
		return source;
	}

	private static boolean gameFontNow() {
		return ModFeatures.MENU == null || ModFeatures.MENU.gameFont();
	}

	private float unitNow(boolean game) {
		return game ? Ui.gameFontUnit(size) : Ui.menuFontUnit(size, semibold);
	}

	/** Lays the text out for the font and zoom in use now, cut to a width in panel pixels (0 for no limit). Call while the panel's zoom is set. */
	void layout(Font font, float maxWidth) {
		boolean game = gameFontNow();
		float nowUnit = unitNow(game);
		if (nowUnit == unit && game == gameFont && maxWidth == room && laidOut != null) {
			return;
		}
		unit = nowUnit;
		gameFont = game;
		room = maxWidth;
		FormattedText text = source;
		float full = measure(font, text);
		if (maxWidth > 0f && full > maxWidth) {
			// cut where the share of the width that fits ends, measured in the game font, and add an ellipsis
			float dots = measure(font, FormattedText.of(ELLIPSIS));
			int gameWidth = font.width(source);
			int keep = Math.max(0, Math.round(gameWidth * (maxWidth - dots) / full));
			text = FormattedText.composite(font.substrByWidth(source, keep), FormattedText.of(ELLIPSIS));
		}
		laidOut = pieces(text);
		width = Ui.piecesWidth(font, laidOut, symbolScale()) * unit;
	}

	private List<Ui.Piece> pieces(FormattedText text) {
		List<Ui.Piece> menu = gameFont ? null : Ui.inMenuFontPieces(text, size, semibold);
		return menu != null ? menu : List.of(new Ui.Piece(Language.getInstance().getVisualOrder(Ui.withSymbols(text)), false));
	}

	/** @return how much larger the symbols the menu font lacks are drawn in the game's font, to match the text's size */
	private float symbolScale() {
		return gameFont ? 1f : Ui.symbolScale(size, semibold);
	}

	private float measure(Font font, FormattedText text) {
		return Ui.piecesWidth(font, pieces(text), symbolScale()) * unit;
	}

	private float natural = -1f;
	private float naturalUnit = -1f;

	/** @return the width of the whole text, uncut, at its size in the font in use now: what a panel needs to show it without "…" */
	float naturalWidth(Font font) {
		boolean game = gameFontNow();
		float nowUnit = unitNow(game);
		if (nowUnit != naturalUnit) {
			naturalUnit = nowUnit;
			boolean before = gameFont;
			float unitBefore = unit;
			gameFont = game;
			unit = nowUnit;
			natural = measure(font, source);
			gameFont = before;
			unit = unitBefore;
		}
		return natural;
	}

	/** @return the laid out width in panel pixels */
	float width() {
		return width;
	}

	/** Draws the text with its top-left corner at x, y, snapped to whole screen pixels, in its colors; uncolored parts take the given color. */
	void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, int color, boolean shadow) {
		if (laidOut == null) {
			return;
		}
		float pixel = Ui.screenPixel();
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(x / pixel) * pixel, Math.round(y / pixel) * pixel);
		pose.scale(unit, unit);
		Ui.drawPieces(graphics, font, laidOut, symbolScale(), color, shadow);
		pose.popMatrix();
	}
}
