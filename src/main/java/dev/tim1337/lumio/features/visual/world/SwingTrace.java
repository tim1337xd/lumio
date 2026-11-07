package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.Logs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The swing of the first person hand, frame by frame, for finding out why it looks wrong: the game's values, the hand's own counter, the item's
 * drop when changed, the frame's time, and which of Lumio's hand paths ran. Shown at the top left and written to the log while attack is held or a
 * swing runs, only while "Show the swing values" is on under the Debug log; otherwise each hook costs one check.
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
	private static final int TOP = 4;
	private static final int LEFT = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int BACKDROP_COLOR = 0x90000000;
	private static final int BACKDROP_WIDTH = 230;

	private static int framePaths;
	private static int tickPaths;
	private static int shownPaths;
	private static long lastFrameNanos;
	private static float equip;
	private static long frames;
	private static final String[] lines = new String[4];

	private SwingTrace() {
	}

	/** @return whether the values are shown and written */
	public static boolean on() {
		return ModFeatures.DEBUG.tracesSwing();
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
		if (client.options.keyAttack.isDown() || player.swinging || ModFeatures.HELD_ITEM_TRANSFORM.swingRunning()) {
			Logs.info("Swing frame {}: {} | {} | {} | {}", frames, lines[0], lines[1], lines[2], lines[3]);
		}
	}

	/** Draws the values of the last frame at the top left. */
	public static void draw(GuiGraphicsExtractor graphics) {
		if (!on() || lines[0] == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		graphics.fill(LEFT - 2, TOP - 2, LEFT + BACKDROP_WIDTH, TOP + lines.length * LINE_HEIGHT, BACKDROP_COLOR);
		for (int i = 0; i < lines.length; i++) {
			graphics.text(client.font, lines[i], LEFT, TOP + i * LINE_HEIGHT, TEXT_COLOR, false);
		}
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
