package dev.tim1337.lumio.features.visual.world;

import net.minecraft.network.chat.Component;

/** What the ambience shows when: the choices on its page and the rules of where things may spawn. Plain, so that it is unit tested. */
public final class AmbienceLook {
	/**
	 * The extras (light shafts and dust, ground mist, falling leaves, water glints, island mood with its ash, sparks and webs) are switched off for now and have no settings. Their code stays, and
	 * this one flag brings them back, together with rows for {@link Choice}.
	 */
	public static final boolean EXTRAS = false;

	/** The page's style. */
	public enum Style {
		FIREFLIES("Fireflies"), RAIN("Rain");

		private final Component name;

		Style(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** How the ground looks in the rain. */
	public enum WetGround {
		OFF("Off"), PUDDLES("Puddles"), EVERYWHERE("Everywhere");

		private final Component name;

		WetGround(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	/** What the world is like around the camera; filled in now and then, and only read here. */
	public static final class Scene {
		public boolean night;
		public boolean sunUp;
		public boolean raining;
		public boolean underGround;
		public boolean noSky;
		/** The add-on's world, whose island it tells through the fields below. */
		public boolean knownWorld;
		public boolean hintNoLeaves;
		public boolean hintMist;
		public boolean moodAsh;
		public boolean moodSparks;
		public boolean moodDust;
		public boolean moodWebs;
	}

	/** What the page says; filled in now and then, and only read here. */
	public static final class Choice {
		public Style style = Style.FIREFLIES;
		public boolean firefliesAnywhere;
		public boolean shafts = EXTRAS;
		public boolean mist = EXTRAS;
		public boolean leaves = EXTRAS;
		public boolean water = EXTRAS;
		public boolean mood = EXTRAS;
	}

	private AmbienceLook() {
	}

	/** @return how much of a kind's normal number there should be now, from 0 for none: what is not wanted fades out */
	public static float share(AmbiencePool.Kind kind, Choice choice, Scene scene) {
		boolean open = !scene.noSky && !scene.underGround;
		boolean mood = choice.mood && scene.knownWorld;
		return switch (kind) {
			case FIREFLY -> choice.style == Style.FIREFLIES && (scene.night || choice.firefliesAnywhere) ? 1f : 0f;
			// the Rain style rains whatever the server's weather is
			case RAIN, RIPPLE -> choice.style == Style.RAIN && open ? 1f : 0f;
			case GLINT -> choice.water && !scene.raining && open ? 1f : 0f;
			case LEAF -> choice.leaves && open && !(scene.knownWorld && scene.hintNoLeaves) ? 1f : 0f;
			case MIST -> choice.mist && open && (scene.knownWorld ? scene.hintMist : scene.night || scene.raining) ? 1f : 0f;
			case SHAFT -> choice.shafts && open && scene.sunUp && !scene.raining ? 1f : 0f;
			case DUST -> choice.shafts && open && scene.sunUp && !scene.raining || choice.mood && (mood ? scene.moodDust : scene.underGround) ? 1f : 0f;
			case ASH -> mood && scene.moodAsh || choice.mood && !scene.knownWorld && scene.noSky ? 1f : 0f;
			case EMBER -> mood && scene.moodSparks || choice.mood && !scene.knownWorld && scene.noSky ? 1f : 0f;
			case WEB -> mood && scene.moodWebs ? 1f : 0f;
		};
	}

	/** @return whether a firefly may spawn over a block: at night near grass; with Anywhere over any natural ground, and over a built one now and then */
	public static boolean fireflySpot(boolean grass, boolean natural, boolean chance, boolean anywhere) {
		return grass || anywhere && (natural || chance);
	}

	/** Fireflies keep at least this far apart, in blocks, and there is at most one in a cell of this size. */
	public static final double FIREFLY_SPACING = 2.5;
	public static final int FIREFLY_CELL = 4;

	/** @return whether a new firefly is too near one already there: closer than the spacing, or in the same cell of the grid */
	public static boolean fireflyCrowded(double x, double y, double z, double ox, double oy, double oz) {
		double dx = x - ox;
		double dy = y - oy;
		double dz = z - oz;
		if (dx * dx + dy * dy + dz * dz < FIREFLY_SPACING * FIREFLY_SPACING) {
			return true;
		}
		return Math.floorDiv((int) Math.floor(x), FIREFLY_CELL) == Math.floorDiv((int) Math.floor(ox), FIREFLY_CELL)
			&& Math.floorDiv((int) Math.floor(y), FIREFLY_CELL) == Math.floorDiv((int) Math.floor(oy), FIREFLY_CELL)
			&& Math.floorDiv((int) Math.floor(z), FIREFLY_CELL) == Math.floorDiv((int) Math.floor(oz), FIREFLY_CELL);
	}

	/** @return how many fireflies are wanted at the share of tried spots that were open air: few in a narrow tunnel, all of them in the open */
	public static float fireflyDensity(float wanted, float openShare) {
		return wanted * Math.clamp(openShare * 2f, 0.12f, 1f);
	}

	/** @return whether a rain ring may spawn: on open water with the sky above it */
	public static boolean ringSpot(boolean water, boolean airAbove, boolean skyAbove) {
		return water && airAbove && skyAbove;
	}

	/** @return whether a glint may spawn: on open water with the sky above it */
	public static boolean glintSpot(boolean water, boolean airAbove, boolean skyAbove) {
		return water && airAbove && skyAbove;
	}

	/** @return how strongly the ground should reflect now, from 0: only with the Rain style */
	public static float wetWanted(Style style, WetGround ground, float strength) {
		return style == Style.RAIN && ground != WetGround.OFF ? strength : 0f;
	}
}
