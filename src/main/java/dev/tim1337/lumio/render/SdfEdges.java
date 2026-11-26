package dev.tim1337.lumio.render;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * The outline of a glyph as edges for a multi-channel signed distance field: its contours of straight and quadratic edges, each edge in one or
 * more of the three color channels, so that at every corner the two edges meeting there share exactly one channel. The median of the three
 * distances then keeps the corner sharp where a single distance would round it. Plain logic on AWT shapes, tested without the game.
 */
final class SdfEdges {
	/** The channels: red, green, blue as bits. Cyan, magenta and yellow are two of them each, white all three. */
	static final int RED = 1;
	static final int GREEN = 2;
	static final int BLUE = 4;
	static final int CYAN = GREEN | BLUE;
	static final int MAGENTA = RED | BLUE;
	static final int YELLOW = RED | GREEN;
	static final int WHITE = RED | GREEN | BLUE;
	/** Two edges meet at a corner where their directions turn by more than this, as the sine of the angle (about 3 degrees). */
	private static final double CORNER = Math.sin(3.0 * Math.PI / 180.0);
	/** A cubic edge (not in TrueType fonts) is taken as this many straight pieces. */
	private static final int CUBIC_PIECES = 8;

	/** An edge: from p0 over the control point p1 to p2; a straight edge has p1 halfway. */
	static final class Edge {
		final double x0;
		final double y0;
		final double x1;
		final double y1;
		final double x2;
		final double y2;
		final boolean straight;
		int color = WHITE;

		Edge(double x0, double y0, double x1, double y1, double x2, double y2, boolean straight) {
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.straight = straight;
		}

		/** @return the direction at the start (x, y), not of length 1 */
		double[] startDirection() {
			double dx = straight ? x2 - x0 : x1 - x0;
			double dy = straight ? y2 - y0 : y1 - y0;
			return dx * dx + dy * dy > 1e-12 ? new double[] {dx, dy} : new double[] {x2 - x0, y2 - y0};
		}

		/** @return the direction at the end */
		double[] endDirection() {
			double dx = straight ? x2 - x0 : x2 - x1;
			double dy = straight ? y2 - y0 : y2 - y1;
			return dx * dx + dy * dy > 1e-12 ? new double[] {dx, dy} : new double[] {x2 - x0, y2 - y0};
		}
	}

	private SdfEdges() {
	}

