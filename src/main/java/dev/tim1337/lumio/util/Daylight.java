package dev.tim1337.lumio.util;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** How much it is day in the world, for things that light or dim with it: the world lyrics, and the cinematic camera's choice of shots. */
public final class Daylight {
	private static final int TICKS_PER_DAY = 24000;
	private static final int NOON_TICKS = 6000;
	/** Rain takes this much of the daylight away. */
	private static final float RAIN_DIM = 0.4f;

	private Daylight() {
	}

	/** @return how much it is day, from 0 (night) to 1 (a clear noon); full day well before noon, dark well after sunset */
	public static float of(Level level) {
		long time = level.getOverworldClockTime() % TICKS_PER_DAY;
		float day = Mth.clamp(0.5f + 1.2f * (float) Math.cos(2.0 * Math.PI * (time - NOON_TICKS) / TICKS_PER_DAY), 0f, 1f);
		return day * (1f - RAIN_DIM * level.getRainLevel(1f));
	}
}
