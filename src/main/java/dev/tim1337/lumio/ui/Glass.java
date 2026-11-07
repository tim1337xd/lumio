package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.render.ColoredFlow;
import dev.tim1337.lumio.render.GlassPanes;

import net.minecraft.util.Mth;

/**
 * The mod's single glass material, for the menu and the HUD panels: a tinted pane the world shows through, blurred and refracted at its edge, with
 * a specular highlight on the edge where light falls (strongest top left, then bottom right), a drop shadow and the surrounding glow.
 * <p>
 * It is rendered by a shader ({@link GlassPanes}) in one pass. Where that is not possible (the shaders failed to compile, or the setting is off)
 * it falls back to a painted version: a tint and an outline, without blur and refraction.
 */
public final class Glass {
	private Glass() {
	}

	/** Draws a glass pane: the body, then the edge. */
	public static void surface(Ui ui, int x, int y, int width, int height, float radius, float fade, boolean window, UiGlow.Target target) {
		surface(ui, x, y, width, height, radius, fade, fade, window, target);
	}

	/** Same, with a separate glow amount: a button has none until hovered. */
	public static void surface(Ui ui, int x, int y, int width, int height, float radius, float fade, float glow, boolean window, UiGlow.Target target) {
		surface(ui, x, y, width, height, radius, fade, glow, window, target, true);
	}

	/**
	 * Same, with or without the depth effect: without it the pane is flat, with the same fill and corners but no shadow, no bent and lit edge
	 * and no gloss, and an even thin edge line.
	 */
	public static void surface(Ui ui, int x, int y, int width, int height, float radius, float fade, float glow, boolean window, UiGlow.Target target,
		boolean depth) {
		if (GlassPanes.active()) {
			GlassPanes.pane(ui, x, y, width, height, radius, fade, style(window, target, glow, 0f, depth ? 0 : GlassPanes.FLAG_FLAT));
			return;
		}
		fade = Mth.clamp(fade, 0f, 1f);
		if (!depth) {
			// flat: the tinted body and an even line, without the shadow and the lit edge
			MenuFeature menu = ModFeatures.MENU;
			float density = density(menu);
			int tint = plainTint(menu);
			UiGlow.halo(ui, x, y, width, height, radius, target, glow);
			ui.rounded(x, y, width, height, radius, Ui.withOpacity(tint, density * fade));
			ui.ring(x, y, width, height, radius, 1, Ui.withOpacity(0xFFFFFFFF, FLAT_LINE * fade));
			UiGlow.edge(ui, x, y, width, height, radius, target, glow, false);
			return;
		}
		body(ui, x, y, width, height, radius, fade, glow, window, target);
		edges(ui, x, y, width, height, radius, fade, glow, window, target);
	}

	/** Opacity of the even edge line of a flat glass pane without the shader. */
	private static final float FLAT_LINE = 0.35f;

	/** @return the color of the glass where it is painted without the shader: where that failed, the Colored theme is a plain color made from its colors */
	private static int plainTint(MenuFeature menu) {
		if (HudStyle.colored()) {
			return 0xFF000000 | ColoredFlow.plainColor();
		}
		return 0xFF000000 | (menu == null ? 0x0B0E1A : menu.glassTint());
	}

	/** @return how dense the glass is: its color's opacity */
	private static float density(MenuFeature menu) {
		return menu == null ? 0.48f : menu.glassOpacity();
	}

	/** Set while a window is drawn inside of another: its glow lies over the window around it. */
	private static boolean insideWindow;

	public static void setInsideWindow(boolean inside) {
		insideWindow = inside;
	}

	/** @return the flag for what stands on its own, whose glow goes behind all elements: a panel of the HUD, and the menu's window */
	private static int standsAlone(boolean window, UiGlow.Target target) {
		boolean alone = HudStyle.inHud() ? target == UiGlow.Target.HUD : window && !insideWindow;
		return alone ? GlassPanes.FLAG_GLOW_BEHIND : 0;
	}

