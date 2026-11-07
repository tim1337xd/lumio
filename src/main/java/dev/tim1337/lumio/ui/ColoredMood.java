package dev.tim1337.lumio.ui;

import net.minecraft.network.chat.Component;

/**
 * How the colors of the Colored theme move and look: the presets, and what the sliders of its Customize sheet mean. All values are from 0 to 1, and the values of the Flow preset
 * are the theme's own look: each mapping gives exactly that there. Plain numbers, so it is unit tested.
 */
public final class ColoredMood {
	/** How the fields of color move. */
	public enum Movement {
		DRIFT("Drift"),
		SWIRL("Swirl"),
		WAVES("Waves"),
		STILL("Still");

		private final Component displayName;

		Movement(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** A look to start from; two of them have colors they suggest. */
	public enum Preset {
		CALM("Calm", "Slow and soft", 0.2f, 0.25f, Movement.DRIFT, 0.35f, 0.42f, null),
		FLOW("Flow", "The default", 0.5f, 0.5f, Movement.DRIFT, 0.5f, 0.6f, null),
		VIVID("Vivid", "Rich, clear edges", 0.6f, 0.85f, Movement.DRIFT, 0.55f, 1f, null),
		AURORA("Aurora", "Long bands", 0.4f, 0.4f, Movement.WAVES, 0.3f, 0.7f, new int[]{0x2FE6A8, 0x2F6BFF, 0xA23BFF}),
		EMBER("Ember", "Warm, slow glow", 0.15f, 0.3f, Movement.SWIRL, 0.25f, 0.65f, new int[]{0xFF5A2E, 0xFFB02E, 0xA81E3C});

		private final String title;
		private final String line;
		private final float speed;
		private final float edges;
		private final Movement movement;
		private final float fieldSize;
		private final float intensity;
		private final int[] suggested;

		Preset(String title, String line, float speed, float edges, Movement movement, float fieldSize, float intensity, int[] suggested) {
			this.title = title;
			this.line = line;
			this.speed = speed;
			this.edges = edges;
			this.movement = movement;
			this.fieldSize = fieldSize;
			this.intensity = intensity;
			this.suggested = suggested;
		}

		public String title() {
			return title;
		}

		public String line() {
			return line;
		}

		public float speed() {
			return speed;
		}

		public float edges() {
			return edges;
		}

		public Movement movement() {
			return movement;
		}

		public float fieldSize() {
			return fieldSize;
		}

		public float intensity() {
			return intensity;
		}

		/** @return the colors it suggests, or null */
		public int[] suggested() {
			return suggested;
		}
	}

	private static final float SAME_VALUE = 0.006f;
	/** Up to this speed the mood reads as calm, up to the next as steady, above it as lively. */
	private static final float CALM_SPEED = 0.5f;
	private static final float STEADY_SPEED = 0.75f;

	/** @return the short line about a look: its preset (or "Custom"), its movement and its mood, as "Flow · Drift · Calm" */
	public static String summary(Preset preset, Movement movement, float speed) {
		String mood = speed <= CALM_SPEED ? "Calm" : speed <= STEADY_SPEED ? "Steady" : "Lively";
		return (preset == null ? "Custom" : preset.title()) + " · " + movement.displayName().getString() + " · " + mood;
	}

	private static final float FLOW_FALL = 3.4f;
	private static final float FALL_RANGE = 2.2f;
	private static final float MOST_SPEED_FACTOR = 4f;
	private static final float MOST_SIZE_FACTOR = 1.5f;
	/** At the most defined edges, how narrow the step between a field and what is around it is. */
	private static final float MIDDLE = 0.5f;
	private static final float WIDER_WHEN_SHARP = 0.6f;
	/** What Intensity does at its ends: how saturated and how bright the colors are. */
	private static final float LEAST_SATURATION = 0.55f;
	private static final float MOST_SATURATION = 1.3f;
	private static final float LEAST_BRIGHTNESS = 0.8f;
	private static final float MOST_BRIGHTNESS = 1.08f;
	/** How much brighter the colors get with the music at its loudest. */
	public static final float MOST_BREATH = 0.1f;

	private ColoredMood() {
	}

	/** @return the preset these values are, or null for values of the player's own */
	public static Preset presetOf(float speed, float edges, Movement movement, float fieldSize, float intensity) {
		for (Preset preset : Preset.values()) {
			if (Math.abs(preset.speed - speed) < SAME_VALUE && Math.abs(preset.edges - edges) < SAME_VALUE && preset.movement == movement
				&& Math.abs(preset.fieldSize - fieldSize) < SAME_VALUE && Math.abs(preset.intensity - intensity) < SAME_VALUE) {
				return preset;
			}
		}
		return null;
	}

	/** @return how fast the motion runs: the time the fields' rounds take is divided by this */
	public static float rate(float speed) {
		return (float) Math.pow(MOST_SPEED_FACTOR, 2f * speed - 1f);
	}

	/** @return how fast a field falls off from its middle */
	public static float fall(float edges) {
		// softer below the middle; above it the step makes the edge, and a field that kept falling faster would only get smaller
		return edges <= MIDDLE ? FLOW_FALL * (float) Math.pow(FALL_RANGE, 2f * edges - 1f) : FLOW_FALL * (1f - WIDER_WHEN_SHARP * sharp(edges));
	}

	/** @return how much of a clear step a field's edge has, from 0 for a soft one */
	public static float sharp(float edges) {
		return Math.max(0f, (edges - MIDDLE) / MIDDLE);
	}

	/** @return how large the fields are, from the left of the slider, where they are large */
	public static float radiusFactor(float fieldSize) {
		return (float) Math.pow(MOST_SIZE_FACTOR, 1f - 2f * fieldSize);
	}

	/** @return a value of the Mood pad, from a place on it: from its left or its bottom, as a share of its side */
	public static float padValue(float place, float side) {
		return side <= 0f ? 0f : Math.clamp(place / side, 0f, 1f);
	}

	/** @return a color as Intensity makes it: more or less saturated and bright; the Flow preset's value leaves it as it is */
	public static int intense(int rgb, float intensity) {
		float flow = Preset.FLOW.intensity;
		float saturation = intensity < flow ? LEAST_SATURATION + (1f - LEAST_SATURATION) * intensity / flow : 1f + (MOST_SATURATION - 1f) * (intensity - flow) / (1f - flow);
		float brightness = intensity < flow ? LEAST_BRIGHTNESS + (1f - LEAST_BRIGHTNESS) * intensity / flow : 1f + (MOST_BRIGHTNESS - 1f) * (intensity - flow) / (1f - flow);
		if (Math.abs(saturation - 1f) < SAME_VALUE && Math.abs(brightness - 1f) < SAME_VALUE) {
			return rgb;
		}
		float r = rgb >> 16 & 0xFF;
		float g = rgb >> 8 & 0xFF;
		float b = rgb & 0xFF;
		float gray = 0.2126f * r + 0.7152f * g + 0.0722f * b;
		return channel(gray + (r - gray) * saturation, brightness) << 16 | channel(gray + (g - gray) * saturation, brightness) << 8 | channel(gray + (b - gray) * saturation, brightness);
	}

	private static int channel(float value, float brightness) {
		return Math.clamp(Math.round(value * brightness), 0, 255);
	}

	/** @return a color brighter by a share, with its hue */
	public static int brighter(int rgb, float share) {
		float factor = 1f + share;
		return channel(rgb >> 16 & 0xFF, factor) << 16 | channel(rgb >> 8 & 0xFF, factor) << 8 | channel(rgb & 0xFF, factor);
	}
}
