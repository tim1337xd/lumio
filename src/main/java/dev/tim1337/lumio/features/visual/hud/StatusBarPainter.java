package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.motion.Critical;

/**
 * Draws one status bar in the look of the stack, in bar pixels with its top-left corner at 0, 0: Standard (a dark glass track with the value
 * inside, in white over the fill) or Slim (a thicker rounded bar with the value centered on it in an outlined tint, standing above it). Positions, sizes and colors follow the mockups.
 */
final class StatusBarPainter {
	/** Standard: bar height, corner radius, the track and its edge. */
	static final int BAR_HEIGHT = 11;
	private static final float BAR_RADIUS = 4f;
	private static final int TRACK = 0x9E0A0A10;
	private static final int TRACK_EDGE = 0x1FFFFFFF;
	private static final int HIGHLIGHT = 0x4DFFFFFF;
	private static final int LOWLIGHT = 0x1F000000;
	/** Standard: text inside the bar, the icon before it and the room around them. */
	private static final float VALUE_SCALE = 0.72f;
	/** The smallest a value shrinks to in a narrow bar, and the room it keeps before the defense pill is left out. */
	private static final float MIN_VALUE_SCALE = 0.5f;
	private static final float MIN_VALUE_ROOM = 16f;
	private static final float MAX_SCALE = 0.6f;
	private static final float MAX_OPACITY = 0.62f;
	private static final int ICON = 6;
	private static final float INSET = 4f;
	private static final float ICON_GAP = 2.5f;
	private static final int TEXT_SHADOW = 0x8C000000;
	/** The defense pill at the right end of the health bar: its inset, padding, fill, icon and text colors. */
	private static final float PILL_INSET = 2f;
	private static final float PILL_PAD = 3f;
	private static final int PILL_FILL = 0x47000000;
	private static final int DEFENSE_ICON = 0xFF7CF29A;
	private static final int DEFENSE_TEXT = 0xFFC9FFD8;
	private static final int DEFENSE_ICON_SIZE = 5;
	private static final float DEFENSE_SCALE = 0.6f;
	/** Standard experience bar: row height, track, and the level pill on it. */
	static final int XP_HEIGHT = 7;
	private static final float XP_TRACK = 3f;
	private static final float PILL_HEIGHT = 8f;
	private static final float LEVEL_SCALE = 0.6f;
	private static final int LEVEL_FILL = 0xFF16301A;
	private static final int LEVEL_EDGE = 0x8C8EEA5C;
	private static final int LEVEL_TEXT = 0xFFB9F59A;
	/** Room left and right of the level in its pill, and where the middle of digits lies in a line of text (9 units tall). */
	private static final float LEVEL_PAD = 4f;
	private static final float DIGIT_MIDDLE = 4f;
	private static final float GAIN_RISE = 2.5f;
	/** What is drawn on a white fill. */
	private static final int ON_WHITE = 0xFF26262C;
	/** The air icon: a ring this share of the icon size, with this line width. */
	private static final float AIR_RING = 0.85f;
	private static final float AIR_RING_LINE = 1.1f;
	/**
	 * Slim: the bars' heights (health, mana and the like, and experience), their dark track, the icon in front and its gap; the numbers on them:
	 * the game's font size, how high a digit is in it, how far above the bar's bottom their bottom lies, the outline's width, the gap before the
	 * maximum and how strong the maximum is; the experience ring.
	 */
	private static final float SLIM_BAR = 5f;
	private static final float SLIM_XP_BAR = 4f;
	private static final int SLIM_TRACK = 0x8C000000;
	private static final int SLIM_ICON = 5;
	private static final float SLIM_ICON_GAP = 2.5f;
	private static final float SLIM_NUMBER_SCALE = 1f;
	private static final float DIGIT_HEIGHT = 7f;
	private static final float SLIM_NUMBER_LIFT = 0.5f;
	private static final float OUTLINE = 1f;
	private static final float SLIM_MAX_GAP = 3f;
	private static final float SLIM_MAX_OPACITY = 0.62f;
	/** The defense outside the bar, beside it: smaller than the numbers on the bar. */
	private static final float SLIM_DEFENSE_SCALE = 0.75f;
	/** The smallest the numbers on a slim bar get to fit, against their usual size. */
	private static final float SLIM_LEAST_SCALE = 0.75f;
	private static final float XP_RING = 0.85f;
	private static final float XP_RING_LINE = 1.4f;
	/** The numbers' tint and outline from the bar's color: the share of its saturation and the least brightness of the tint, and the outline's saturation and brightness. */
	private static final float TINT_SATURATION = 0.6f;
	private static final float TINT_BRIGHTNESS = 1f;
	private static final float EDGE_SATURATION = 0.9f;
	private static final float EDGE_BRIGHTNESS = 0.22f;
	/** Low health: the pulsing edge around the track and the light on the fill. */
	private static final int LOW_EDGE = SystemColors.RED;
	private static final float LOW_LIGHT = 0.3f;

