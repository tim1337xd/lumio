package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.render.ColoredFlow;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The animated glow around the menu, panels and buttons: a single color, or several colors present at once at different places along the edge.
 * Color modes:
 * <ul>
 * <li>flow: the colors are spread around the edge and rotate along it</li>
 * <li>aurora: each color is a soft patch drifting along the edge at its own speed, blending where they meet</li>
 * <li>pulse: each color has a fixed place and swells and fades in turn while the others remain</li>
 * </ul>
 * In the Colored theme the glow's colors are not from here: the shader of the panes takes them from the same colors as the fill, at the same place of the screen in the same frame. Only
 * pulse fits that, as it is about brightness: the Colored colors then swell and fade along the edge, at the glow's speed.
 */
public final class UiGlow {
	public enum Mode {
		FLOW("Flow"),
		AURORA("Aurora"),
		PULSE("Pulse");

		private final Component displayName;

		Mode(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** Element kinds a glow can surround; each can be enabled individually. */
	public enum Target {
		MENU, HUD, BUTTONS, TITLE
	}

	private static final float TAU = (float) (Math.PI * 2.0);
	private static final long START = System.nanoTime();
	/** The screen's sRGB channel values in linear light, and back: light adds up in linear color, so the glow's colors are mixed there. */
	private static final float[] TO_LINEAR = new float[256];
	private static final int TO_SCREEN_STEPS = 1024;
	private static final int[] TO_SCREEN = new int[TO_SCREEN_STEPS + 1];
	/** How many places along an edge the glow's colors and brightness are worked out for. */
	public static final int TABLE_SIZE = 64;

	static {
		for (int i = 0; i < 256; i++) {
			TO_LINEAR[i] = (float) Math.pow(i / 255.0, 2.2);
		}
		for (int i = 0; i <= TO_SCREEN_STEPS; i++) {
			TO_SCREEN[i] = (int) Math.round(Math.pow((double) i / TO_SCREEN_STEPS, 1.0 / 2.2) * 255.0);
		}
	}

	// how strong the glow is when nothing is hovered, and per kind of element: the menu window and the HUD panels the same, small controls less
	private static final float CALM = 0.85f;
	private static final float MENU_SHARE = 1f;
	private static final float TITLE_SHARE = 0.85f;
	private static final float BUTTONS_SHARE = 0.75f;
	/** How far above its hovered level a control's glow rises while the hover comes in, before it settles. */
	private static final float HOVER_RISE = 0.6f;
	// the painted glow's two layers: how far the core reaches as a share of the spread, and how strong the core and the bloom are
	private static final float CORE_SPREAD = 0.22f;
	private static final float PAINTED_CORE = 0.6f;
	private static final float PAINTED_BLOOM = 0.42f;

	private UiGlow() {
	}

	/** @return two colors (0xRRGGBB) mixed in linear light: a share of the second one */
	static int mixLinear(int a, int b, float share) {
		return channelMix(a >> 16 & 0xFF, b >> 16 & 0xFF, share) << 16 | channelMix(a >> 8 & 0xFF, b >> 8 & 0xFF, share) << 8 | channelMix(a & 0xFF, b & 0xFF, share);
	}

	private static int channelMix(int a, int b, float share) {
		return toScreen(TO_LINEAR[a] + (TO_LINEAR[b] - TO_LINEAR[a]) * share);
	}

	/** @return a linear channel value from 0 to 1 back on the screen's scale, from 0 to 255 */
	private static int toScreen(float linear) {
		return TO_SCREEN[Math.round(Mth.clamp(linear, 0f, 1f) * TO_SCREEN_STEPS)];
	}

	/** @return how much of the glow an element kind gets: the menu window and the HUD panels the most, and all a little calm when idle */
	private static float share(Target target) {
		return CALM * switch (target) {
			case MENU, HUD -> MENU_SHARE;
			case TITLE -> TITLE_SHARE;
			case BUTTONS -> BUTTONS_SHARE;
		};
	}

	/**
	 * @return the glow of a control from how far it is hovered (0 to 1): it rises a little above its hovered level while the hover comes in,
	 *         then settles there; without the menu's animations it is simply the hover
	 */
	public static float hoverGlow(float hover) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || !menu.animations()) {
			return hover;
		}
		float h = Mth.clamp(hover, 0f, 1f);
		return h + HOVER_RISE * 4f * h * (1f - h);
	}

