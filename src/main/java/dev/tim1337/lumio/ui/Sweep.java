package dev.tim1337.lumio.ui;

/**
 * A conic sweep in a rounded box: the part of the box from an angle to a full turn, clockwise from the top, filled in one color. Used for item
 * cooldowns, where the darkening sweeps around and shrinks to nothing as the cooldown runs out. It is drawn in screen pixels, two spans per row at
 * most, so it stays smooth at any GUI scale.
 */
public final class Sweep {
	private static final double TURN = Math.PI * 2.0;

	private Sweep() {
	}

	/**
	 * @param left     share of the turn still covered, from 0 (nothing) to 1 (the whole box); the covered part ends at the top
	 * @param radius   corner radius of the box, in GUI pixels
	 * @param guiScale screen pixels per GUI pixel, so every screen row gets its own spans
	 */
	public static void draw(Ui ui, float x, float y, float width, float height, float radius, float left, int color, int guiScale) {
		if (left <= 0.001f || (color >>> 24) == 0) {
			return;
		}
		int scale = Math.max(1, guiScale);
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(x, y);
			pose.scale(1f / scale, 1f / scale);
			int w = Math.round(width * scale);
			int h = Math.round(height * scale);
			float r = Math.min(radius * scale, Math.min(w, h) / 2f);
			double from = TURN * (1.0 - Math.min(1f, left));
			double cx = w / 2.0;
			double cy = h / 2.0;
			for (int row = 0; row < h; row++) {
				// the row's extent inside the rounded corners
				double py = row + 0.5;
				double inset = 0;
				if (py < r) {
					double d = r - py;
					inset = r - Math.sqrt(Math.max(0, r * r - d * d));
				} else if (py > h - r) {
					double d = py - (h - r);
					inset = r - Math.sqrt(Math.max(0, r * r - d * d));
				}
				int rowLeft = (int) Math.round(inset);
				int rowRight = w - rowLeft;
				if (rowRight <= rowLeft) {
					continue;
				}
				double dy = py - cy;
				span(ui, row, rowLeft, rowRight, cx, dy, from, color);
			}
		} finally {
			pose.popMatrix();
		}
	}

	/** Fills the parts of one row whose angle (clockwise from the top) is at least {@code from}. */
	private static void span(Ui ui, int row, int rowLeft, int rowRight, double cx, double dy, double from, int color) {
		// the angle changes monotonically along each half of a row (left and right of the center), so each half holds at most one covered span
		int split = (int) Math.round(cx);
		fillCovered(ui, row, rowLeft, Math.max(rowLeft, Math.min(split, rowRight)), cx, dy, from, color);
		fillCovered(ui, row, Math.max(rowLeft, Math.min(split, rowRight)), rowRight, cx, dy, from, color);
	}

	private static void fillCovered(Ui ui, int row, int start, int end, double cx, double dy, double from, int color) {
		if (end <= start) {
			return;
		}
		boolean firstIn = angle(start + 0.5 - cx, dy) >= from;
		boolean lastIn = angle(end - 0.5 - cx, dy) >= from;
		if (firstIn && lastIn) {
			ui.fill(start, row, end - start, 1, color);
			return;
		}
		if (!firstIn && !lastIn) {
			return;
		}
		// one boundary inside the half: find it by bisection
		int low = start;
		int high = end - 1;
		while (high - low > 1) {
			int mid = (low + high) >>> 1;
			boolean in = angle(mid + 0.5 - cx, dy) >= from;
			if (in == firstIn) {
				low = mid;
			} else {
				high = mid;
			}
		}
		if (firstIn) {
			ui.fill(start, row, high - start, 1, color);
		} else {
			ui.fill(high, row, end - high, 1, color);
		}
	}

	/** @return the angle of a point around the center, clockwise from the top, from 0 to 2π */
	private static double angle(double dx, double dy) {
		double a = Math.atan2(dx, -dy);
		return a < 0 ? a + TURN : a;
	}
}
