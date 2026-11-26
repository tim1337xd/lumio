package dev.tim1337.lumio.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.ui.EditableHud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Keeps the HUD on the screen and its panels off each other, on any screen and GUI scale: once per frame, after the HUD is drawn, it takes where
 * every shown element lies as the player placed it and moves the movable ones off the elements of higher priority (the hotbar and the bars and the
 * info bar stay; then the chat, the scoreboard, then the other panels in the editor's order), along the edge each is anchored at ({@link HudFit}).
 * The moves are never saved: the player's own places stay their own, and once there is room again the panels are back there. The next frame
 * draws them moved; one frame late, which nobody sees, so nothing is drawn twice. With the chat over the hotbar (the chat's choice), the chat and the
 * hotbar with what belongs to it (the text above it, the status bars and whatever lies in their rows, such as an add-on's panel beside the hotbar)
 * do not move off each other: the game draws the chat after all of them.
 */
public final class HudAutoFit {
	/** The most elements the fit looks at; the HUD has far fewer. */
	private static final int MOST = 96;

	private static EditableHud[] order;
	private static final float[] BOXES = new float[MOST * 4];
	private static final int[] AXES = new int[MOST];
	private static final int[] KINDS = new int[MOST];
	private static final float[] SHIFTS = new float[MOST * 2];
	/** What the last fit was made from, to skip it while nothing changed. */
	private static final float[] LAST_BOXES = new float[MOST * 4];
	private static final int[] LAST_AXES = new int[MOST];
	private static int lastCount = -1;
	private static int lastWidth;
	private static int lastHeight;
	private static boolean lastOver;

	private HudAutoFit() {
	}

	/** Fits the HUD to a screen of this size in GUI units; called after the HUD is drawn. */
	public static void update(int screenWidth, int screenHeight) {
		EditableHud[] elements = order();
		int count = 0;
		for (EditableHud element : elements) {
			if (count == MOST) {
				break;
			}
			int[] box = element.hudShown() ? element.hudNaturalBounds() : null;
			if (box == null || box[2] <= 0 || box[3] <= 0) {
				// not shown: it takes no room and is not moved
				element.hudSetFitShift(0f, 0f);
				continue;
			}
			BOXES[count * 4] = box[0];
			BOXES[count * 4 + 1] = box[1];
			BOXES[count * 4 + 2] = box[2];
			BOXES[count * 4 + 3] = box[3];
			AXES[count] = fixed(element) ? 0 : element.hudFitAxis();
			count++;
		}
		boolean over = ModFeatures.CHAT != null && ModFeatures.CHAT.overHotbar();
		if (count == lastCount && screenWidth == lastWidth && screenHeight == lastHeight && over == lastOver
			&& Arrays.equals(BOXES, 0, count * 4, LAST_BOXES, 0, count * 4) && Arrays.equals(AXES, 0, count, LAST_AXES, 0, count)) {
			return;
		}
		lastOver = over;
		kinds(elements, count, over);
		System.arraycopy(BOXES, 0, LAST_BOXES, 0, count * 4);
		System.arraycopy(AXES, 0, LAST_AXES, 0, count);
		lastCount = count;
		lastWidth = screenWidth;
		lastHeight = screenHeight;
		HudFit.resolve(BOXES, AXES, over ? KINDS : null, count, screenWidth, screenHeight, SHIFTS);
		int at = 0;
		for (EditableHud element : elements) {
			if (at == count) {
				break;
			}
			int[] box = element.hudShown() ? element.hudNaturalBounds() : null;
			if (box == null || box[2] <= 0 || box[3] <= 0) {
				continue;
			}
			element.hudSetFitShift(SHIFTS[at * 2], SHIFTS[at * 2 + 1]);
			at++;
		}
	}

	/**
	 * Marks the chat as over and the hotbar's group as under it: the hotbar, the text above it, the status bars, and every element that reaches into
	 * the rows from their top to the screen's bottom. Fills {@link #KINDS} in the order of {@link #BOXES}.
	 */
	private static void kinds(EditableHud[] elements, int count, boolean over) {
		Arrays.fill(KINDS, 0, count, 0);
		if (!over) {
			return;
		}
		float bandTop = Float.MAX_VALUE;
		int at = 0;
		for (EditableHud element : elements) {
			if (at == count) {
				break;
			}
			if (!counted(element)) {
				continue;
			}
			if (hotbarGroup(element)) {
				bandTop = Math.min(bandTop, BOXES[at * 4 + 1]);
			}
			at++;
		}
		at = 0;
		for (EditableHud element : elements) {
			if (at == count) {
				break;
			}
			if (!counted(element)) {
				continue;
			}
			if (element == ModFeatures.CHAT) {
				KINDS[at] = HudFit.OVER;
			} else if (hotbarGroup(element) || BOXES[at * 4 + 1] + BOXES[at * 4 + 3] > bandTop) {
				KINDS[at] = HudFit.UNDER;
			}
			at++;
		}
	}

	/** @return whether an element has a box in {@link #BOXES} this frame, as {@link #update} took them */
	private static boolean counted(EditableHud element) {
		int[] box = element.hudShown() ? element.hudNaturalBounds() : null;
		return box != null && box[2] > 0 && box[3] > 0;
	}

	/** @return whether an element is the hotbar or goes with it wherever it is: the held item's name, the text above it and each status bar */
	private static boolean hotbarGroup(EditableHud element) {
		return element == ModFeatures.HOTBAR || element == ModFeatures.HOTBAR.nameHud() || element == ModFeatures.ACTION_BAR
			|| ModFeatures.STATUS != null && ModFeatures.STATUS.editables().contains(element);
	}

	/** Forgets the last fit, so the next frame makes it again (after "Fit to screen" changed the places). */
	public static void invalidate() {
		lastCount = -1;
	}

	/** @return whether an element stays where it is however the others lie, though it is a panel: the hotbar and the text above it */
	private static boolean fixed(EditableHud element) {
		return element == ModFeatures.HOTBAR || element == ModFeatures.ACTION_BAR;
	}

	/**
	 * @return the elements in the order of their priority, made once: first those that stay (the hotbar, the bars, the info bar, the music
	 *         window), then the chat, the scoreboard, then the other panels as the editor lists them. The player list (an overlay while its key is held)
	 *         and the held item (in the world) take no part.
	 */
	private static EditableHud[] order() {
		if (order == null) {
			List<EditableHud> all = ModFeatures.editableHuds();
			List<EditableHud> sorted = new ArrayList<>();
			for (EditableHud element : all) {
				if (part(element) && (fixed(element) || element.hudFitAxis() == 0)) {
					sorted.add(element);
				}
			}
			sorted.add(ModFeatures.CHAT);
			sorted.add(ModFeatures.SCOREBOARD);
			for (EditableHud element : all) {
				if (part(element) && !sorted.contains(element)) {
					sorted.add(element);
				}
			}
			order = sorted.toArray(new EditableHud[0]);
		}
		return order;
	}

	private static boolean part(EditableHud element) {
		return element != ModFeatures.TAB_LIST && element != ModFeatures.HELD_ITEM_TRANSFORM;
	}
}