	/** How much larger the numbers are than the small size, as set in the settings; the heights of the bars and rows grow with them. */
	private static float text = 1f;
	/** How much a bar grows per step of text size: the standard bar, and the experience row and pill. */
	private static final float BAR_GROWTH = 8f;
	private static final float XP_GROWTH = 4f;
	private static final float PILL_GROWTH = 6f;

	private StatusBarPainter() {
	}

	/** Sets the text size for the bars drawn next: 1 is small (the size of before), 1.25 medium. */
	static void textSize(float factor) {
		text = factor;
	}

	/** @return the standard bar's height, which holds its value inside */
	static int barHeight() {
		return BAR_HEIGHT + Math.round((text - 1f) * BAR_GROWTH);
	}

	static int xpHeight() {
		return XP_HEIGHT + Math.round((text - 1f) * XP_GROWTH);
	}

	private static float pillHeight() {
		return PILL_HEIGHT + (text - 1f) * PILL_GROWTH;
	}

	/** @return the slim row's height: the bar at its bottom and its number, which stands above the bar, with its outline; the row above starts after it */
	static int slimHeight() {
		return numberRoom(SLIM_BAR);
	}

	static int slimXpHeight() {
		return numberRoom(SLIM_XP_BAR);
	}

	private static int numberRoom(float bar) {
		return (int) Math.ceil(Math.max(bar, SLIM_NUMBER_LIFT + DIGIT_HEIGHT * SLIM_NUMBER_SCALE * text + OUTLINE));
	}

	private static int icon() {
		return Math.round(ICON * text);
	}

	private static int slimIcon() {
		return Math.round(SLIM_ICON * text);
	}

	private static int defenseIcon() {
		return Math.round(DEFENSE_ICON_SIZE * text);
	}

	/** Draws a bar's icon; air is a small ring, like a bubble. */
	private static void icon(Ui ui, StatusBar.Kind kind, int x, int y, int size, int color) {
		if (kind == StatusBar.Kind.AIR) {
			ui.ringAt(x + size / 2f, y + size / 2f, size * AIR_RING, AIR_RING_LINE, color);
		} else {
			ui.icon(kind.icon, x, y, size, color);
		}
	}

	/** @return the gradient's top color for a fill color */
	private static int light(int color) {
		return Ui.mix(color, 0xFFFFFFFF, 0.14f);
	}

