package dev.tim1337.lumio.features.visual.world;

/**
 * How long the local player's swing takes on screen, in ticks: the game's length with its effects, times e to the minus the chosen speed, so 0 is the game's, 1 about 2.7 times as fast and -2 about 7.4 times
 * as slow. Only the look: whether and when a hit counts is the game's. Plain numbers, so it is unit tested.
 */
public final class SwingSpeed {
	/** The game's length of a swing without effects. */
	public static final int BASE_TICKS = 6;
	public static final float SLOWEST = -2f;
	public static final float FASTEST = 1f;
	public static final float GAME = 0f;
	private static final float LEAST_TICKS = 1f;
	private static final float SAME = 0.001f;

	private SwingSpeed() {
	}

	/** @return the game's length of a swing with Haste or Mining Fatigue, by the effect's amplifier: 0 for level I, and Haste wins if both are there */
	public static float withEffects(float baseTicks, boolean haste, int hasteAmplifier, boolean fatigue, int fatigueAmplifier) {
		if (haste) {
			return baseTicks - (1 + hasteAmplifier);
		}
		return fatigue ? baseTicks + (1 + fatigueAmplifier) * 2 : baseTicks;
	}

	/** @return how many ticks a swing of that length takes at a speed, at least one */
	public static float ticks(float gameTicks, float speed) {
		return Math.max(LEAST_TICKS, gameTicks * (float) Math.exp(-speed));
	}

	/** @return whether a speed is the game's own */
	public static boolean isGame(float speed) {
		return Math.abs(speed - GAME) < SAME;
	}

	/** @return how many times faster than the game a speed is */
	public static float factor(float speed) {
		return (float) Math.exp(speed);
	}

	/** @return the speed for a value of the old setting, which was a plain factor with 1 as the game's: the same pace, as far as the new range goes */
	public static float fromOldFactor(float factor) {
		if (!(factor > 0f)) {
			return GAME;
		}
		return Math.max(SLOWEST, Math.min(FASTEST, (float) Math.log(factor)));
	}
}