	/** @return the shader pane style for the current menu glass settings */
	private static GlassPanes.Style style(boolean window, UiGlow.Target target, float glow, float lit, int extraFlags) {
		MenuFeature menu = ModFeatures.MENU;
		float opacity = density(menu);
		float effect = menu == null ? 0.8f : menu.glassEffect();
		int tint = menu == null ? 0x0B0E1A : menu.glassTint();
		float blur = menu == null ? 0.55f : menu.glassBlur();
		float refraction = menu == null ? 0.6f : menu.glassRefraction();
		float dispersion = menu == null ? 0.35f : menu.glassDispersion();
		int flags = extraFlags | (window ? GlassPanes.FLAG_WINDOW : 0) | GlassPanes.FLAG_INNER_GLOW | GlassPanes.FLAG_READABLE | standsAlone(window, target);
		float flowShare = 0f;
		if (HudStyle.colored()) {
			// the colors take the place of the tint and of most of the world, and a dark layer lies between them and the content; the rest of the pane stays
			ColoredFlow.ask();
			boolean inHud = HudStyle.inHud();
			flags |= GlassPanes.FLAG_COLORED;
			flowShare = inHud ? ColoredFlow.GAME_SHARE : ColoredFlow.MENU_SHARE;
			opacity = inHud ? ColoredFlow.gameLayer() : ColoredFlow.menuLayer();
			tint = ColoredPalette.LAYER_RGB;
			refraction = 0f;
			dispersion = 0f;
		}
		// a control's glow rises a little as the hover comes in, then settles
		float shown = target == UiGlow.Target.BUTTONS ? UiGlow.hoverGlow(glow) : glow;
		int accent = menu == null ? SystemColors.BLUE_RGB : menu.accentArgb() & 0xFFFFFF;
		// buttons are denser than windows, so their label stays readable over any world
		float density = target == UiGlow.Target.BUTTONS ? Mth.clamp(opacity + 0.22f, 0f, 1f) : opacity;
		// a flat pane has no shadow and does not bend the picture at its edge
		boolean flat = (flags & GlassPanes.FLAG_FLAT) != 0;
		return new GlassPanes.Style(tint, density, blur, flat ? 0f : refraction, flat ? 0f : dispersion, effect,
			flat ? 0f : window ? 22f : target == UiGlow.Target.BUTTONS ? 4f : 9f, flat ? 0f : window ? 0.5f : target == UiGlow.Target.BUTTONS ? 0.22f : 0.34f,
			UiGlow.spread(target), UiGlow.strength(target, shown), 0xFFFFFF, window ? 16f : 10f, accent, lit, flags, flowShare);
	}

	/**
	 * Draws everything behind the glass content: the shadow, the surrounding glow, and the tinted pane with its gloss. Content drawn afterwards,
	 * such as a sidebar, is in front of it and behind the edge light.
	 *
	 * @param fade   opacity from 0 to 1, lower while a panel animates in or out
	 * @param window whether this is the menu window (large, with a deeper edge light) rather than a small panel
	 * @param target kind of element, for its glow
	 */
	public static void body(Ui ui, int x, int y, int width, int height, float radius, float fade, boolean window, UiGlow.Target target) {
		body(ui, x, y, width, height, radius, fade, fade, window, target);
	}

	public static void body(Ui ui, int x, int y, int width, int height, float radius, float fade, float glow, boolean window, UiGlow.Target target) {
		fade = Mth.clamp(fade, 0f, 1f);
		if (GlassPanes.active()) {
			GlassPanes.pane(ui, x, y, width, height, radius, fade, style(window, target, glow, 0f, 0));
			return;
		}
		MenuFeature menu = ModFeatures.MENU;
		float density = density(menu);
		int tint = plainTint(menu);

		// the glass is translucent, so the shadow is only drawn outside it: underneath it would show as dark and light bands inside the pane
		float shadow = (window ? 0.5f : 0.34f) * fade;
		ui.halo(x, y, width, height, radius, window ? 22 : 9, place -> Ui.withOpacity(0xFF000000, shadow));
		UiGlow.halo(ui, x, y, width, height, radius, target, glow);
		// body: a tint the world shows through, less the denser it is
		ui.rounded(x, y, width, height, radius, Ui.withOpacity(tint, density * fade));
	}

	/** Draws the glass edge light and the glow on it, on top of the glass content so the edge is never covered. */
	public static void edges(Ui ui, int x, int y, int width, int height, float radius, float fade, boolean window, UiGlow.Target target) {
		edges(ui, x, y, width, height, radius, fade, fade, window, target);
	}

