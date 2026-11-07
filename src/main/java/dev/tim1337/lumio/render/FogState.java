package dev.tim1337.lumio.render;

import net.minecraft.client.renderer.fog.FogData;

/**
 * The fog of the surroundings in the last frame, as the game set it up (with the mod's sky and ambience applied): where it starts and ends and
 * its color. Things drawn in the world with their own shader, such as the world lyrics, use it to fade into the fog like the world does.
 */
public final class FogState {
	private static float start = Float.MAX_VALUE;
	private static float end = Float.MAX_VALUE;
	private static int color = 0xFFFFFFFF;
	private static int skyColor = 0xFF78A7FF;

	private FogState() {
	}

	/** Takes the fog the game just set up. Called once per frame. */
	public static void capture(FogData fog) {
		// the fog of the surroundings (weather, water, the ambience), not the one hiding the edge of the loaded world: what is drawn in the sky is
		// not fogged by the render distance either
		start = fog.environmentalStart;
		end = fog.environmentalEnd;
		var c = fog.color;
		color = 0xFF000000 | Math.round(clamp(c.x()) * 255f) << 16 | Math.round(clamp(c.y()) * 255f) << 8 | Math.round(clamp(c.z()) * 255f);
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	/** @return how far off the world is gone in the fog, in blocks */
	public static float end() {
		return end;
	}

	/** @return how clearly something at this distance is seen through the fog, from 1 (no fog) to 0 (hidden in it) */
	public static float clearness(double distance) {
		if (end >= Float.MAX_VALUE / 2 || end <= start) {
			return distance < end ? 1f : 0f;
		}
		return clamp((float) ((end - distance) / (end - start)));
	}

	/** @return the fog's color as ARGB */
	public static int color() {
		return color;
	}

	/** The sky's color straight up as the frame shows it, kept when the sky is set up; for what reflects the sky. */
	public static void captureSky(int argb) {
		skyColor = argb;
	}

	/** @return the sky's color straight up as ARGB */
	public static int skyColor() {
		return skyColor;
	}
}