	/** @return whether elements of this kind have a glow */
	public static boolean on(Target target) {
		MenuFeature menu = ModFeatures.MENU;
		return menu != null && menu.glowOn();
	}

	/** Snapshot of the glow state (colors and mode), taken once so every edge piece is drawn from the same moment. */
	private static final class Look {
		final int[] colors;
		final UiGlow.Mode mode;
		final float phase;
		final float strength;
		final int size;
		/** How far a color blends into the next: 1 is the original look, less gives short blends between distinct colors, more gives long ones. */
		final float soft;
		/** How many colors the Colored theme has: its pulse swells in as many places. */
		final int coloredCount;

		Look(MenuFeature menu) {
			soft = menu.glowBlend();
			colors = menu.glowPalette();
			mode = menu.glowMode();
			// frozen when animations are off, at a phase where the colors look best
			phase = menu.animations() ? (float) ((System.nanoTime() - START) / 1e9 * menu.glowSpeed() % 7200.0) : 3.1f;
			strength = menu.glowStrength();
			size = menu.glowSize();
			coloredCount = Math.max(1, menu.coloredColors().length);
		}

		/** Colors at 64 edge positions, computed once per snapshot; edge pieces look their color up here instead of computing it. */
		private int[] table;
		/** How bright the Colored theme's glow is at those positions, from 0 to 255. */
		private int[] coloredTable;

		/** @return the color at an edge position, with its brightness as alpha */
		int lut(float place) {
			if (table == null) {
				table = new int[TABLE_SIZE];
				for (int i = 0; i < TABLE_SIZE; i++) {
					table[i] = at((i + 0.5f) / TABLE_SIZE);
				}
			}
			// interpolate between table entries so long edges have no color steps
			float at = place * TABLE_SIZE - 0.5f;
			at -= (float) Math.floor(at / TABLE_SIZE) * TABLE_SIZE;
			int i = (int) at;
			float f = at - i;
			int a = table[i % TABLE_SIZE];
			int b = table[(i + 1) % TABLE_SIZE];
			if (a == b) {
				return a;
			}
			int alpha = Math.round(Mth.lerp(f, a >>> 24, b >>> 24));
			return (alpha << 24) | mixLinear(a, b, f);
		}

		/** @return how bright the Colored theme's glow is at each edge position, from 0 to 255 */
		int[] coloredBrightness() {
			if (coloredTable == null) {
				coloredTable = new int[TABLE_SIZE];
				for (int i = 0; i < TABLE_SIZE; i++) {
					coloredTable[i] = Math.round(coloredAt((i + 0.5f) / TABLE_SIZE) * 255f);
				}
			}
			return coloredTable;
		}

		/** @return the color of the glow where it is painted and not drawn by the shader: in the Colored theme the plain color the window is painted in then, as bright as its glow is at that place */
		int painted(float place) {
			if (!HudStyle.colored()) {
				return lut(place);
			}
			int[] brightness = coloredBrightness();
			int at = Math.floorMod((int) Math.floor(place * TABLE_SIZE), TABLE_SIZE);
			return brightness[at] << 24 | ColoredFlow.plainColor() & 0xFFFFFF;
		}

		/** The Colored theme's colors are always there: only a pulse changes how bright they are along the edge. */
		float coloredAt(float place) {
			if (mode != Mode.PULSE) {
				return 1f;
			}
			return coloredCount == 1 ? breath() : pulseBrightness(place, coloredCount);
		}

		private float breath() {
			return 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(TAU * phase * 0.22f));
		}

		private float pulseSwell(int i, int count) {
			return 0.28f + 0.72f * (0.5f + 0.5f * (float) Math.sin(TAU * (phase * 0.26f - (float) i / count)));
		}