	public static void edges(Ui ui, int x, int y, int width, int height, float radius, float fade, float glow, boolean window, UiGlow.Target target) {
		fade = Mth.clamp(fade, 0f, 1f);
		if (GlassPanes.active()) {
			// only the rim, over whatever was drawn on the pane since the body
			GlassPanes.pane(ui, x, y, width, height, radius, fade, style(window, target, glow, 0f, GlassPanes.FLAG_RIM_ONLY));
			return;
		}
		MenuFeature menu = ModFeatures.MENU;
		float effect = menu == null ? 0.8f : menu.glassEffect();
		float[] corners = Ui.cornerPlaces(width, height, radius);
		float perimeter = 2f * (width - 2f * radius + height - 2f * radius) + (float) (Math.PI * 2.0 * radius);
		float sigma = Mth.clamp(Math.min(width, height) * 0.3f, 9f, 52f) / Math.max(1f, perimeter);
		// edge light: bright where light falls (top-left and bottom-right corners, less on the other two)
		Ui.EdgeColor specular = place -> {
			float topLeft = bump(place, corners[0], sigma);
			float bottomRight = bump(place, corners[2], sigma);
			float others = 0.3f * (bump(place, corners[1], sigma) + bump(place, corners[3], sigma));
			// large panes get a bright corner where light falls; small ones such as buttons get an even line, since a lone bright corner arc looks like a second line
			float contrast = window ? 0.84f : 0.22f;
			float strength = (1f - contrast) + contrast * Math.min(1f, topLeft + 0.72f * bottomRight + others);
			return Ui.withOpacity(0xFFFFFFFF, strength);
		};
		float line = Math.min(1f, (0.28f + 0.62f * effect) * fade);
		// a single line along the edge: the glass light with the glow blended on top, not two overlapping lines
		Ui.EdgeColor glowLine = UiGlow.rimColor(target, glow);
		ui.rim(x, y, width, height, radius, 1, glowLine == null ? place -> Ui.withOpacity(specular.at(place), line)
			: place -> Ui.over(glowLine.at(place), Ui.withOpacity(specular.at(place), line)));
		UiGlow.edge(ui, x, y, width, height, radius, target, glow, false);
	}

	/**
	 * A glass button, for the buttons and sliders of vanilla screens: a small pane with a soft shadow that lights up and glows on hover. Its edge
	 * light matches the rest of the glass.
	 *
	 * @param lit  hover amount, from 0 to 1
	 * @param fade 1, or less for a disabled button
	 */
	public static void button(Ui ui, int x, int y, int width, int height, float radius, float lit, float fade) {
		if (GlassPanes.active()) {
			GlassPanes.pane(ui, x, y, width, height, radius, fade, style(false, UiGlow.Target.BUTTONS, lit * fade, lit, 0));
			return;
		}
		MenuFeature menu = ModFeatures.MENU;
		float density = density(menu);
		float effect = menu == null ? 0.8f : menu.glassEffect();
		int tint = plainTint(menu);
		float shadow = 0.22f * fade;
		ui.halo(x, y, width, height, radius, 4, place -> Ui.withOpacity(0xFF000000, shadow));
		UiGlow.halo(ui, x, y, width, height, radius, UiGlow.Target.BUTTONS, UiGlow.hoverGlow(lit) * fade);
		// buttons are denser than windows, so their label stays readable over any world
		ui.rounded(x, y, width, height, radius, Ui.withOpacity(tint, Mth.clamp(density + 0.22f, 0f, 1f) * fade));
		if (lit > 0.01f) {
			ui.rounded(x, y, width, height, radius, Ui.withOpacity(ui.accent, 0.26f * lit * fade));
		}
		float[] corners = Ui.cornerPlaces(width, height, radius);
		float perimeter = 2f * (width - 2f * radius + height - 2f * radius) + (float) (Math.PI * 2.0 * radius);
		float sigma = Mth.clamp(Math.min(width, height) * 0.5f, 6f, 30f) / Math.max(1f, perimeter);
		float line = Math.min(1f, (0.20f + 0.50f * effect + 0.3f * lit) * fade);
		Ui.EdgeColor glowLine = UiGlow.rimColor(UiGlow.Target.BUTTONS, lit * fade);
		ui.rim(x, y, width, height, radius, 1, place -> {
			float strength = 0.78f + 0.22f * Math.min(1f, bump(place, corners[0], sigma) + 0.7f * bump(place, corners[2], sigma));
			int white = Ui.withOpacity(0xFFFFFFFF, strength * line);
			int base = lit > 0.01f ? Ui.mix(white, Ui.withOpacity(ui.accent, Math.min(1f, 0.9f * fade)), 0.6f * lit) : white;
			return glowLine == null ? base : Ui.over(glowLine.at(place), base);
		});
		UiGlow.edge(ui, x, y, width, height, radius, UiGlow.Target.BUTTONS, lit * fade, false);
	}

