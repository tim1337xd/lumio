package dev.tim1337.lumio.features.visual.world;

import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * The lightning of a storm in the clouds: now and then a flash somewhere round the player, far off, that lights the clouds from inside and the sky with them,
 * and sometimes a bolt from the clouds down to the horizon. Only seen: no sound, nothing near the player. When and where is chosen here, every frame, so
 * the clouds, the sky and the fog all light up with the same flash.
 */
public final class SkyLightning {
	/** When it strikes: never, only while it rains or thunders in the game, or now and then in any weather. */
	public enum When {
		OFF("Off"),
		WITH_RAIN("With rain"),
		ALWAYS("Always");

		private final Component name;

		When(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** How stormy the sky counts for lightning in any weather: a light storm, now and then a strike. */
	public static final float CLEAR_STORM = 0.35f;

	/** How often it strikes. */
	public enum Often {
		RARE("Rare", 30f),
		NORMAL("Normal", 12f),
		OFTEN("Often", 5f);

		private final Component name;
		/** The mean time between two strikes in a full storm, in seconds. */
		final float seconds;

		Often(String name, float seconds) {
			this.name = Component.literal(name);
			this.seconds = seconds;
		}

		public Component displayName() {
			return name;
		}
	}

	/** A storm weaker than this has no lightning: a light rain is no thunderstorm. */
	static final float LEAST_STORM = 0.15f;
	/** How long a strike's light lasts, in seconds. */
	static final float FLASH_SECONDS = 0.7f;
	private static final float NEAREST_BLOCKS = 260f;
	private static final float FARTHEST_BLOCKS = 900f;
	private static final float BOLT_SHARE = 0.35f;
	private static final float TAU = (float) (Math.PI * 2.0);

	private final Random random = new Random();
	private float untilNext = -1f;
	private float age = Float.MAX_VALUE;
	private float azimuth;
	private float distance;
	private boolean bolt;
	private float seed;

	/**
	 * Moves on by a frame.
	 *
	 * @param storm how stormy it is, from 0 to 1: rain and thunder, as far as the clouds are shown
	 */
	public void update(float seconds, float storm, boolean on, Often often) {
		age += seconds;
		if (!on || storm < LEAST_STORM) {
			untilNext = -1f;
			return;
		}
		if (untilNext < 0f) {
			untilNext = wait(storm, often);
		}
		untilNext -= seconds;
		if (untilNext <= 0f) {
			strike();
			untilNext = wait(storm, often);
		}
	}

	/** @return seconds until the next strike: by chance, as strikes are, and the stormier the sooner */
	private float wait(float storm, Often often) {
		float mean = often.seconds / (0.4f + 0.6f * storm);
		return (float) (-Math.log(1.0 - random.nextDouble() * 0.999) * mean);
	}

	private void strike() {
		age = 0f;
		azimuth = random.nextFloat() * TAU;
		distance = NEAREST_BLOCKS + random.nextFloat() * (FARTHEST_BLOCKS - NEAREST_BLOCKS);
		bolt = random.nextFloat() < BOLT_SHARE;
		seed = random.nextFloat() * 1000f;
	}

	/** @return whether a strike was less than this many seconds ago */
	public boolean struckWithin(float seconds) {
		return age <= seconds;
	}

	/** @return how stormy the sky counts for lightning: as it is, and with Always at least a light storm */
	public static float stormFor(When when, float storm) {
		return switch (when) {
			case OFF -> 0f;
			case WITH_RAIN -> storm;
			case ALWAYS -> Math.max(storm, CLEAR_STORM);
		};
	}

	/** @return how bright the flash is now, from 0 to about 1: a strong flash that flickers, and a second, weaker one soon after */
	public float flash() {
		return flashAt(age);
	}

	static float flashAt(float age) {
		if (age < 0f || age > FLASH_SECONDS) {
			return 0f;
		}
		float flicker = 0.55f + 0.45f * (((int) (age * 20f)) % 2 == 0 ? 1f : 0f);
		float second = age - 0.32f;
		float strong = (float) Math.exp(-age * 6f) * flicker + 0.5f * (float) Math.exp(-(second * 9f) * (second * 9f));
		// it ends at nothing, so the last frame is no step
		return strong * (1f - age / FLASH_SECONDS);
	}

	public float azimuth() {
		return azimuth;
	}

	public float distance() {
		return distance;
	}

	/** @return whether this strike shows a bolt down to the horizon */
	public boolean bolt() {
		return bolt;
	}

	public float seed() {
		return seed;
	}
}
