package dev.tim1337.lumio.features.visual.world;

import net.minecraft.network.chat.Component;

/** The sky's choices, what Auto picks, and what keeps the sky steady: nothing in it jumps when the player warps or the server changes the time. Plain, so that it is unit tested. */
public final class SkyLook {
	/** The page's style. */
	public enum Style {
		AUTO("Auto"), CLOUDS("Clouds"), AURORA("Aurora"), STARS("Stars");

		private final Component name;

		Style(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** How much the cover pales the sky at most, the rain greys it, and a storm turns it dark grey-blue; and how far a color tints it. */
	static final float OVERCAST_MOST = 0.45f;
	static final float RAIN_GREY = 0.35f;
	static final float STORM_MOST = 0.55f;
	static final float TINT_MOST = 0.14f;
	private static final float[] STORM_RGB = {0.16f, 0.19f, 0.26f};

	/**
	 * The sky as the clouds make it: paler and greyer with more cover, grey in the rain, dark grey-blue in a storm, and gently towards the clouds' color,
	 * so sky, fog and clouds are one look. Changes the color in place.
	 *
	 * @param rgb      the color, from 0 to 1 per channel
	 * @param overcast how much cover there is, from 0 to 1, already eased
	 * @param rain     how much it rains, from 0 to 1, already eased
	 * @param storm    how much it thunders, from 0 to 1, already eased
	 * @param tint     the clouds' color as 0xRRGGBB, with tintShare from 0 to 1 (0 for their natural colors)
	 */
	public static void weatherSky(float[] rgb, float overcast, float rain, float storm, int tint, float tintShare) {
		float lum = 0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2];
		float pale = overcast * OVERCAST_MOST;
		float grey = rain * RAIN_GREY;
		float dark = storm * STORM_MOST;
		float colored = tintShare * TINT_MOST;
		for (int i = 0; i < 3; i++) {
			float c = rgb[i];
			c += (lum * 1.08f - c) * pale;
			c += (lum * 0.95f - c) * grey;
			c += (STORM_RGB[i] * (0.6f + lum) - c) * dark;
			float channel = (tint >> (16 - 8 * i) & 0xFF) / 255f;
			c += (channel * Math.max(lum, 0.15f) * 1.25f - c) * colored;
			rgb[i] = Math.clamp(c, 0f, 1f);
		}
	}

	/** When aurora and stars show. */
	public enum When {
		NIGHT("At night"), ALWAYS("Always");

		private final Component name;