		private float pulseBrightness(float place, int count) {
			float sigma = (0.5f / count + 0.06f) * soft;
			float sum = 0f;
			for (int i = 0; i < count; i++) {
				float centre = (i + 0.5f) / count + phase * 0.008f;
				float d = Ui.edgeDistance(place, centre - (float) Math.floor(centre)) / sigma;
				sum += pulseSwell(i, count) * (float) Math.exp(-d * d);
			}
			return 0.30f + 0.70f * Math.min(1f, sum * 1.25f);
		}

		int at(float place) {
			int count = colors.length;
			if (count == 1) {
				// one color is one even glow all around; a pulse still pulses
				return pack(colors[0], mode == Mode.PULSE ? breath() : 1f);
			}
			switch (mode) {
				case FLOW: {
					float u = place - phase * 0.085f;
					u -= (float) Math.floor(u);
					float s = u * count;
					int i = (int) s;
					float f = s - i;
					f = Mth.clamp(0.5f + (f - 0.5f) / soft, 0f, 1f);
					f = f * f * (3f - 2f * f);
					return pack(mixLinear(colors[i % count], colors[(i + 1) % count], f), 1f);
				}
				case AURORA: {
					float sigma = (0.55f / count + 0.08f) * soft;
					float sum = 0f;
					float r = 0f;
					float g = 0f;
					float b = 0f;
					for (int i = 0; i < count; i++) {
						float centre = (float) i / count + phase * 0.010f + 0.24f * (float) Math.sin(phase * (0.33f + 0.10f * i) + i * 2.3f);
						float d = Ui.edgeDistance(place, centre - (float) Math.floor(centre)) / sigma;
						float w = (float) Math.exp(-d * d);
						sum += w;
						r += w * TO_LINEAR[colors[i] >> 16 & 0xFF];
						g += w * TO_LINEAR[colors[i] >> 8 & 0xFF];
						b += w * TO_LINEAR[colors[i] & 0xFF];
					}
					return pack(toScreen(r / sum) << 16 | toScreen(g / sum) << 8 | toScreen(b / sum), 0.42f + 0.58f * Math.min(1f, sum));
				}
				default: {
					float sigma = (0.5f / count + 0.06f) * soft;
					float sum = 0f;
					float r = 0f;
					float g = 0f;
					float b = 0f;
					for (int i = 0; i < count; i++) {
						float centre = (i + 0.5f) / count + phase * 0.008f;
						float d = Ui.edgeDistance(place, centre - (float) Math.floor(centre)) / sigma;
						float w = pulseSwell(i, count) * (float) Math.exp(-d * d);
						sum += w;
						r += w * TO_LINEAR[colors[i] >> 16 & 0xFF];
						g += w * TO_LINEAR[colors[i] >> 8 & 0xFF];
						b += w * TO_LINEAR[colors[i] & 0xFF];
					}
					if (sum < 0.0001f) {
						return 0;
					}
					return pack(toScreen(r / sum) << 16 | toScreen(g / sum) << 8 | toScreen(b / sum), 0.30f + 0.70f * Math.min(1f, sum * 1.25f));
				}
			}
		}

