package dev.tim1337.lumio.render;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.features.visual.world.SkyFeature;

import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * The sky's quality Auto: what the sky costs on the graphics card is measured for a few seconds, from High down, and the highest level that keeps it
 * within {@link #BUDGET_MILLIS} per frame is kept. The choice is remembered with a mark of the graphics (card, backend, the game's Graphics and the
 * screen's size class): the next start uses it at once, and a change of any of them measures again. Afterwards a cheap watch stays on, since the
 * first seconds may have shown a light sky (clouds by day) and a later one costs more (the aurora by night): when that runs well over the budget for
 * a while, one level lower is kept. It never goes up again on its own; a new mark measures from High.
 * <p>
 * The time is that of the GPU, from timestamps of the game's queries around the sky's passes (prepare and the draw in the sky's pass, the clouds
 * over the sky and over the land), read a few frames late so nothing waits for the GPU. Only frames in which the mod's sky drew something count.
 */
public final class SkyAutoQuality {
	/** The most the sky may cost per frame on the GPU, in milliseconds, at the level Auto keeps: a quarter of a frame at 60 FPS. */
	static final double BUDGET_MILLIS = 4.0;
	/** Once settled, a level is lowered only when a window costs this much more than the budget. */
	static final double LATER_FACTOR = 1.5;
	/** How long a window of measured frames is, and the fewest frames it needs. */
	private static final long WINDOW_NANOS = 2_000_000_000L;
	private static final int WINDOW_FRAMES = 20;
	/** Frames not counted after a level changed: the clouds cross-fade, and the first frames of new sizes cost more. */
	private static final int SETTLE_FRAMES = 10;
	/** How many frames a timestamp may take to come back. */
	private static final int SLOTS = 4;
	/** The spans of a frame timed apart: the sky, the clouds over the sky, the clouds over the land. */
	public static final int SKY = 0;
	public static final int CLOUDS_OVER_SKY = 1;
	public static final int CLOUDS = 2;
	private static final int SPANS = 3;
	/** The level Auto starts measuring at, and what it uses while the GPU cannot time. */
	private static final int HIGHEST = 2;
	private static final int FALLBACK = 1;
	/** Screens of about the same number of pixels share a size class: a window resized a little keeps the choice. */
	private static final double SIZE_CLASS_STEP = 0.5;

	private static GpuQueryPool pool;
	private static boolean failed;
	private static int slot = -1;
	private static final boolean[][] written = new boolean[SLOTS][SPANS];
	private static final boolean[] counted = new boolean[SLOTS];
	private static boolean[] open = new boolean[SPANS];

	private static int level = HIGHEST;
	private static boolean settled;
	private static boolean restored;
	private static int mark;
	private static int skip;
	private static double windowSum;
	private static int windowFrames;
	private static long windowStart;

	private SkyAutoQuality() {
	}

	/** @return the level Auto uses now: 0 Low, 1 Medium, 2 High */
	public static int level() {
		if (failed) {
			return FALLBACK;
		}
		check();
		return level;
	}

	/** Starts the timing of a frame's sky, at the start of its first pass; reads the frame that used this slot before. */
	public static void frameStart() {
		if (!measuring()) {
			return;
		}
		// a frame whose sky pass never told its end (a pass the game left out) is closed here, so the next begins
		for (int span = 0; span < SPANS; span++) {
			end(span);
		}
		try {
			if (pool == null) {
				pool = RenderSystem.getDevice().createTimestampQueryPool(SLOTS * SPANS * 2);
			}
			slot = (slot + 1) % SLOTS;
			readSlot(slot);
			counted[slot] = false;
		} catch (RuntimeException | Error error) {
			fail(error);
			return;
		}
		begin(SKY);
	}

	/** Marks the frame as one in which the mod's sky drew something, so it counts. */
	public static void drew() {
		if (slot >= 0 && measuring()) {
			counted[slot] = true;
		}
	}

	/** Writes the timestamp before a span of the frame; no pass may be open on the encoder. */
	public static void begin(int span) {
		if (slot < 0 || !measuring() || open[span] || written[slot][span]) {
			return;
		}
		try {
			RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, index(slot, span));
			open[span] = true;
		} catch (RuntimeException | Error error) {
			fail(error);
		}
	}

	/** Writes the timestamp after a span begun with {@link #begin}; nothing when none is open. */
	public static void end(int span) {
		if (!open[span] || failed) {
			return;
		}
		open[span] = false;
		try {
			RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, index(slot, span) + 1);
			written[slot][span] = true;
		} catch (RuntimeException | Error error) {
			fail(error);
		}
	}

	private static int index(int at, int span) {
		return (at * SPANS + span) * 2;
	}

	private static boolean measuring() {
		SkyFeature sky = ModFeatures.SKY;
		return !failed && sky != null && sky.isEnabled() && sky.autoQuality();
	}

	/** Adds the slot's frame to the window when all of its spans came back; a frame whose results are not there yet is left out. */
	private static void readSlot(int at) {
		boolean any = false;
		boolean complete = true;
		double millis = 0.0;
		for (int span = 0; span < SPANS; span++) {
			if (!written[at][span]) {
				continue;
			}
			written[at][span] = false;
			any = true;
			OptionalLong start = pool.getValue(index(at, span));
			OptionalLong end = pool.getValue(index(at, span) + 1);
			double spanMillis = start.isPresent() && end.isPresent() ? GpuTimestamps.millis(start.getAsLong(), end.getAsLong()) : -1.0;
			if (spanMillis < 0.0) {
				complete = false;
			} else {
				millis += spanMillis;
			}
		}
		if (any && complete && counted[at]) {
			sample(millis);
		}
	}

	/** One frame's cost of the sky: averaged over a window, then the level is kept or lowered. */
	static void sample(double millis) {
		if (skip > 0) {
			skip--;
			return;
		}
		long now = System.nanoTime();
		if (windowFrames == 0) {
			windowStart = now;
		}
		windowSum += millis;
		windowFrames++;
		if (windowFrames < WINDOW_FRAMES || now - windowStart < WINDOW_NANOS) {
			return;
		}
		double average = windowSum / windowFrames;
		windowSum = 0.0;
		windowFrames = 0;
		Decision decision = decide(level, settled, average);
		boolean changed = decision.level() != level;
		if (changed) {
			Logs.info("Sky quality Auto: the sky cost {} ms per frame on the GPU, stepping down to {}", format(average), name(decision.level()));
			level = decision.level();
			skip = SETTLE_FRAMES;
		}
		if (decision.settled() && (!settled || changed)) {
			if (!settled) {
				Logs.info("Sky quality Auto chose {} ({} ms per frame on the GPU)", name(level), format(average));
			}
			settled = true;
			remember();
		}
	}

	/** What Auto does after a window: the level to use, and whether the measuring is over. */
	record Decision(int level, boolean settled) {
	}

	/** @return the level and state after a window that cost the average given, at the level and state given */
	static Decision decide(int level, boolean settled, double average) {
		double limit = settled ? BUDGET_MILLIS * LATER_FACTOR : BUDGET_MILLIS;
		if (average > limit && level > 0) {
			// while measuring, the next window measures the lower level; settled, the lower level is kept at once
			return new Decision(level - 1, settled || level - 1 == 0);
		}
		return new Decision(level, true);
	}

	/** Takes the remembered choice when its mark is the graphics' now, or measures again from High; checked every frame, without allocating. */
	private static void check() {
		int now = currentMark();
		if (restored && now == mark) {
			return;
		}
		SkyFeature sky = ModFeatures.SKY;
		int saved = sky == null ? -1 : savedLevel(sky.autoQualityMemory(), now);
		if (!restored && saved >= 0) {
			level = saved;
			settled = true;
		} else {
			level = HIGHEST;
			settled = false;
		}
		restored = true;
		mark = now;
		windowSum = 0.0;
		windowFrames = 0;
		skip = SETTLE_FRAMES;
	}

	/** @return the level remembered for the mark given, or -1 when the memory is of other graphics or unreadable */
	static int savedLevel(String memory, int mark) {
		int colon = memory.indexOf(':');
		if (colon != 1) {
			return -1;
		}
		int saved = memory.charAt(0) - '0';
		if (saved < 0 || saved > HIGHEST) {
			return -1;
		}
		try {
			return Integer.parseUnsignedInt(memory.substring(colon + 1), 16) == mark ? saved : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** @return the memory of a level for a mark, as {@link #savedLevel} reads it */
	static String memory(int level, int mark) {
		return level + ":" + Integer.toHexString(mark);
	}

	private static void remember() {
		SkyFeature sky = ModFeatures.SKY;
		if (sky != null) {
			sky.rememberAutoQuality(memory(level, mark));
		}
	}

	/** A hash of what the sky's cost depends on: the card and backend, the game's Graphics, and the size class of the screen. Only the hash is saved. */
	private static int currentMark() {
		var info = RenderSystem.getDevice().getDeviceInfo();
		Minecraft client = Minecraft.getInstance();
		int pixels = Math.max(1, client.getWindow().getWidth()) * Math.max(1, client.getWindow().getHeight());
		int sizeClass = (int) Math.round(Math.log(pixels) / Math.log(2.0) / SIZE_CLASS_STEP);
		int hash = info.name().hashCode();
		hash = hash * 31 + info.backendName().hashCode();
		hash = hash * 31 + info.driverInfo().hashCode();
		hash = hash * 31 + client.options.graphicsPreset().get().ordinal();
		return hash * 31 + sizeClass;
	}

	private static void fail(Throwable error) {
		failed = true;
		open = new boolean[SPANS];
		Logs.info("The graphics card cannot time the sky, so its quality Auto uses Medium", error);
	}

	private static String name(int level) {
		return switch (level) {
			case 0 -> "Low";
			case 1 -> "Medium";
			default -> "High";
		};
	}

	private static String format(double millis) {
		return String.format(Locale.ROOT, "%.2f", millis);
	}
}
