package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.debug.DebugCategory;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The swing of the first person hand, frame by frame, for finding out why it looks wrong: the game's values, the hand's own counter, the item's
 * drop when changed, the frame's time, and which of Lumio's hand paths ran. A probe: the lines are made only while a debug tool asks for them
 * ({@link DebugHooks#wants} with {@link DebugCategory#SWING}), which shows them and logs them while a swing runs; otherwise each hook costs one
 * check.
 */
public final class SwingTrace {
	/** The hand's swing was drawn from Lumio's counter in this frame. */
	public static final int DRAWN = 1;
	/** The fancy swing moved and turned the item. */
	public static final int FANCY = 1 << 1;
	/** The swing's movement was made shorter for a small item. */
	public static final int SHORTER = 1 << 2;
	/** The item's height was set by the dip after a hit (this tick). */
	public static final int DIP = 1 << 3;
	/** The game reset the item's swap timer during a swing, and it was kept (this tick). */
	public static final int KEPT_SWAP = 1 << 4;
	/** The server only rewrote the item in the hand, and it was not dropped and raised (this tick). */
	public static final int SAME_ITEM = 1 << 5;
	/** A click started the hand's swing (this tick). */
	public static final int CLICK_STARTED = 1 << 6;
	/** A click came during the hand's swing and started nothing (this tick). */
	public static final int CLICK_DURING = 1 << 7;
	/** While mining, the next swing followed straight on (this tick). */
	public static final int CHAINED = 1 << 8;
	private static final String[] PATH_NAMES = {"drawn", "fancy", "shorter", "dip", "keptSwap", "sameItem", "clickStarted", "clickDuring", "chained"};
	/** The paths that belong to a frame; the others last for the tick they happened in. */
	private static final int FRAME_PATHS = DRAWN | FANCY | SHORTER;

	private static int framePaths;
	private static int tickPaths;
	private static int shownPaths;
	private static long lastFrameNanos;
	private static float equip;
	private static long frames;
	private static final String[] lines = new String[4];

	private SwingTrace() {
	}

	/** @return whether a debug tool asks for the values */
	public static boolean on() {
		return DebugHooks.wants(DebugCategory.SWING);
	}

	/** One of Lumio's hand paths ran. */
	public static void ran(int path) {
		if ((path & FRAME_PATHS) != 0) {
			framePaths |= path;
		} else {
			tickPaths |= path;
		}
	}

	/** A new tick of the hand's swing begins: what happened in the tick before is let go. */
	public static void newTick() {
		tickPaths = 0;
	}

	/** How far the item in a hand is dropped for a change, from 0 (in place) to 1. */
	static void equip(float share) {
		equip = share;
	}

	/** The hand's swing of this frame: what was drawn, against the game's own, at the partial tick. */
	static void frame(float drawnSwing, float gameSwing, float partialTick, float swingTicks) {
		if (!on()) {
			framePaths = 0;
			return;
		}
		long now = System.nanoTime();
		double frameMillis = lastFrameNanos == 0L ? 0.0 : (now - lastFrameNanos) / 1e6;
		lastFrameNanos = now;
		shownPaths = framePaths | tickPaths;
		framePaths = 0;
		frames++;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		// 26.3 keeps the swing's ticks inside its swing description, which has no counter of its own to show
		//#if 26.3
		//$ String swingTime = "-";
		//#else
		String swingTime = Integer.toString(player.swingTime);
		//#endif
		lines[0] = "game: attackAnim " + round(gameSwing) + " swingTime " + swingTime + " swinging " + player.swinging + " partial " + round(partialTick);
		lines[1] = "hand: drawn " + round(drawnSwing) + " running " + ModFeatures.HELD_ITEM_TRANSFORM.swingRunning() + " length " + round(swingTicks) + " ticks, equip " + round(equip);
		lines[2] = "frame: " + round((float) frameMillis) + " ms, " + client.getFps() + " fps, attack held " + client.options.keyAttack.isDown();
		lines[3] = "paths: " + pathNames(shownPaths);
		// "swing" while attack is held or a swing runs (the frames worth logging), else "idle"; the lines one under the other
		boolean swinging = client.options.keyAttack.isDown() || player.swinging || ModFeatures.HELD_ITEM_TRANSFORM.swingRunning();
		DebugHooks.event(DebugCategory.SWING, swinging ? "swing" : "idle", "frame " + frames + "\n" + String.join("\n", lines));
	}

	private static String pathNames(int paths) {
		StringBuilder names = new StringBuilder();
		for (int i = 0; i < PATH_NAMES.length; i++) {
			if ((paths & 1 << i) != 0) {
				names.append(names.isEmpty() ? "" : ", ").append(PATH_NAMES[i]);
			}
		}
		return names.isEmpty() ? "none (the game's swing)" : names.toString();
	}

	private static String round(float value) {
		return Float.toString(Math.round(value * 1000f) / 1000f);
	}
}
