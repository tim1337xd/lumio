package dev.tim1337.lumio.features.visual.world;

import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;

/** The sky's choices, and what keeps the sky steady: nothing in it jumps when the player warps or the server changes the time. Plain, so that it is unit tested. */
public final class SkyLook {
	/** The page's style. */
	public enum Style {
		CLOUDS("Clouds"), AURORA("Aurora"), STARS("Night sky"), MOLTEN("Molten");

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

	/**
	 * @return whether a saved sky is from before the cloud shadows started off: it has the sky's settings but none for the shadows, which were on then,
	 *         so they stay on
	 */
	static boolean shadowsFromBefore(JsonObject saved) {
		return saved.has("style") && !saved.has("cloud_shadows");
	}

	/** How the clouds are drawn: the game's blocky cells, shaded and spaced, or soft clouds in three layers. */
	public enum CloudStyle {
		GAME("Game"), NATURAL("Natural");

		private final Component name;

		CloudStyle(String name) {
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

	private SkyLook() {
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
	 * fade back in; a small correction by the server glides. A new world takes them at once, as nothing was seen before it. What the rest of the sky goes
	 * by (the light on the clouds, the day and night colors, the turn of its own stars) has an angle of its own that always glides, so a paused or jumping
	 * time of the server never makes it change at once.
	 */
	public static final class SkyClock {
		private static final float TAU = (float) (Math.PI * 2.0);
		/** A change between two frames larger than this is a jump; the time itself turns the sky by about a thousandth of this per frame. */
		static final float JUMP_RADIANS = 0.02f;
		/** How long the fade out takes, and the fade back in. */
		static final float FADE_SECONDS = 0.7f;
		/** Below this the sky counts as faded out: the steps of the fade need not add up exactly. */
		private static final float HIDDEN = 0.001f;
		/** A change up to this is the time going on, taken at once; a larger one glides over about this many seconds. */
		private static final float RUNNING_RADIANS = 0.002f;
		private static final float GLIDE_SECONDS = 0.4f;
		/** How long the light of the sky takes to follow, as a time constant. */
		static final float LIGHT_SECONDS = 3f;

		private boolean started;
		private int world;
		private float sun;
		private float moon;
		private float stars;
		private float lightSun;
		private float lightStars;
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
				lightSun = wantedSun;
				lightStars = wantedStars;
				return;
			}
			lightSun = follow(lightSun, wantedSun, seconds, LIGHT_SECONDS);
			lightStars = follow(lightStars, wantedStars, seconds, LIGHT_SECONDS);
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
				take(wantedSun, wantedMoon, wantedStars);
				return;
			}
			shown = Math.min(1f, shown + seconds / FADE_SECONDS);
			sun = follow(sun, wantedSun, seconds, GLIDE_SECONDS);
			moon = follow(moon, wantedMoon, seconds, GLIDE_SECONDS);
			stars = follow(stars, wantedStars, seconds, GLIDE_SECONDS);
		}

		/** @return an angle moved on toward another, the short way round: at once for the running of the time, softly for more */
		private static float follow(float from, float to, float seconds, float timeConstant) {
			float difference = to - from;
			difference -= TAU * Math.round(difference / TAU);
			if (Math.abs(difference) <= RUNNING_RADIANS) {
				return to;
			}
			return from + difference * (1f - (float) Math.exp(-seconds / timeConstant));
		}

		/** @return the sun's angle the light of the sky goes by, always gliding */
		public float lightSun() {
			return lightSun;
		}

		/** @return the turn of the sky's own stars and lights, always gliding */
		public float lightStars() {
			return lightStars;
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

	/**
	 * Whether a world has no sky for clouds. Never because of a roof over the camera: under a roof the clouds show wherever the sky does on the
	 * screen, through a doorway, a window or a skylight, as they do outside (the sky light where the camera stands once took them away, also in
	 * a house, far from its door). Only a world that is known to lie underground has none.
	 *
	 * @param knownWorld  whether an add-on knows the world the player is in
	 * @param underground whether that world lies underground (the Dwarven Mines, the Crystal Hollows)
	 */
	public static boolean underCover(boolean knownWorld, boolean underground) {
		return knownWorld && underground;
	}

	/** The top layer of a world's build height, as {@link Ceiling} looks at it: which chunks are loaded, and where bedrock lies in that layer. */
	public interface TopLayer {
		boolean loaded(int chunkX, int chunkZ);

		/** @return whether the block at this column in the top layer of the build height is bedrock */
		boolean bedrock(int x, int z);
	}

	/**
	 * Whether a world of the Nether's kind has a solid bedrock ceiling (the vanilla Nether) and so no sky. Decided once per world, from the top
	 * layer of the build height in the chunks loaded around the player, and kept until the player leaves that world: nothing where the camera
	 * stands, nor any later place, changes it. A world without such a ceiling (Hypixel's islands of the Nether's kind) has the sky everywhere;
	 * blocks cover it where they stand on the screen. Until it is decided the sky waits, so the vanilla Nether never flashes one.
	 */
	public static final class Ceiling {
		/** The chunks looked at around the player's: this many each way. */
		static final int RADIUS_CHUNKS = 2;
		/** How many of them must be loaded before the decision is made. */
		static final int MIN_CHUNKS = 9;
		/** The columns looked at in each chunk: every this many blocks each way. */
		static final int STEP_BLOCKS = 4;
		/** The share of the columns looked at that must be bedrock for a ceiling: the vanilla Nether's top layer is bedrock everywhere. */
		static final float SOLID_SHARE = 0.9f;

		private Object world;
		private boolean decided;
		private boolean ceiling;

		/**
		 * Looks at the top layer around the player once per world: a new world forgets the last decision; once decided, this does nothing.
		 *
		 * @param world   the world the player is in (a new object when the player joins or changes to another)
		 * @param chunkX  the chunk the player is in
		 * @param chunkZ  the chunk the player is in
		 * @param top     the top layer of that world's build height
		 */
		public void look(Object world, int chunkX, int chunkZ, TopLayer top) {
			if (world != this.world) {
				this.world = world;
				decided = false;
				ceiling = false;
			}
			if (decided) {
				return;
			}
			int chunks = 0;
			int columns = 0;
			int bedrock = 0;
			for (int cx = chunkX - RADIUS_CHUNKS; cx <= chunkX + RADIUS_CHUNKS; cx++) {
				for (int cz = chunkZ - RADIUS_CHUNKS; cz <= chunkZ + RADIUS_CHUNKS; cz++) {
					if (!top.loaded(cx, cz)) {
						continue;
					}
					chunks++;
					for (int x = 0; x < 16; x += STEP_BLOCKS) {
						for (int z = 0; z < 16; z += STEP_BLOCKS) {
							columns++;
							bedrock += top.bedrock(cx * 16 + x, cz * 16 + z) ? 1 : 0;
						}
					}
				}
			}
			if (chunks < MIN_CHUNKS) {
				return;
			}
			decided = true;
			ceiling = bedrock >= columns * SOLID_SHARE;
		}

		/** @return whether the decision for the current world is made */
		public boolean decided() {
			return decided;
		}

		/** @return whether the decision for this world is made: then there is nothing more to look at */
		public boolean decidedFor(Object world) {
			return decided && world == this.world;
		}

		/** @return whether the current world has the sky: decided, and without a bedrock ceiling */
		public boolean hasSky() {
			return decided && !ceiling;
		}
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