		private static int pack(int rgb, float opacity) {
			return (Math.round(Mth.clamp(opacity, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
		}
	}

	/** The snapshot is recomputed at most every few milliseconds rather than per panel per frame; palette and time do not change in between. */
	private static Look cached;
	private static long cachedAt;

	private static Look look(MenuFeature menu) {
		long now = System.nanoTime();
		if (cached == null || now - cachedAt > 4_000_000L) {
			cached = new Look(menu);
			cachedAt = now;
		}
		return cached;
	}

	/** @return current glow colors at {@link #TABLE_SIZE} edge positions as ARGB with brightness as alpha, or null without glow */
	public static int[] table() {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || !menu.glowEnabled()) {
			return null;
		}
		Look look = look(menu);
		look.lut(0f);
		return look.table;
	}

	/** @return how bright the Colored theme's glow is at {@link #TABLE_SIZE} edge positions, from 0 to 255, or null without glow */
	public static int[] coloredBrightness() {
		MenuFeature menu = ModFeatures.MENU;
		return menu == null || !menu.glowEnabled() ? null : look(menu).coloredBrightness();
	}

	/** @return how far the glow of an element kind extends from its edge in menu pixels, or 0 without glow */
	public static int spread(Target target) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || !menu.glowOn()) {
			return 0;
		}
		Look look = look(menu);
		return switch (target) {
			case MENU, HUD -> look.size;
			case BUTTONS -> Math.max(3, Math.round(look.size * 0.45f));
			case TITLE -> Math.max(3, Math.round(look.size * 0.7f));
		};
	}

	/** @return glow strength of an element kind from 0 to 1, multiplied by the element's own opacity */
	public static float strength(Target target, float fade) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || fade <= 0.01f || !menu.glowOn()) {
			return 0f;
		}
		return look(menu).strength * share(target) * Mth.clamp(fade, 0f, 1f);
	}

	/**
	 * Draws the glow outside a shape.
	 *
	 * @param fade opacity from 0 to 1, lower while a panel animates in
	 */
	public static void halo(Ui ui, int x, int y, int width, int height, float radius, Target target, float fade) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || fade <= 0.01f || !menu.glowOn()) {
			return;
		}
		Look look = look(menu);
		int spread = switch (target) {
			case MENU -> look.size;
			case HUD -> Math.max(3, Math.round(look.size * 0.6f));
			case BUTTONS -> Math.max(3, Math.round(look.size * 0.45f));
			case TITLE -> Math.max(3, Math.round(look.size * 0.7f));
		};
		// two layers, as the shader draws it: a narrow bright core at the edge and a wide, soft bloom
		float amount = look.strength * share(target) * Mth.clamp(fade, 0f, 1f);
		float core = amount * PAINTED_CORE;
		float bloom = amount * PAINTED_BLOOM;
		ui.halo(x, y, width, height, radius, Math.max(2, Math.round(spread * CORE_SPREAD)), place -> Ui.withOpacity(look.painted(place), core));
		ui.halo(x, y, width, height, radius, spread, place -> Ui.withOpacity(look.painted(place), bloom));
	}

	/** @return the edge line color function of the glow, for callers drawing their own edge line that want a single combined line; null without glow */
	public static Ui.EdgeColor rimColor(Target target, float fade) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || fade <= 0.01f || !menu.glowOn()) {
			return null;
		}
		Look look = look(menu);
		float amount = look.strength * share(target) * Mth.clamp(fade, 0f, 1f);
		return place -> {
			int color = look.painted(place);
			return Ui.withOpacity(Ui.mix(color, 0xFFFFFFFF, 0.28f) & 0xFFFFFF | (color & 0xFF000000), Math.min(1f, amount * 1.1f));
		};
	}

	/** Draws the glow on the edge itself: a thin bright line and a little light just inside it. */
	public static void edge(Ui ui, int x, int y, int width, int height, float radius, Target target, float fade) {
		edge(ui, x, y, width, height, radius, target, fade, true);
	}

	/** @param withRim whether the edge line is drawn; glass draws it combined with its own line */
	public static void edge(Ui ui, int x, int y, int width, int height, float radius, Target target, float fade, boolean withRim) {
		MenuFeature menu = ModFeatures.MENU;
		if (menu == null || fade <= 0.01f || !menu.glowOn()) {
			return;
		}
		Look look = look(menu);
		float amount = look.strength * share(target) * Mth.clamp(fade, 0f, 1f);
		int depth = target == Target.MENU ? 7 : 4;
		if (target != Target.HUD) {
			// HUD panels are small and only need the edge line: this is a measurable part of the frame cost, and there are many panels
			ui.innerLight(x, y, width, height, radius, depth, place -> Ui.withOpacity(look.painted(place), amount * 0.42f));
		}
		if (!withRim) {
			return;
		}
		// the line is lighter than the glow, like the edge of a lit object
		ui.rim(x, y, width, height, radius, 1, place -> {
			int color = look.painted(place);
			return Ui.withOpacity(Ui.mix(color, 0xFFFFFFFF, 0.28f) & 0xFFFFFF | (color & 0xFF000000), Math.min(1f, amount * 1.1f));
		});
	}
}
