package dev.tim1337.lumio.features.visual.world;

import net.minecraft.network.chat.Component;

/**
 * The colors of the Molten sky: thin glowing lines over a dark sky in one color, by day and by night. The sky behind the lines, its horizon (also
 * the fog's color) and the light it gives the world all come from the lines' color, so sky, fog and world are one look. Plain logic, tested without
 * the game.
 */
public final class SkyMolten {
	/** The lines' colors, as 0xRRGGBB. */
	static final int GOLD_RGB = 0xFF9A1F;
	/**
	 * The sky behind the lines: near black, of low saturation, only warmed or cooled by their color (the color lives in the lines). How much of their
	 * color it keeps over a grey of the same lightness, and its brightest channel at the horizon and at the top (Gold: about 40/28/20 and 22/17/14).
	 */
	static final float SKY_SATURATION = 0.45f;
	static final float HORIZON_SHARE = 0.157f;
	static final float ZENITH_SHARE = 0.086f;
	/** How far the sky's light on the world turns towards the lines' color with "Tint the world". */
	static final float WORLD_TINT = 0.5f;
	/** How much of the sun and the moon is left over the Molten sky. */
	static final float SUN_LEFT = 0.35f;

	/** How many lines cross the sky. */
	public enum Lines {
		FEW("Few"), NORMAL("Normal"), MANY("Many");

		private final Component name;

		Lines(String name) {
			this.name = Component.literal(name);
		}

		public Component displayName() {
			return name;
		}
	}

	private SkyMolten() {
	}

	/** @return the horizon behind the lines, from 0 to 1 per channel: dark and greyish, warmed by their color; also the fog's color */
	public static float[] horizon(int lineRgb) {
		return dim(lineRgb, HORIZON_SHARE);
	}

	/** @return the top of the sky behind the lines: darker still */
	public static float[] zenith(int lineRgb) {
		return dim(lineRgb, ZENITH_SHARE);
	}

	/** @return the color moved most of the way to a grey of its lightness, with its brightest channel at the given value */
	private static float[] dim(int rgb, float brightest) {
		float[] color = share(rgb, 1f);
		float grey = 0.2126f * color[0] + 0.7152f * color[1] + 0.0722f * color[2];
		float most = 1e-4f;
		for (int i = 0; i < 3; i++) {
			color[i] = grey + (color[i] - grey) * SKY_SATURATION;
			most = Math.max(most, color[i]);
		}
		for (int i = 0; i < 3; i++) {
			color[i] *= brightest / most;
		}
		return color;
	}

	/** @return the sky's light on the world, turned a part of the way towards the lines' color, kept as bright as it was */
	static float[] tintedLight(float[] light, int lineRgb, float share) {
		float[] line = share(lineRgb, 1f);
		float bright = Math.max(Math.max(light[0], light[1]), Math.max(light[2], 1e-4f));
		float lineBright = Math.max(Math.max(line[0], line[1]), Math.max(line[2], 1e-4f));
		float[] tinted = new float[3];
		for (int i = 0; i < 3; i++) {
			float toward = line[i] / lineBright * bright;
			tinted[i] = light[i] + (toward - light[i]) * share;
		}
		return tinted;
	}

	private static float[] share(int rgb, float share) {
		return new float[]{(rgb >> 16 & 0xFF) / 255f * share, (rgb >> 8 & 0xFF) / 255f * share, (rgb & 0xFF) / 255f * share};
	}
}