	/**
	 * Draws a bar in the standard look.
	 *
	 * @param low      how strong the low health pulse is now, 0 for none
	 * @param defense  how visible the defense pill is (faded in the editor while it is off), 0 for none
	 * @param maxShown how visible the maximum is
	 */
	static void standard(Ui ui, StatusBar bar, float width, int color, int extraColor, int extraText, float low, float defense, float maxShown,
		float alpha) {
		float h = barHeight();
		if (low > 0f) {
			ui.roundedSmooth(-1f, -1f, width + 2f, h + 2f, BAR_RADIUS + 1f, Ui.withOpacity(LOW_EDGE, (0.3f + 0.4f * low) * alpha));
		}
		ui.roundedSmooth(0f, 0f, width, h, BAR_RADIUS, Ui.withOpacity(TRACK, alpha));
		float trail = bar.trail.value();
		float fill = bar.fill.value();
		if (trail > fill + 0.003f) {
			ui.roundedSmooth(0f, 0f, width * trail, h, BAR_RADIUS, Ui.withOpacity(0xFFFFFFFF, bar.trailAlpha() * alpha));
		}
		float filled = width * fill;
		if (bar.extraFraction > 0f) {
			// absorption and overflow mana continue the bar after its own color, in gold and in lavender
			float end = Math.min(width, filled + width * bar.extraFraction);
			fillShape(ui, 0f, end, h, extraColor, alpha);
		}
		if (filled > 0.5f) {
			fillShape(ui, 0f, filled, h, color, alpha);
			if (low > 0f) {
				ui.softLight(filled / 2f, h / 2f, filled / 2f + 6f, h, Ui.withOpacity(color, LOW_LIGHT * low * alpha));
			}
		}
		ui.ring(0, 0, Math.round(width), (int) h, BAR_RADIUS, 1, Ui.withOpacity(TRACK_EDGE, alpha));

		// the pill with the defense at the right end, and the value from the left; the maximum is dropped first when there is no room
		float right = width - PILL_INSET;
		float defenseTextWidth = defense > 0f && !bar.defenseText.isEmpty() ? ui.boldWidthScaled(bar.defenseText, (DEFENSE_SCALE * text)) : 0f;
		float defensePill = PILL_PAD * 2 + defenseIcon() + 2f + defenseTextWidth;
		// a narrow bar keeps its value and leaves out the pill
		if (defenseTextWidth > 0f && INSET + icon() + ICON_GAP + MIN_VALUE_ROOM + defensePill <= width) {
			float pillWidth = defensePill;
			float pillX = right - pillWidth;
			float a = defense * alpha;
			ui.roundedSmooth(pillX, PILL_INSET, pillWidth, h - 2 * PILL_INSET, 2.5f, Ui.withOpacity(PILL_FILL, a));
			ui.icon("bar_shield", Math.round(pillX + PILL_PAD), Math.round((h - defenseIcon()) / 2f), defenseIcon(), Ui.withOpacity(DEFENSE_ICON, a));
			ui.textAtScaledBold(bar.defenseText, pillX + PILL_PAD + defenseIcon() + 2f, h / 2f - 4.5f * (DEFENSE_SCALE * text), (DEFENSE_SCALE * text),
				Ui.withOpacity(DEFENSE_TEXT, a));
			right = pillX - 2f;
		}
		float x = INSET;
		icon(ui, bar.kind, Math.round(x), Math.round((h - icon()) / 2f), icon(), Ui.withOpacity(0xFFFFFFFF, alpha));
		x += icon() + ICON_GAP;
		float room = right - x;
		float valueScale = (VALUE_SCALE * text);
		float valueWidth = ui.boldWidthScaled(bar.valueText, valueScale);
		if (valueWidth > room) {
			// a single value that does not fit shrinks
			valueScale = Math.max((MIN_VALUE_SCALE * text), valueScale * room / valueWidth);
			valueWidth = ui.boldWidthScaled(bar.valueText, valueScale);
		}
		// still too wide at the smallest size: the single value ends with "…"
		String value = valueWidth > room ? ui.fitScaled(bar.valueText, Math.max(0f, room), valueScale) : bar.valueText;
		shadowed(ui, value, x, h / 2f - 4.5f * valueScale, valueScale, Ui.withOpacity(bar.valueRgb == 0 ? 0xFFFFFFFF : 0xFF000000 | bar.valueRgb, alpha), alpha);
		if (bar.kind == StatusBar.Kind.SPEED && filled > 0.5f) {
			// the fill of this bar is white, so what lies on it is drawn again in dark, as far as the fill goes
			ui.graphics().enableScissor(0, 0, Math.round(filled), Math.round(h));
			icon(ui, bar.kind, Math.round(INSET), Math.round((h - icon()) / 2f), icon(), Ui.withOpacity(ON_WHITE, alpha));
			ui.textAtScaledBold(value, x, h / 2f - 4.5f * valueScale, valueScale, Ui.withOpacity(ON_WHITE, alpha));
			ui.graphics().disableScissor();
		}
		// after the value: the absorption or overflow ("+375", in its soft color), or the maximum ("/ 20", dim)
		float shownMax = bar.extraInText ? 1f : maxShown;
		if (shownMax > 0f && !bar.maxText.isEmpty()) {
			float maxX = x + valueWidth + 2f;
			if (maxX + ui.boldWidthScaled(bar.maxText, (MAX_SCALE * text)) <= right) {
				int maxColor = bar.extraInText ? extraText : Ui.withOpacity(0xFFFFFFFF, MAX_OPACITY * maxShown);
				shadowed(ui, bar.maxText, maxX, h / 2f - 4.5f * (MAX_SCALE * text) + 0.4f, (MAX_SCALE * text), Ui.withOpacity(maxColor, alpha), shownMax * alpha);
			}
		}
	}

