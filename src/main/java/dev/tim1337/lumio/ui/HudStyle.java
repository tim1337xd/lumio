package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.menu.MenuFeature;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Appearance of the HUD, following the menu theme (glass, dark or light), or the HUD's own theme when one is chosen: that one applies while the
 * HUD is drawn, and menus keep theirs.
 */
public final class HudStyle {
	/** How opaque a panel of the dark and light themes is at full opacity: nearly solid, the world only faintly behind it. */
	private static final float SOLID_OPACITY = 0.9f;

	private HudStyle() {
	}

	/** Whether the HUD is being drawn right now, from the first HUD element to the last (marker elements are registered for this). */
	private static boolean hudPass;

	public static void beginHud() {
		hudPass = true;
	}

	public static void endHud() {
		hudPass = false;
	}

	/** Own theme of the element being drawn, or null; set by the element while it draws, also over inventories. */
	private static MacTheme panelTheme;

	/** Draws what follows in the given theme (null for the HUD theme) until reset to null. */
	public static void setPanelTheme(MacTheme theme) {
		panelTheme = theme;
	}

	/** Own depth effect of the element being drawn (on or off), or null to follow the menu settings; set by the element while it draws. */
	private static Boolean panelDepth;

	/** Draws what follows with or without the depth effect (null for the menu settings) until reset to null. */
	public static void setPanelDepth(Boolean depth) {
		panelDepth = depth;
	}

	/** How much of its glow the element being drawn has: less with a soft glow; set by the element while it draws. */
	private static float panelGlow = 1f;

	/** Draws what follows with this share of its glow until set back to 1. */
	public static void setPanelGlow(float share) {
		panelGlow = share;
	}

	/** The glow switch of the panel being drawn (null outside of one), and how see-through it is: any surface it draws follows them. */
	private static Boolean panelGlowOn;
	private static float panelSeeThrough;

	/** Everything a panel draws until this is set back to null has the panel's own glow and transparency, also where it does not say so itself, as in a simple row. */
	public static void setPanelSurface(Boolean glowOn, float seeThrough) {
		panelGlowOn = glowOn;
		panelSeeThrough = seeThrough;
	}

	/** @return how much glow a surface that does not choose its own has: all of it outside of a panel, what the panel says within one */
	static float surfaceGlow(Boolean glowOn, float softShare) {
		return glowOn == null ? 1f : glowOn ? softShare : 0f;
	}

	/** @return the glow a surface without a choice of its own has right now */
	public static float surfaceGlowNow() {
		return surfaceGlow(panelGlowOn, panelGlow);
	}

	/** @return the theme for what is drawn now, for the tests */
	public static MacTheme themeNow() {
		return theme();
	}

	/** @return whether HUD panels drawn now have the depth effect: the element's own choice, otherwise the menu's switch */
	public static boolean depth() {
		if (panelDepth != null) {
			return panelDepth;
		}
		MenuFeature menu = ModFeatures.MENU;
		return menu == null || menu.hudDepth();
	}