		When(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** The cover the Weather look raises the clouds to at least: Cloudy leaves fewer, smaller gaps; Storm only rare small ones. */
	private static final float CLOUDY_COVER = 0.8f;
	private static final float STORM_COVER = 0.97f;

	/**
	 * @param cloudy how far Cloudy is faded in, from 0 to 1
	 * @param storm  how far Storm is faded in, from 0 to 1
	 * @return the clouds' cover with the Weather look: the setting, raised towards Cloudy's and Storm's cover as far as they are faded in
	 */
	public static float weatherCover(float cover, float cloudy, float storm) {
		float raised = cover + (Math.max(cover, CLOUDY_COVER) - cover) * cloudy;
		return raised + (Math.max(raised, STORM_COVER) - raised) * storm;
	}

	/** How the cloud sky looks, whatever the real weather: clear, overcast or a storm. Only the look; the game's weather stays as it is. */
	public enum WeatherLook {
		CLEAR("Clear"), CLOUDY("Cloudy"), STORM("Storm");

		private final Component name;

		WeatherLook(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	public enum CloudHeight {
		LOW("Low", 112f), GAME("Game", 192f), HIGH("High", 300f);

		private final Component name;
		private final float blocks;

		CloudHeight(String name, float blocks) {
			this.name = Component.literal(name);
			this.blocks = blocks;
		}

		public Component displayName() {
			return name;
		}

		public float blocks() {
			return blocks;
		}
	}

	/** The holes in the layer. */
	public enum Gaps {
		NONE("None"), FEW("Few"), MANY("Many");

		private final Component name;

		Gaps(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** Which way the clouds drift. */
	public enum Wind {
		GAME("Game"), FIXED("Fixed");

		private final Component name;

		Wind(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** Where a style's colors come from: its own natural ones, the accent, or those picked. */
	public enum Colors {
		NATURAL("Natural"), ACCENT("Accent"), CUSTOM("Custom");

		private final Component name;

		Colors(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** What Auto has picked. */
	public enum Pick {
		CLOUDS, AURORA, STARS
	}

	private static final float DAY_FROM = 0.05f;
	private static final float NIGHT_FROM = -0.05f;
	private static final float RAIN_FROM = 0.5f;
	private static final float DRY_FROM = 0.2f;

	private SkyLook() {
	}

	/**
	 * Auto's pick. It changes only when it gets dark or light or when the weather turns, never for where the player is. A clear night has the aurora or the stars, in turn; day and rain have the clouds.
	 */
	public static final class AutoPick {
		private Pick pick = Pick.CLOUDS;
		private boolean night;
		private boolean raining;
		private boolean started;
		private boolean auroraNext = true;

		/** Looks at the moment. @return the pick */
		public Pick update(float sunHeight, float rain) {
			boolean nowNight = night ? sunHeight < DAY_FROM : sunHeight < NIGHT_FROM;
			boolean nowRaining = raining ? rain > DRY_FROM : rain > RAIN_FROM;
			if (!started || nowNight != night || nowRaining != raining) {
				started = true;
				night = nowNight;
				raining = nowRaining;
				if (raining || !night) {
					pick = Pick.CLOUDS;
				} else {
					pick = auroraNext ? Pick.AURORA : Pick.STARS;
					auroraNext = !auroraNext;
				}
			}
			return pick;
		}
	}

	/**
	 * The place the sky is seen from. It follows every step of the camera, so clouds pass overhead as they should; a jump, as of a warp or a new server, is taken out, so that the sky stays
	 * where it is. Only the height comes back to the camera's, over some seconds, as the layer of the clouds is at a height in the world.
	 */
	public static final class Eye {
		private static final double JUMP_BLOCKS = 48.0;
		private static final double HEIGHT_BACK_SECONDS = 3.0;

		private boolean started;
		private double lastX;
		private double lastY;
		private double lastZ;
		private double offsetX;
		private double offsetY;
		private double offsetZ;

		/** Follows the camera over some seconds. */
		public void update(double x, double y, double z, double seconds) {
			if (started) {
				double dx = x - lastX;
				double dy = y - lastY;
				double dz = z - lastZ;
				if (dx * dx + dy * dy + dz * dz > JUMP_BLOCKS * JUMP_BLOCKS) {
					offsetX -= dx;
					offsetY -= dy;
					offsetZ -= dz;
				}
			}
			started = true;
			lastX = x;
			lastY = y;
			lastZ = z;
			offsetY *= Math.exp(-seconds / HEIGHT_BACK_SECONDS);
		}

		public double x() {
			return lastX + offsetX;
		}

		public double y() {
			return lastY + offsetY;
		}

		public double z() {
			return lastZ + offsetZ;
		}
	}

	/**
	 * Where the sun, the moon and the stars stand, by the time of day. With the time they run as the game's do, too slowly to see. At a jump of the time (a
	 * warp, a new world, a time the server sets) they are never seen to turn or slide: they fade out where they are, take their new places at once and
	 * fade back in. A new world takes them at once, as nothing was seen before it.
	 */
	public static final class SkyClock {
		private static final float TAU = (float) (Math.PI * 2.0);
		/** A change between two frames larger than this is a jump; the time itself turns the sky by about a thousandth of this per frame. */
		static final float JUMP_RADIANS = 0.02f;
		/** How long the fade out takes, and the fade back in. */
		static final float FADE_SECONDS = 0.25f;
		/** Below this the sky counts as faded out: the steps of the fade need not add up exactly. */
		private static final float HIDDEN = 0.001f;

		private boolean started;
		private int world;
		private float sun;
		private float moon;
		private float stars;
		private float shown = 1f;
		private boolean hiding;

		/** Takes the game's angles of this frame, in radians, in the world with this identity. */
		public void update(float wantedSun, float wantedMoon, float wantedStars, int worldIdentity, float seconds) {
			if (!started || worldIdentity != world) {
				started = true;
				world = worldIdentity;
				hiding = false;
				shown = 1f;
				take(wantedSun, wantedMoon, wantedStars);
				return;
			}
			if (!hiding && (jumps(sun, wantedSun) || jumps(moon, wantedMoon) || jumps(stars, wantedStars))) {
				hiding = true;
			}
			if (hiding) {
				shown = Math.max(0f, shown - seconds / FADE_SECONDS);
				if (shown > HIDDEN) {
					return;
				}
				shown = 0f;
				hiding = false;
			} else {
				shown = Math.min(1f, shown + seconds / FADE_SECONDS);
			}
			take(wantedSun, wantedMoon, wantedStars);
		}

		/** Lets the game's angles through again from the next frame, and shows everything: while the sky is off. */
		public void forget() {
			started = false;
			hiding = false;
			shown = 1f;
		}

		private static boolean jumps(float from, float to) {
			float difference = to - from;
			difference -= TAU * Math.round(difference / TAU);
			return Math.abs(difference) > JUMP_RADIANS;
		}

		private void take(float wantedSun, float wantedMoon, float wantedStars) {
			sun = wantedSun;
			moon = wantedMoon;
			stars = wantedStars;
		}

		public float sun() {
			return sun;
		}

		public float moon() {
			return moon;
		}

		public float stars() {
			return stars;
		}

		/** @return how much of the sun, moon and lights of the sky is shown: 1, less only while a jump is hidden */
		public float shown() {
			return shown;
		}

		/** @return whether the places run with the time, and no jump is being hidden */
		public boolean settled() {
			return !hiding && shown >= 1f;
		}
	}

	/** A ceiling this far above the camera covers it where only a little sky light comes in (the Dwarven Mines, the Crystal Hollows). */
	static final int HIGH_CEILING_BLOCKS = 24;
	static final int DIM_SKY_LIGHT = 8;

	/**
	 * @param skyLight    the sky light where the camera is, 0 to 15
	 * @param blocksAbove how far the highest block over the camera is above it
	 * @return whether the camera has no view of the sky worth clouds: no sky light at all, or a high ceiling with only a little light from an opening
	 */
	public static boolean underCover(int skyLight, int blocksAbove) {
		return skyLight <= 0 || blocksAbove > HIGH_CEILING_BLOCKS && skyLight < DIM_SKY_LIGHT;
	}

	/**
	 * @param aboveHere  how far the highest block over the camera is above it, in a world of the Nether's kind (its roof is one)
	 * @param openAround at how many of four places around the camera nothing is above it
	 * @return whether the camera is under the open sky, as on an island of the Nether: nothing straight above, or open in most places around, so a
	 * small roof does not take the sky away
	 */
	public static boolean openOverNether(int aboveHere, int openAround) {
		return aboveHere <= 0 || openAround >= 2;
	}

	public static final class Slew {
		private final float perSecond;
		private float value;

		public Slew(float secondsForAll) {
			this.perSecond = 1f / secondsForAll;
		}

		/** @return the value to show now */
		public float update(float wanted, float seconds) {
			float most = perSecond * seconds;
			value += Math.max(-most, Math.min(most, wanted - value));
			return value;
		}

		public float value() {
			return value;
		}
	}

	/** One of the skies at a time, by the theme's fade: the old one fades out over some seconds, then the new one in. */
	public static final class Fade<T> {
		private final float perSecond;
		private T shown;
		private float level;

		public Fade(float secondsEachWay) {
			this.perSecond = 1f / secondsEachWay;
		}

		/** Moves on towards what is wanted, which may be null for nothing. @return what to show now */
		public T update(T wanted, float seconds) {
			float step = perSecond * seconds;
			if (wanted != shown) {
				level -= step;
				if (level <= 0f) {
					level = 0f;
					shown = wanted;
				}
			} else if (shown != null) {
				level = Math.min(1f, level + step);
			}
			return shown;
		}

		public T shown() {
			return shown;
		}

		public float level() {
			return level;
		}
	}
}