	/** Draws what is behind the window right through it, as it is, in a flat pane. @return whether it was drawn; if not, the caller paints something itself */
	public static boolean world(Ui ui, int x, int y, int width, int height, float radius) {
		if (!GlassPanes.active()) {
			return false;
		}
		GlassPanes.clearPane(ui, x, y, width, height, radius, new GlassPanes.Style(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0xFFFFFF, 10f, 0, 0f, GlassPanes.FLAG_FLAT, 0f));
		return true;
	}

	/** Blurs all behind what is drawn next, over the given size from the top left, with no look of glass. @return whether it was drawn; if not, the caller does without */
	public static boolean blurBehind(Ui ui, int width, int height, float blur, float fade) {
		if (!GlassPanes.active()) {
			return false;
		}
		GlassPanes.clearPane(ui, 0, 0, width, height, 0f, fade, new GlassPanes.Style(0, 0f, blur, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0xFFFFFF, 10f, 0, 0f, GlassPanes.FLAG_FLAT, 0f));
		return true;
	}

	/** Draws the colors of the Colored theme in a flat pane without the layer over them. @return whether it was drawn; if not, the caller paints something itself */
	public static boolean colors(Ui ui, int x, int y, int width, int height, float radius) {
		if (!GlassPanes.active() || !HudStyle.colored()) {
			return false;
		}
		GlassPanes.pane(ui, x, y, width, height, radius, 1f, new GlassPanes.Style(ColoredPalette.LAYER_RGB, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0xFFFFFF, 10f, 0, 0f,
			GlassPanes.FLAG_COLORED | GlassPanes.FLAG_FLAT, ColoredFlow.MENU_SHARE));
		return true;
	}

	/**
	 * Draws a solid dark or light theme panel in one shader pass when possible: shadow, glow, fill and edge line. @return whether it was drawn; if
	 * not, the caller paints it itself.
	 */
	public static boolean solid(Ui ui, int x, int y, int width, int height, float radius, float opacity, UiGlow.Target target, float glow, boolean light, int fill, int line) {
		return solid(ui, x, y, width, height, radius, opacity, target, glow, light, fill, line, true);
	}

	/** Same, with or without the depth effect, which on a solid pane is its shadow. */
	public static boolean solid(Ui ui, int x, int y, int width, int height, float radius, float opacity, UiGlow.Target target, float glow, boolean light, int fill, int line,
		boolean depth) {
		if (!GlassPanes.active()) {
			return false;
		}
		float fade = Mth.clamp(opacity / 0.94f, 0f, 1f);
		int flags = GlassPanes.FLAG_OPAQUE | (light ? GlassPanes.FLAG_LIGHT : 0) | (target == UiGlow.Target.MENU ? GlassPanes.FLAG_WINDOW : 0)
			| (target != UiGlow.Target.HUD ? GlassPanes.FLAG_INNER_GLOW : 0) | (depth ? 0 : GlassPanes.FLAG_FLAT) | standsAlone(target == UiGlow.Target.MENU, target);
		GlassPanes.pane(ui, x, y, width, height, radius, fade, new GlassPanes.Style(fill & 0xFFFFFF, 1f, 0f, 0f, 0f, 0f,
			!depth ? 0f : target == UiGlow.Target.MENU ? 22f : 8f, !depth ? 0f : light ? 0.18f : 0.35f, UiGlow.spread(target), UiGlow.strength(target, glow),
			line & 0xFFFFFF, 12f, 0, 0f, flags, 0f));
		return true;
	}

	private static float bump(float place, float centre, float sigma) {
		float d = Ui.edgeDistance(place, centre) / sigma;
		return (float) Math.exp(-d * d);
	}
}
