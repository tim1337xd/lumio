package dev.tim1337.lumio.render;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Turns two GPU timestamps of the game's query pools into milliseconds. The pools hand back the graphics card's raw ticks, and a tick is not a
 * nanosecond on every card: on many AMD cards it is 10 ns, which made every time measured there read a tenth of the truth. The length of a tick
 * comes from the device.
 */
public final class GpuTimestamps {
	private static final double NANOS_PER_MILLI = 1_000_000.0;
	private static double nanosPerTick;

	private GpuTimestamps() {
	}

	/** @return the time between two timestamps of the same pool in milliseconds, or -1 when they make no sense (the later one is earlier) */
	public static double millis(long start, long end) {
		if (end < start) {
			return -1.0;
		}
		return (end - start) * nanosPerTick() / NANOS_PER_MILLI;
	}

	private static double nanosPerTick() {
		if (nanosPerTick <= 0.0) {
			float period = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
			nanosPerTick = period > 0f && Float.isFinite(period) ? period : 1.0;
		}
		return nanosPerTick;
	}
}
