package dev.tim1337.lumio.render;

/**
 * A rectangle cut to a box with round corners, as a stack of rows that share their edges exactly. Light that is cut this way has no seams: a scissor is whole GUI units after the pose, so
 * rows cut with scissors in a window of any other size overlapped or left a gap, and each showed as a thin line.
 */
public final class RoundedRows {
	/** Rows per corner: their straight sides stay well within a pixel of the curve at any window size. */
	private static final int ROWS_PER_CORNER = 16;
	/** How many numbers one edge takes: its height, left and right. */
	public static final int STRIDE = 3;

	private RoundedRows() {
	}

	/**
	 * @param left the rectangle's left side; top, right and bottom likewise
	 * @param boxLeft the box whose corners cut it; boxTop, boxRight and boxBottom likewise
	 * @param radius the radius of the box's corners
	 * @return the edges from top to bottom, each as y, left and right: two edges in a row make one row, without a gap or an overlap between them. Empty if nothing is left.
	 */
	public static float[] edges(float left, float top, float right, float bottom, float boxLeft, float boxTop, float boxRight, float boxBottom, float radius) {
		float cutTop = Math.max(top, boxTop);
		float cutBottom = Math.min(bottom, boxBottom);
		if (cutBottom <= cutTop || right <= left) {
			return new float[0];
		}
		float[] edges = new float[(2 * ROWS_PER_CORNER + 2) * STRIDE];
		int count = put(edges, 0, cutTop, left, right, boxLeft, boxTop, boxRight, boxBottom, radius);
		// the corners' rows are even along the curve, not along the height: the curve runs nearly flat at its end
		for (int i = 1; i < ROWS_PER_CORNER; i++) {
			float y = boxTop + along(i, radius);
			if (y > cutTop && y < cutBottom) {
				count = put(edges, count, y, left, right, boxLeft, boxTop, boxRight, boxBottom, radius);
			}
		}
		for (int i = ROWS_PER_CORNER - 1; i >= 1; i--) {
			float y = boxBottom - along(i, radius);
			if (y > cutTop && y < cutBottom) {
				count = put(edges, count, y, left, right, boxLeft, boxTop, boxRight, boxBottom, radius);
			}
		}
		count = put(edges, count, cutBottom, left, right, boxLeft, boxTop, boxRight, boxBottom, radius);
		float[] used = new float[count];
		System.arraycopy(edges, 0, used, 0, count);
		return used;
	}

	private static float along(int step, float radius) {
		return (float) (radius * (1.0 - Math.cos(step * Math.PI / 2.0 / ROWS_PER_CORNER)));
	}

	private static int put(float[] edges, int count, float y, float left, float right, float boxLeft, float boxTop, float boxRight, float boxBottom, float radius) {
		float inset = inset(Math.min(y - boxTop, boxBottom - y), radius);
		edges[count] = y;
		edges[count + 1] = Math.max(left, boxLeft + inset);
		edges[count + 2] = Math.min(right, boxRight - inset);
		return count + STRIDE;
	}

	/** @return how far in from the box's side the curve is, at a distance from the box's top or bottom */
	private static float inset(float distance, float radius) {
		if (distance >= radius) {
			return 0f;
		}
		float away = radius - Math.max(distance, 0f);
		return (float) (radius - Math.sqrt(radius * radius - away * away));
	}
}