	/** @return the theme for what is drawn now: the element's own, the HUD's while drawing the HUD, otherwise the menu's */
	private static MacTheme theme() {
		if (panelTheme != null) {
			return panelTheme;
		}
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null) {
			return MacTheme.DARK;
		}
		return hudPass ? menu.hudTheme() : menu.theme();
	}

	/** @return whether what is drawn now takes the colors of the Colored theme */
	public static boolean colored() {
		return theme() == MacTheme.COLORED;
	}

	/** @return whether the HUD is what is being drawn now, which takes the colors a bit quieter than a menu */
	public static boolean inHud() {
		return hudPass;
	}

	public static Palette palette() {
		return theme().palette();
	}

	public static int text() {
		return palette().hudText();
	}

	public static int secondary() {
		return palette().hudSecondary();
	}

	/** @return the color of a thin line between a panel's parts, at an opacity, visible on every theme */
	public static int line(float opacity) {
		return Ui.withOpacity(text(), theme().lineOpacity(opacity));
	}

	/** @return the full opacity of a panel: glass uses its own clarity, dark and light are nearly opaque */
	public static float opacity() {
		return theme().onGlass() ? 1f : SOLID_OPACITY;
	}

	/** @return a faint fill for elements on a panel, such as slots */
	public static int slot() {
		return palette().slot();
	}

	/** Draws a panel surface in the current theme, with its glow: within a panel the one that panel has set. */
	public static void panel(Ui ui, int x, int y, int width, int height, float radius, float opacity) {
		float shown = opacity * (1f - panelSeeThrough);
		panel(ui, x, y, width, height, radius, shown, UiGlow.Target.HUD, surfaceGlowNow() * Mth.clamp(shown / opacity(), 0f, 1f));
	}

	/** Same, with or without glow: every panel has its own glow switch. */
	public static void panel(Ui ui, int x, int y, int width, int height, float radius, float opacity, boolean glow) {
		panel(ui, x, y, width, height, radius, opacity, UiGlow.Target.HUD, glow ? panelGlow * Mth.clamp(opacity / opacity(), 0f, 1f) : 0f);
	}

	/** Same for a panel acting as a button: its glow has its own amount and kind. */
	public static void panel(Ui ui, int x, int y, int width, int height, float radius, float opacity, UiGlow.Target target, float glow) {
		MacTheme theme = theme();
		// the depth effect (shadow, lit and bent edge, gloss) is a choice for HUD elements; other panels keep it
		boolean depth = target != UiGlow.Target.HUD || depth();
		if (theme.onGlass()) {
			Glass.surface(ui, x, y, width, height, radius, opacity, glow, false, target, depth);
			return;
		}
		boolean light = theme == MacTheme.LIGHT;
		Palette p = theme.palette();
		float fade = Mth.clamp(opacity / SOLID_OPACITY, 0f, 1f);
		if (Glass.solid(ui, x, y, width, height, radius, opacity, target, glow, light, p.window() & 0xFFFFFF, p.windowBorder() & 0xFFFFFF, depth)) {
			return;
		}
		if (depth) {
			ui.shadow(x, y, width, height, radius, 8, (light ? 0.18f : 0.35f) * fade);
		}
		UiGlow.halo(ui, x, y, width, height, radius, target, glow);
		ui.rounded(x, y, width, height, radius, Ui.withOpacity(p.window(), opacity));
		ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(p.windowBorder(), fade));
		UiGlow.edge(ui, x, y, width, height, radius, target, glow);
	}

	/** @return whether the current drawing is on a light panel, where server colors (designed for dark backgrounds) are too pale to read */
	public static boolean light() {
		return theme() == MacTheme.LIGHT;
	}

	/** Maximum relative luminance that still reads on a light panel: a 4.5:1 contrast against its near-white fill. */
	private static final double MOST_LUMINANCE = 0.16;

	/** @return the color darkened, keeping its hue, until it is readable on a light panel; unchanged on dark panels */
	public static int readable(int rgb) {
		if (!light()) {
			return rgb;
		}
		int alpha = rgb & 0xFF000000;
		float r = rgb >> 16 & 0xFF;
		float g = rgb >> 8 & 0xFF;
		float b = rgb & 0xFF;
		for (int i = 0; i < 40 && luminance(r, g, b) > MOST_LUMINANCE; i++) {
			r *= 0.92f;
			g *= 0.92f;
			b *= 0.92f;
		}
		return alpha | Math.round(r) << 16 | Math.round(g) << 8 | Math.round(b);
	}

	private static double luminance(float r, float g, float b) {
		return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
	}

	private static double linear(float channel) {
		double c = channel / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	/** Texts with readable colors, cached per text instance rather than recomputed every frame: action bar and scoreboard stay the same for a while. */
	private static final Map<Object, Object> READABLE = new IdentityHashMap<>();

	private static Style readable(Style style) {
		TextColor color = style.getColor();
		if (color == null) {
			return style;
		}
		int darker = readable(color.getValue());
		return darker == color.getValue() ? style : style.withColor(darker);
	}

	/** @return the text with every color darkened like {@link #readable(int)} on a light panel; otherwise the text itself */
	public static Component readable(Component text) {
		if (!light() || text == null) {
			return text;
		}
		Object found = READABLE.get(text);
		if (found == null) {
			MutableComponent made = Component.empty();
			text.visit((style, part) -> {
				made.append(Component.literal(part).setStyle(readable(style)));
				return Optional.empty();
			}, Style.EMPTY);
			remember(text, made);
			found = made;
		}
		return (Component) found;
	}

	/** Same for already laid out text, as the action bar stores it. */
	public static FormattedCharSequence readable(FormattedCharSequence text) {
		if (!light() || text == null) {
			return text;
		}
		Object found = READABLE.get(text);
		if (found == null) {
			List<Style> styles = new ArrayList<>();
			IntArrayList codepoints = new IntArrayList();
			text.accept((index, style, codepoint) -> {
				styles.add(readable(style));
				codepoints.add(codepoint);
				return true;
			});
			FormattedCharSequence made = sink -> {
				for (int i = 0; i < codepoints.size(); i++) {
					if (!sink.accept(i, styles.get(i), codepoints.getInt(i))) {
						return false;
					}
				}
				return true;
			};
			remember(text, made);
			found = made;
		}
		return (FormattedCharSequence) found;
	}

	private static void remember(Object text, Object made) {
		if (READABLE.size() > 256) {
			READABLE.clear();
		}
		READABLE.put(text, made);
	}
}