	/** @return where the defense pill is in a standard bar (x and width), for clicking it in the editor; null without one */
	static float[] defensePill(Ui ui, StatusBar bar, float width) {
		if (bar.defenseText.isEmpty()) {
			return null;
		}
		float pillWidth = PILL_PAD * 2 + defenseIcon() + 2f + ui.boldWidthScaled(bar.defenseText, (DEFENSE_SCALE * text));
		return new float[]{width - PILL_INSET - pillWidth, pillWidth};
	}

	/** Fills a part of the bar in its color, lighter at the top, with a thin highlight along the top and a faint shade along the bottom. */
	private static void fillShape(Ui ui, float x, float width, float h, int color, float alpha) {
		ui.roundedSmooth(x, 0f, width, h, BAR_RADIUS, Ui.withOpacity(color, alpha));
		ui.roundedSmooth(x, 0f, width, h / 2f + BAR_RADIUS, BAR_RADIUS, Ui.withOpacity(light(color), 0.55f * alpha));
		if (width > 2 * BAR_RADIUS) {
			ui.roundedSmooth(x + BAR_RADIUS - 1f, 0.5f, width - 2 * BAR_RADIUS + 2f, 1f, 0.5f, Ui.withOpacity(HIGHLIGHT, alpha));
			ui.roundedSmooth(x + BAR_RADIUS - 1f, h - 1.5f, width - 2 * BAR_RADIUS + 2f, 1f, 0.5f, Ui.withOpacity(LOWLIGHT, alpha));
		}
	}

	private static void shadowed(Ui ui, String text, float x, float y, float scale, int color, float alpha) {
		ui.textAtScaledBold(text, x + 0.5f, y + 0.6f, scale, Ui.withOpacity(TEXT_SHADOW, alpha));
		ui.textAtScaledBold(text, x, y, scale, color);
	}