	/** @return the contours of a shape, each a list of edges, already colored */
	static List<List<Edge>> contours(Shape shape) {
		List<List<Edge>> contours = new ArrayList<>();
		List<Edge> contour = null;
		double[] c = new double[6];
		double startX = 0;
		double startY = 0;
		double x = 0;
		double y = 0;
		for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
			switch (it.currentSegment(c)) {
				case PathIterator.SEG_MOVETO -> {
					contour = new ArrayList<>();
					contours.add(contour);
					startX = x = c[0];
					startY = y = c[1];
				}
				case PathIterator.SEG_LINETO -> {
					line(contour, x, y, c[0], c[1]);
					x = c[0];
					y = c[1];
				}
				case PathIterator.SEG_QUADTO -> {
					if (contour != null) {
						contour.add(new Edge(x, y, c[0], c[1], c[2], c[3], false));
					}
					x = c[2];
					y = c[3];
				}
				case PathIterator.SEG_CUBICTO -> {
					double lastX = x;
					double lastY = y;
					for (int i = 1; i <= CUBIC_PIECES; i++) {
						double t = i / (double) CUBIC_PIECES;
						double u = 1 - t;
						double nx = u * u * u * x + 3 * u * u * t * c[0] + 3 * u * t * t * c[2] + t * t * t * c[4];
						double ny = u * u * u * y + 3 * u * u * t * c[1] + 3 * u * t * t * c[3] + t * t * t * c[5];
						line(contour, lastX, lastY, nx, ny);
						lastX = nx;
						lastY = ny;
					}
					x = c[4];
					y = c[5];
				}
				case PathIterator.SEG_CLOSE -> {
					line(contour, x, y, startX, startY);
					x = startX;
					y = startY;
				}
				default -> {
				}
			}
		}
		contours.removeIf(List::isEmpty);
		for (List<Edge> each : contours) {
			color(each);
		}
		return contours;
	}

	private static void line(List<Edge> contour, double x0, double y0, double x1, double y1) {
		if (contour != null && (Math.abs(x1 - x0) > 1e-9 || Math.abs(y1 - y0) > 1e-9)) {
			contour.add(new Edge(x0, y0, (x0 + x1) / 2, (y0 + y1) / 2, x1, y1, true));
		}
	}

	/** @return whether the outline turns sharply from one edge into the next */
	static boolean corner(Edge before, Edge after) {
		double[] a = before.endDirection();
		double[] b = after.startDirection();
		double la = Math.hypot(a[0], a[1]);
		double lb = Math.hypot(b[0], b[1]);
		if (la < 1e-12 || lb < 1e-12) {
			return false;
		}
		double dot = (a[0] * b[0] + a[1] * b[1]) / (la * lb);
		double cross = (a[0] * b[1] - a[1] * b[0]) / (la * lb);
		return dot <= 0 || Math.abs(cross) > CORNER;
	}

	/**
	 * Colors a contour: a smooth one is white (all channels); otherwise the stretches between its corners take turns of cyan and magenta,
	 * and with an odd number of stretches the last is yellow, so the stretches on both sides of every corner differ and share one channel.
	 */
	static void color(List<Edge> contour) {
		int count = contour.size();
		List<Integer> corners = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			if (corner(contour.get((i + count - 1) % count), contour.get(i))) {
				corners.add(i);
			}
		}
		if (corners.size() < 2) {
			for (Edge edge : contour) {
				edge.color = WHITE;
			}
			return;
		}
		int stretches = corners.size();
		for (int s = 0; s < stretches; s++) {
			int color = s % 2 == 0 ? CYAN : MAGENTA;
			if (stretches % 2 == 1 && s == stretches - 1) {
				color = YELLOW;
			}
			int from = corners.get(s);
			int to = corners.get((s + 1) % stretches);
			for (int i = from; i != to; i = (i + 1) % count) {
				contour.get(i).color = color;
			}
		}
	}

	/**
	 * Finds the point of an edge nearest to a point.
	 *
	 * @param out receives the distance, the signed pseudo-distance (beyond its ends along the tangent lines there), and how much the edge
	 *            runs along the way to the point there (for ties: the more across, the better)
	 */
	static void distance(Edge e, double px, double py, double[] out) {
		double bestT;
		double best;
		if (e.straight) {
			double dx = e.x2 - e.x0;
			double dy = e.y2 - e.y0;
			double t = ((px - e.x0) * dx + (py - e.y0) * dy) / (dx * dx + dy * dy);
			bestT = Math.max(0, Math.min(1, t));
			double nx = e.x0 + dx * bestT - px;
			double ny = e.y0 + dy * bestT - py;
			best = Math.sqrt(nx * nx + ny * ny);
		} else {
			// the derivative of the squared distance is a cubic in t; its roots in [0, 1] and both ends are the candidates
			double ax = e.x1 - e.x0;
			double ay = e.y1 - e.y0;
			double bx = e.x2 - 2 * e.x1 + e.x0;
			double by = e.y2 - 2 * e.y1 + e.y0;
			double qx = e.x0 - px;
			double qy = e.y0 - py;
			double a3 = bx * bx + by * by;
			double a2 = 3 * (ax * bx + ay * by);
			double a1 = 2 * (ax * ax + ay * ay) + (qx * bx + qy * by);
			double a0 = qx * ax + qy * ay;
			double[] roots = new double[3];
			int n = solveCubic(a3, a2, a1, a0, roots);
			bestT = 0;
			best = Double.MAX_VALUE;
			for (int i = -2; i < n; i++) {
				double t = i == -2 ? 0 : i == -1 ? 1 : roots[i];
				if (t < 0 || t > 1) {
					continue;
				}
				double x = e.x0 + 2 * t * ax + t * t * bx - px;
				double y = e.y0 + 2 * t * ay + t * t * by - py;
				double d = Math.sqrt(x * x + y * y);
				if (d < best) {
					best = d;
					bestT = t;
				}
			}
		}
		double[] dir = direction(e, bestT);
		double len = Math.max(1e-12, Math.hypot(dir[0], dir[1]));
		double cx = point(e, bestT, true) - px;
		double cy = point(e, bestT, false) - py;
		// the side of the edge the point lies on: by the edge's direction
		double cross = (dir[0] * -cy - dir[1] * -cx) / len;
		double sign = cross >= 0 ? 1 : -1;
		double pseudo = sign * best;
		double along = Math.abs((dir[0] * -cx + dir[1] * -cy) / (len * Math.max(1e-12, best)));
		if (bestT <= 0 || bestT >= 1) {
			// beyond an end: the distance to the tangent line there, where the point lies past the end along it
			double[] d = bestT <= 0 ? e.startDirection() : e.endDirection();
			double l = Math.max(1e-12, Math.hypot(d[0], d[1]));
			double ex = bestT <= 0 ? e.x0 : e.x2;
			double ey = bestT <= 0 ? e.y0 : e.y2;
			double ts = ((px - ex) * d[0] + (py - ey) * d[1]) / l;
			if (bestT <= 0 ? ts < 0 : ts > 0) {
				double perpendicular = (d[0] * (py - ey) - d[1] * (px - ex)) / l;
				if (Math.abs(perpendicular) <= best) {
					pseudo = perpendicular;
				}
			}
		}
		out[0] = best;
		out[1] = pseudo;
		out[2] = along;
	}

	private static double point(Edge e, double t, boolean x) {
		double u = 1 - t;
		return x ? u * u * e.x0 + 2 * u * t * e.x1 + t * t * e.x2 : u * u * e.y0 + 2 * u * t * e.y1 + t * t * e.y2;
	}

	private static double[] direction(Edge e, double t) {
		if (e.straight) {
			return new double[] {e.x2 - e.x0, e.y2 - e.y0};
		}
		double dx = 2 * (1 - t) * (e.x1 - e.x0) + 2 * t * (e.x2 - e.x1);
		double dy = 2 * (1 - t) * (e.y1 - e.y0) + 2 * t * (e.y2 - e.y1);
		return dx * dx + dy * dy > 1e-12 ? new double[] {dx, dy} : new double[] {e.x2 - e.x0, e.y2 - e.y0};
	}

	/** @return how many real roots of a t³ + b t² + c t + d the array got */
	static int solveCubic(double a, double b, double c, double d, double[] roots) {
		if (Math.abs(a) < 1e-12) {
			if (Math.abs(b) < 1e-12) {
				if (Math.abs(c) < 1e-12) {
					return 0;
				}
				roots[0] = -d / c;
				return 1;
			}
			double disc = c * c - 4 * b * d;
			if (disc < 0) {
				return 0;
			}
			double s = Math.sqrt(disc);
			roots[0] = (-c + s) / (2 * b);
			roots[1] = (-c - s) / (2 * b);
			return 2;
		}
		double bn = b / a;
		double cn = c / a;
		double dn = d / a;
		double q = (3 * cn - bn * bn) / 9;
		double r = (9 * bn * cn - 27 * dn - 2 * bn * bn * bn) / 54;
		double disc = q * q * q + r * r;
		double shift = bn / 3;
		if (disc > 0) {
			double s = Math.cbrt(r + Math.sqrt(disc));
			double t = Math.cbrt(r - Math.sqrt(disc));
			roots[0] = s + t - shift;
			return 1;
		}
		double theta = Math.acos(Math.max(-1, Math.min(1, r / Math.sqrt(-q * q * q))));
		double m = 2 * Math.sqrt(-q);
		roots[0] = m * Math.cos(theta / 3) - shift;
		roots[1] = m * Math.cos((theta + 2 * Math.PI) / 3) - shift;
		roots[2] = m * Math.cos((theta + 4 * Math.PI) / 3) - shift;
		return 3;
	}
}
