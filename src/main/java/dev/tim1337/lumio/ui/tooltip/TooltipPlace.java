package dev.tim1337.lumio.ui.tooltip;

/**
 * Where a tooltip goes that must keep clear of some boxes, such as a search bar: it moves up above one it would lie on; where it does not fit there it
 * gets shorter and scrolls. Plain numbers, so it is unit tested.
 */
final class TooltipPlace {
	/** Shorter than this a tooltip is of no use: it stays where it is instead. */
	static final int LEAST_HEIGHT = 48;
	private static final int PASSES = 3;

	private TooltipPlace() {
	}

	/**
	 * @param boxes  the boxes, four values each: left, top, right, bottom, with the room to keep around them already added
	 * @param top    the highest the tooltip may start
	 * @param out    receives the place's top and its height
	 */
	static void fit(int x, int y, int width, int height, int top, int[] boxes, int count, int[] out) {
		int atY = y;
		int tall = height;
		for (int pass = 0; pass < PASSES; pass++) {
			boolean moved = false;
			for (int i = 0; i < count; i++) {
				int left = boxes[i * 4];
				int boxTop = boxes[i * 4 + 1];
				int right = boxes[i * 4 + 2];
				int bottom = boxes[i * 4 + 3];
				if (x >= right || x + width <= left || atY >= bottom || atY + tall <= boxTop) {
					continue;
				}
				int room = boxTop - top;
				if (room < LEAST_HEIGHT) {
					continue;
				}
				tall = Math.min(tall, room);
				atY = boxTop - tall;
				moved = true;
			}
			if (!moved) {
				break;
			}
		}
		out[0] = atY;
		out[1] = tall;
	}
}
