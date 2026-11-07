package dev.tim1337.lumio.features.visual.screen.chat;

import java.util.Arrays;

/**
 * Which tabs lie in the row and which go into "More": as many as fit, in their order, and the open one always in the row, in the place of the last that fits. Plain numbers, so it is unit tested.
 */
public final class ChatTabFit {
	/** The tabs by their place in the player's order: those in the row, and those in More, which is empty if all fit. */
	public record Fit(int[] row, int[] more) {
	}

	private ChatTabFit() {
	}

	/**
	 * @param widths    how wide each tab is
	 * @param open      which tab is open
	 * @param room      how wide the row may be
	 * @param moreWidth how wide the More button is
	 * @param gap       between two neighbours
	 */
	public static Fit of(int[] widths, int open, int room, int moreWidth, int gap) {
		int count = widths.length;
		if (needed(widths, gap) <= room) {
			int[] all = new int[count];
			Arrays.setAll(all, i -> i);
			return new Fit(all, new int[0]);
		}
		int chosen = Math.clamp(open, 0, count - 1);
		// every tab has a gap after it: to the next one, or to More
		int free = room - moreWidth;
		int taken = 0;
		while (taken < count && free >= widths[taken] + gap) {
			free -= widths[taken] + gap;
			taken++;
		}
		boolean swaps = chosen >= taken;
		if (swaps) {
			while (taken > 0 && free < widths[chosen] + gap) {
				taken--;
				free += widths[taken] + gap;
			}
		}
		int[] row = new int[swaps ? taken + 1 : taken];
		for (int i = 0; i < taken; i++) {
			row[i] = i;
		}
		if (swaps) {
			row[taken] = chosen;
		}
		int[] more = new int[count - row.length];
		int at = 0;
		for (int i = 0; i < count; i++) {
			if (i >= taken && !(swaps && i == chosen)) {
				more[at++] = i;
			}
		}
		return new Fit(row, more);
	}

	/** @return how wide a row of all these tabs is */
	public static int needed(int[] widths, int gap) {
		int sum = 0;
		for (int width : widths) {
			sum += width;
		}
		return sum + Math.max(0, widths.length - 1) * gap;
	}
}