	/**
	 * Draws a bar in the slim look: a small icon in front, then a rounded bar, and the value centered on it in the bar's own tint with a dark
	 * outline, so large that its lower part lies on the bar and its upper part stands above it; the maximum after it, dimmer. The defense stands in
	 * green at the bar's right end, or, when it does not fit there beside the value and the maximum, small just right of the bar, outside it as the
	 * icon is on its left. Nothing is left out: numbers too wide for the bar are shortened ("12.4k / 12.5k").
	 */
	static void slim(Ui ui, StatusBar bar, float width, int color, int extraColor, int extraText, float low, float defense, float maxShown, float alpha) {
		float h = SLIM_BAR;
		float barY = slimHeight() - h;
		float barX = slimIcon() + SLIM_ICON_GAP;
		float scale = SLIM_NUMBER_SCALE * text;
		float smallScale = SLIM_DEFENSE_SCALE * text;

		// where the numbers go, worked out before the bar is drawn, as the defense outside makes the bar narrower
		boolean max = (maxShown > 0f || bar.extraInText) && !bar.maxText.isEmpty();
		boolean def = defense > 0f && !bar.defenseText.isEmpty();
		String value = bar.valueText;
		String after = max ? bar.maxText : "";
		float barWidth = Math.max(1f, width - barX);
		float defWidth = def ? ui.boldWidthScaled(bar.defenseText, scale) + 2 * OUTLINE : 0f;
		boolean defOutside = def && group(ui, value, after, scale) + defWidth + SLIM_MAX_GAP > barWidth - 2 * OUTLINE;
		float outsideWidth = 0f;
		if (defOutside) {
			outsideWidth = SLIM_ICON_GAP + ui.boldWidthScaled(bar.defenseText, smallScale) + 2 * OUTLINE;
			barWidth = Math.max(1f, barWidth - outsideWidth);
		}
		float inside = barWidth - 2 * OUTLINE - (def && !defOutside ? defWidth + SLIM_MAX_GAP : 0f);
		if (group(ui, value, after, scale) > inside) {
			value = bar.valueShort;
			after = max ? bar.maxShort : "";
		}
		// still too wide in short: a little smaller, as far as it stays readable
		float wide = group(ui, value, after, scale);
		if (wide > inside && wide > 0f) {
			scale = Math.max(SLIM_LEAST_SCALE * text, scale * inside / wide);
		}

		icon(ui, bar.kind, 0, Math.round(barY + (h - slimIcon()) / 2f), slimIcon(), Ui.withOpacity(color, alpha));
		if (low > 0f) {
			ui.roundedSmooth(barX - 1f, barY - 1f, barWidth + 2f, h + 2f, (h + 2f) / 2f, Ui.withOpacity(LOW_EDGE, (0.3f + 0.4f * low) * alpha));
		}
		ui.roundedSmooth(barX, barY, barWidth, h, h / 2f, Ui.withOpacity(SLIM_TRACK, alpha));
		float trail = bar.trail.value();
		float fill = bar.fill.value();
		if (trail > fill + 0.003f) {
			ui.roundedSmooth(barX, barY, barWidth * trail, h, h / 2f, Ui.withOpacity(0xFFFFFFFF, bar.trailAlpha() * alpha));
		}
		float filled = barWidth * fill;
		if (bar.extraFraction > 0f) {
			ui.roundedSmooth(barX, barY, Math.min(barWidth, filled + barWidth * bar.extraFraction), h, h / 2f, Ui.withOpacity(extraColor, alpha));
		}
		if (filled > 0.5f) {
			ui.roundedSmooth(barX, barY, filled, h, h / 2f, Ui.withOpacity(color, alpha));
			ui.roundedSmooth(barX, barY, filled, h / 2f, h / 4f, Ui.withOpacity(light(color), 0.5f * alpha));
			if (low > 0f) {
				ui.softLight(barX + filled / 2f, barY + h / 2f, filled / 2f + 6f, h, Ui.withOpacity(color, LOW_LIGHT * low * alpha));
			}
		}

		// the value centered on the bar with what follows it; beside the defense inside the bar, in the room left of it when they do not fit centered
		float textY = barY + h - SLIM_NUMBER_LIFT - DIGIT_HEIGHT * scale;
		int fillColor = bar.valueRgb == 0 ? numberTint(color) : 0xFF000000 | bar.valueRgb;
		int edge = numberEdge(bar.valueRgb == 0 ? color : 0xFF000000 | bar.valueRgb);
		float valueWidth = ui.boldWidthScaled(value, scale);
		float group = group(ui, value, after, scale);
		float x = barX + (barWidth - group) / 2f;
		if (def && !defOutside && x + group > barX + OUTLINE + inside) {
			x = barX + OUTLINE + (inside - group) / 2f;
		}
		outlined(ui, value, x, textY, scale, fillColor, edge, alpha);
		if (max) {
			int maxFill = bar.extraInText ? extraText : fillColor;
			int maxEdge = bar.extraInText ? numberEdge(extraText) : edge;
			float a = bar.extraInText ? alpha : SLIM_MAX_OPACITY * maxShown * alpha;
			outlined(ui, after, x + valueWidth + SLIM_MAX_GAP, textY, scale, maxFill, maxEdge, a);
		}
		if (def) {
			float a = defense * alpha;
			if (defOutside) {
				float smallY = barY + h / 2f - DIGIT_HEIGHT * smallScale / 2f;
				outlined(ui, bar.defenseText, barX + barWidth + SLIM_ICON_GAP + OUTLINE, smallY, smallScale, numberTint(DEFENSE_ICON), numberEdge(DEFENSE_ICON), a);
			} else {
				outlined(ui, bar.defenseText, barX + barWidth - defWidth + OUTLINE, textY, scale, numberTint(DEFENSE_ICON), numberEdge(DEFENSE_ICON), a);
			}
		}
	}

