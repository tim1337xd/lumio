package dev.tim1337.lumio.features.visual.world;

import net.minecraft.network.chat.Component;

/** The block overlay's styles and the numbers behind how they move: where colors and light are on a block at a time. Plain numbers, so it is unit tested. */
public final class BlockOverlayLook {
	/** The look of the block's box. */
	public enum Style {
		OUTLINE("Outline"), GLOW("Glow"), SHADER("Shader");

		private final Component name;

		Style(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** Where its colors come from. */
	public enum Colors {
		ACCENT("Accent"), RAINBOW("Rainbow"), CUSTOM("Custom");

		private final Component name;

		Colors(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** How long the pulse of a broken block lasts, and how far it grows, in blocks. */
	public static final long PULSE_MILLIS = 100L;
	private static final float SHADER_SPREAD = 0.6f;
	private static final float SHADER_DEPTH = 0.45f;
	private static final float BREATH_LOW = 0.75f;
	private static final float BREATHS_PER_CYCLE = 1.3f;
	private static final float PULSE_GROW = 0.14f;
	/** How long the box takes to come and to go when the block looked at changes. */
	public static final float FADE_SECONDS = 0.09f;
	private static final float FLOW_CYCLES_PER_SECOND = 0.22f;
	/** How far the accent and a custom color wander toward white along the block. */
	private static final float SHADE_RANGE = 0.45f;

	private BlockOverlayLook() {
	}

	/** @return the place of a point on a block's edges, from 0 to 1 and on: once around it, a little further with height, so that colors and light go on around the corners */
	public static float place(float x, float y, float z) {
		return (float) ((Math.atan2(x - 0.5f, z - 0.5f) / (2.0 * Math.PI) + 1.0) % 1.0) + y * 0.25f;
	}

	/** @return the color at a place at a time, as 0xRRGGBB */
	public static int color(Colors colors, int rgb, float place, float seconds, float speed) {
		float at = place + seconds * FLOW_CYCLES_PER_SECOND * speed;
		if (colors == Colors.RAINBOW) {
			return hue(at - (float) Math.floor(at));
		}
		float wave = 0.5f + 0.5f * (float) Math.sin(at * Math.PI * 2.0);
		return toward(rgb, 0xFFFFFF, SHADE_RANGE * wave);
	}

	/** @return the color of the Shader style at a place: the rainbow, or the color with a deeper shade of itself flowing through it, never towards white */
	public static int shaderColor(Colors colors, int rgb, float place, float seconds, float speed) {
		float at = place * SHADER_SPREAD + seconds * FLOW_CYCLES_PER_SECOND * speed;
		if (colors == Colors.RAINBOW) {
			return hue(at - (float) Math.floor(at));
		}
		float wave = 0.5f + 0.5f * (float) Math.sin(at * Math.PI * 2.0);
		return toward(rgb, 0x000000, SHADER_DEPTH * wave);
	}

	/** @return how much of its light the Shader style has now: it breathes a little */
	public static float breath(float seconds, float speed) {
		return BREATH_LOW + (1f - BREATH_LOW) * (float) Math.sin(seconds * FLOW_CYCLES_PER_SECOND * speed * Math.PI * 2.0 * BREATHS_PER_CYCLE);
	}

	/** @return how far a pulse has grown, in blocks, at its age */
	public static float pulseGrow(long ageMillis) {
		float share = Math.clamp(ageMillis / (float) PULSE_MILLIS, 0f, 1f);
		float rest = 1f - share;
		return PULSE_GROW * (1f - rest * rest * rest);
	}

	/** @return how visible a pulse is at its age: 0 once it is over */
	public static float pulseAlpha(long ageMillis) {
		float rest = 1f - Math.clamp(ageMillis / (float) PULSE_MILLIS, 0f, 1f);
		return rest * rest;
	}

	/** @return how far a block is broken, from 0 to 1: what the game counts here or the server's stages of 0 to 9, whichever says more */
	public static float breaking(float clientProgress, int serverStage) {
		float byServer = serverStage >= 0 && serverStage < 10 ? (serverStage + 1) / 10f : 0f;
		return Math.clamp(Math.max(clientProgress, byServer), 0f, 1f);
	}

	/** @return the next step of a fade */
	public static float fade(float amount, boolean in, float seconds) {
		float step = seconds / FADE_SECONDS;
		return in ? Math.min(1f, amount + step) : Math.max(0f, amount - step);
	}

	static int toward(int rgb, int other, float amount) {
		int r = rgb >> 16 & 255;
		int g = rgb >> 8 & 255;
		int b = rgb & 255;
		return Math.round(r + ((other >> 16 & 255) - r) * amount) << 16 | Math.round(g + ((other >> 8 & 255) - g) * amount) << 8 | Math.round(b + ((other & 255) - b) * amount);
	}

	private static int hue(float hue) {
		float h = hue * 6f;
		int sector = (int) h;
		float rising = h - sector;
		float falling = 1f - rising;
		float r;
		float g;
		float b;
		switch (sector % 6) {
			case 0 -> {
				r = 1f;
				g = rising;
				b = 0f;
			}
			case 1 -> {
				r = falling;
				g = 1f;
				b = 0f;
			}
			case 2 -> {
				r = 0f;
				g = 1f;
				b = rising;
			}
			case 3 -> {
				r = 0f;
				g = falling;
				b = 1f;
			}
			case 4 -> {
				r = rising;
				g = 0f;
				b = 1f;
			}
			default -> {
				r = 1f;
				g = 0f;
				b = falling;
			}
		}
		return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
	}
}
