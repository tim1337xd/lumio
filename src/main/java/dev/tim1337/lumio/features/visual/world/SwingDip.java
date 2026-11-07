package dev.tim1337.lumio.features.visual.world;

/**
 * How far the item is lowered after a hit into the air, at another swing speed. Such a hit resets the game's attack timer, and the item drops and rises with
 * that timer, which knows nothing of the swing's speed: at a slow swing the item would rise in the middle of the swing, which is what looks wrong, and mining,
 * which does not reset the timer, never shows it. So the look gets a timer of its own that runs as fast as the swing, and the whole motion is the game's, only
 * slower or faster. The real timer, and with it the attack's strength, is only read. Switching items also resets the timer, without a swing: that stays
 * the game's. Plain numbers, so it is unit tested.
 */
public final class SwingDip {
	/** The game's: how far the item moves in one tick at most. */
	private static final float MOST_PER_TICK = 0.4f;
	private static final float SAME_SPEED = 0.001f;

	private float visual = 1f;
	private float lastReal = 1f;
	private boolean running;

	/**
	 * Moves on by one tick.
	 *
	 * @param real       the game's timer for the item's height, from 0 to 1
	 * @param swinging   whether the player is swinging, as after a hit
	 * @param speed      the swing's speed, 1 for the game's
	 * @param delayTicks how long the game's timer runs
	 * @return the timer for the look, from 0 to 1, or -1 when the game's own applies
	 */
	public float step(float real, boolean swinging, float speed, float delayTicks) {
		boolean reset = real < lastReal;
		lastReal = real;
		if (Math.abs(speed - 1f) < SAME_SPEED) {
			running = false;
			return -1f;
		}
		float perTick = 1f / Math.max(1f, delayTicks);
		if (reset) {
			running = swinging;
			visual = perTick;
		} else if (running) {
			visual = Math.min(1f, visual + speed * perTick);
		}
		if (!running) {
			return -1f;
		}
		// over only when both have run out: at a fast swing the game's timer is still low, and going back to it would drop the item again
		if (visual >= 1f && real >= 1f) {
			running = false;
		}
		return visual;
	}

	/** @return how high the item stands after a tick, from where it stood, for the look's timer: the game's motion at the swing's speed */
	public static float height(float before, float timer, float speed) {
		float most = MOST_PER_TICK * speed;
		float target = timer * timer * timer;
		return before + Math.max(-most, Math.min(most, target - before));
	}
}