	/** @return how wide the value and what follows it are together */
	private static float group(Ui ui, String value, String after, float scale) {
		return ui.boldWidthScaled(value, scale) + (after.isEmpty() ? 0f : SLIM_MAX_GAP + ui.boldWidthScaled(after, scale));
	}

	/** Draws text with a dark outline one pixel wide all around each glyph, in eight directions, as the game's outlined text is drawn. */
	private static void outlined(Ui ui, String text, float x, float y, float scale, int fill, int edge, float alpha) {
		int dark = Ui.withOpacity(edge, alpha);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					ui.textAtScaledBold(text, x + dx * OUTLINE, y + dy * OUTLINE, scale, dark);
				}
			}
		}
		ui.textAtScaledBold(text, x, y, scale, Ui.withOpacity(fill, alpha));
	}

	/** @return the light tint the numbers on a bar of this color are drawn in: its hue, lighter and softer */
	static int numberTint(int color) {
		float[] hsb = java.awt.Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, null);
		return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], hsb[1] * TINT_SATURATION, Math.max(hsb[2], TINT_BRIGHTNESS)) & 0xFFFFFF;
	}

	/** @return the very dark shade of a color's hue that outlines the numbers */
	static int numberEdge(int color) {
		float[] hsb = java.awt.Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, null);
		return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], EDGE_SATURATION, EDGE_BRIGHTNESS) & 0xFFFFFF;
	}

	/** Draws the experience bar in the standard look: a thin track with the level in a pill on its middle, glowing after a gain. */
	static void standardXp(Ui ui, StatusBar bar, float width, int color, float alpha) {
		float trackY = (xpHeight() - XP_TRACK) / 2f;
		ui.roundedSmooth(0f, trackY, width, XP_TRACK, XP_TRACK / 2f, Ui.withOpacity(TRACK, alpha));
		ui.roundedSmooth(0f, trackY, width * bar.fill.value(), XP_TRACK, XP_TRACK / 2f, Ui.withOpacity(color, alpha));
		ui.roundedSmooth(0f, trackY, width * bar.fill.value(), XP_TRACK / 2f, XP_TRACK / 4f, Ui.withOpacity(light(color), 0.5f * alpha));
		float glow = gainGlow(bar);
		// The pill is placed in whole screen pixels, centered on the bar's own center: the room left and right of it (and above and below) is
		// made equal in screen pixels, so it never sits a pixel off, whatever the width and the GUI scale. The number is centered by its glyphs.
		float pixel = Ui.screenPixel();
		int barPixels = Math.round(width / pixel);
		int rowPixels = Math.round(xpHeight() / pixel);
		float ink = ui.boldInkWidthScaled(bar.levelText, (LEVEL_SCALE * text));
		int pillPixels = evenWith(barPixels, Math.round((ink + 2 * LEVEL_PAD) / pixel));
		int pillHeightPixels = evenWith(rowPixels, Math.round(pillHeight() / pixel));
		float pillX = (barPixels - pillPixels) / 2 * pixel;
		float pillY = (rowPixels - pillHeightPixels) / 2 * pixel;
		float pillWidth = pillPixels * pixel;
		float pillHeight = pillHeightPixels * pixel;
		if (glow > 0f) {
			ui.softLight(width / 2f, xpHeight() / 2f, pillWidth / 2f + 7f, pillHeight(), Ui.withOpacity(color, 0.55f * glow * alpha));
		}
		// the edge as the pill itself, and the fill one screen pixel inside it: both on the same pixels, unlike an outline drawn in menu pixels
		ui.roundedSmooth(pillX, pillY, pillWidth, pillHeight, pillHeight / 2f, Ui.withOpacity(Ui.mix(LEVEL_EDGE, 0xFFB9F59A, glow), alpha));
		ui.roundedSmooth(pillX + pixel, pillY + pixel, pillWidth - 2 * pixel, pillHeight - 2 * pixel, pillHeight / 2f - pixel,
			Ui.withOpacity(LEVEL_FILL, alpha));
		float textX = pillX + (pillWidth - ink) / 2f;
		float textY = pillY + pillHeight / 2f - DIGIT_MIDDLE * (LEVEL_SCALE * text);
		ui.textAtScaledBold(bar.levelText, textX, textY, (LEVEL_SCALE * text), Ui.withOpacity(LEVEL_TEXT, alpha));
		drawGain(ui, bar, pillX + pillWidth + 4f, pillY, LEVEL_TEXT, alpha);
	}

	/** @return a size in screen pixels made odd or even like the room it is centered in, so the room left over splits evenly */
	private static int evenWith(int room, int size) {
		return (room - size) % 2 == 0 ? size : size + 1;
	}

	/** Draws the experience bar in the slim look: a ring in front, a thin rounded bar, and the level centered on it in the bar's tint with a dark outline. */
	static void slimXp(Ui ui, StatusBar bar, float width, int color, float alpha) {
		float h = SLIM_XP_BAR;
		float barY = slimXpHeight() - h;
		float barX = slimIcon() + SLIM_ICON_GAP;
		float barWidth = Math.max(1f, width - barX);
		float ring = slimIcon();
		ui.ringAt(ring / 2f, barY + h / 2f, ring * XP_RING, XP_RING_LINE, Ui.withOpacity(color, alpha));
		ui.roundedSmooth(barX, barY, barWidth, h, h / 2f, Ui.withOpacity(SLIM_TRACK, alpha));
		ui.roundedSmooth(barX, barY, barWidth * bar.fill.value(), h, h / 2f, Ui.withOpacity(color, alpha));
		float glow = gainGlow(bar);
		float scale = SLIM_NUMBER_SCALE * text;
		float ink = ui.boldWidthScaled(bar.levelText, scale);
		float x = barX + (barWidth - ink) / 2f;
		float textY = barY + h - SLIM_NUMBER_LIFT - DIGIT_HEIGHT * scale;
		if (glow > 0f) {
			ui.softLight(barX + barWidth / 2f, barY, ink / 2f + 6f, DIGIT_HEIGHT * scale, Ui.withOpacity(color, 0.5f * glow * alpha));
		}
		outlined(ui, bar.levelText, x, textY, scale, numberTint(color), numberEdge(color), alpha);
		drawGain(ui, bar, x + ink + 4f, textY, numberTint(color), alpha);
	}

	/** @return how strongly the level glows after a gain, fading out */
	private static float gainGlow(StatusBar bar) {
		float since = bar.sinceGain();
		return since >= StatusBar.GAIN_SECONDS ? 0f : 1f - since / StatusBar.GAIN_SECONDS;
	}

	/** Draws the gained experience floating up a little from beside the level and fading out. */
	private static void drawGain(Ui ui, StatusBar bar, float x, float y, int color, float alpha) {
		float since = bar.sinceGain();
		if (since >= StatusBar.GAIN_SECONDS || bar.gainText.isEmpty()) {
			return;
		}
		float rise = Critical.progress(since, 0.5f) * GAIN_RISE;
		float fade = Math.min(1f, (StatusBar.GAIN_SECONDS - since) / 0.5f) * Math.min(1f, since / 0.12f);
		shadowed(ui, bar.gainText, x, y - rise, (LEVEL_SCALE * text), Ui.withOpacity(color, fade * alpha), fade * alpha);
	}
}
