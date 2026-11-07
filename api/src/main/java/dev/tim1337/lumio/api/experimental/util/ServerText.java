package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/**
 * Cleans text a server sends for a panel on a dark background. Servers write colors both as styles and as legacy codes inside the text ("§d§lRNG
 * Meter"): the codes become styles, their state starts fresh at every segment (so a color never leaks into the next part or line), stray
 * formatting characters are dropped. A color stays exactly as it is sent; only one too dark to read is helped: black and dark gray, which have no hue to keep, take the secondary text color, and a very dark color (dark blue) is
 * lightened as far as needed, keeping its hue. Dark red and dark purple can be read as they are.
 */
public final class ServerText {
	/** Colors without a hue darker than this (relative luminance) are too dark to read on the panel. */
	private static final float LEAST_LUMINANCE = 0.10f;
	/** A color with a hue is lightened up to this: below dark red, which stays as it is sent. */
	private static final float LEAST_COLORED_LUMINANCE = 0.06f;
	/** How far apart a color's channels are at least to count as having a hue. */
	private static final int LEAST_CHROMA = 24;
	private static final float LIGHTEN_STEP = 1.04f;
	private static final int MOST_LIGHTEN_STEPS = 60;

	private ServerText() {
	}

	/**
	 * @param secondary the color too dark parts take, as RGB
	 * @return the text with its colors made readable; an empty text for a line with nothing visible in it
	 */
	public static Component clean(Component text, int secondary) {
		MutableComponent out = Component.empty();
		text.visit((style, part) -> {
			appendSegment(out, style, part, secondary);
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** @return whether a line shows nothing: empty, only spaces, or only formatting codes (a spacer line) */
	public static boolean blank(Component text) {
		String plain = ChatFormatting.stripFormatting(text.getString());
		return plain == null || plain.isBlank();
	}

	private static void appendSegment(MutableComponent out, Style base, String part, int secondary) {
		Style style = readable(base, secondary);
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < part.length(); i++) {
			char c = part.charAt(i);
			if (c == ChatFormatting.PREFIX_CODE) {
				flush(out, run, style);
				ChatFormatting code = i + 1 < part.length() ? ChatFormatting.getByCode(part.charAt(i + 1)) : null;
				i++;
				style = apply(style, base, code, secondary);
				continue;
			}
			run.append(c);
		}
		flush(out, run, style);
	}

	private static void flush(MutableComponent out, StringBuilder run, Style style) {
		if (!run.isEmpty()) {
			out.append(Component.literal(run.toString()).setStyle(style));
			run.setLength(0);
		}
	}

	/** @return the style after a legacy code: a color starts a fresh style, a format adds to it, a reset goes back to the segment's own style */
	private static Style apply(Style style, Style base, ChatFormatting code, int secondary) {
		if (code == null) {
			return style;
		}
		if (code == ChatFormatting.RESET) {
			return readable(base, secondary);
		}
		TextColor color = TextColor.fromLegacyFormat(code);
		if (color != null) {
			return readable(Style.EMPTY.withColor(color), secondary);
		}
		return switch (code) {
			case BOLD -> style.withBold(true);
			case ITALIC -> style.withItalic(true);
			case UNDERLINE -> style.withUnderlined(true);
			case STRIKETHROUGH -> style.withStrikethrough(true);
			// scrambled text is not readable on a panel: shown plain
			default -> style;
		};
	}

	/** @return the style with a too dark color made readable, and without scrambling */
	private static Style readable(Style style, int secondary) {
		Style out = style.withObfuscated(false);
		TextColor color = out.getColor();
		if (color != null) {
			int rgb = color.getValue();
			int shown = readableColor(rgb, secondary);
			if (shown != rgb) {
				out = out.withColor(shown);
			}
		}
		return out;
	}

	/** @return a color as it can be read on a dark panel: as it is if it can be; a very dark one with a hue lighter with its hue; a dark one without a hue the secondary color */
	static int readableColor(int rgb, int secondary) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		boolean hasHue = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) >= LEAST_CHROMA;
		if (!hasHue) {
			return luminance(rgb) < LEAST_LUMINANCE ? secondary & 0xFFFFFF : rgb;
		}
		float factor = 1f;
		int out = rgb & 0xFFFFFF;
		for (int i = 0; i < MOST_LIGHTEN_STEPS && luminance(out) < LEAST_COLORED_LUMINANCE; i++) {
			factor *= LIGHTEN_STEP;
			out = Math.min(255, Math.round(r * factor)) << 16 | Math.min(255, Math.round(g * factor)) << 8 | Math.min(255, Math.round(b * factor));
		}
		return out;
	}

	/** @return the relative luminance of a color, from 0 (black) to 1 (white) */
	static float luminance(int rgb) {
		return (float) (0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF));
	}

	private static double linear(int channel) {
		double c = channel / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	/** @return the characters from start to end of a line, keeping their styles */
	public static Component slice(Component line, int start, int end) {
		MutableComponent out = Component.empty();
		int[] at = {0};
		line.visit((style, part) -> {
			int from = at[0];
			int to = from + part.length();
			at[0] = to;
			int a = Math.max(start, from);
			int b = Math.min(end, to);
			if (b > a) {
				out.append(Component.literal(part.substring(a - from, b - from)).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}
}
