package dev.tim1337.lumio.features.music;

import com.google.gson.JsonObject;

/**
 * Where a block of the lyrics in the world goes: the view decides, not what the player is doing. From the coarse grid of rays across the screen
 * ({@link WorldLyricOpenSky}: how far the view reaches in each cell, and where the sky is) a spot is chosen in this order of preference:
 * <ol>
 * <li>open sky, far away;</li>
 * <li>an open spot near the player (a clearing, a hall, along a tunnel), at the depth that is free there;</li>
 * <li>before the blocks in front (a wall, a cave, a room): off to the side or above, by default in the upper left or right of the view.</li>
 * </ol>
 * The block never stands in the quiet zone around the crosshair, which is small in an open view and grows as the surface the player looks at comes
 * near; where the room is tight it shows fewer rows rather than move into it. Screen places run from -1 to 1 across and up the picture (0 is the
 * crosshair); the quiet zone is a share of the screen's height. Plain logic, tested without the game.
 */
final class LyricSpots {
	/** The player's choice: the sky when a calm piece of it is in view and around otherwise, always around, or only the sky. */
	enum Mode {
		AUTO, AROUND, SKY
	}

	/** Where a block stands, best first. */
	enum Kind {
		SKY, OPEN, WALL, NONE
	}

	/** In the sky a block stands this far away, whatever the render distance. */
	static final double SKY_BLOCKS = LyricReach.SKY_BLOCKS;
	/** Scenery farther than this may be drawn over; anything nearer blocks the sight to a block behind it. */
	static final double DRAWN_OVER_BLOCKS = 24.0;
	/** An open spot: the block stands at this share of the depth that is free there, between these distances. */
	static final double OPEN_SHARE = 0.85;
	static final double OPEN_LEAST_BLOCKS = 8.0;
	static final double OPEN_MOST_BLOCKS = 48.0;
	/** Before a wall: this far in front of the surface, and never nearer than this. */
	static final double WALL_GAP_BLOCKS = 1.0;
	static final double WALL_LEAST_BLOCKS = 3.0;
	/** A piece of sky is calm enough when this share of the block's area shows sky. */
	static final double SKY_SHARE = 0.6;
	/**
	 * The quiet zone around the crosshair, as a share of the screen's height, measured to the block's nearest edge: this small with a surface at
	 * {@link #QUIET_FAR_BLOCKS} or more at the crosshair, this large with one at {@link #QUIET_NEAR_BLOCKS} or less, by the logarithm of the
	 * distance between.
	 */
	static final float QUIET_LEAST = 0.06f;
	static final float QUIET_MOST = 0.30f;
	static final double QUIET_NEAR_BLOCKS = 2.0;
	static final double QUIET_FAR_BLOCKS = 48.0;
	/** The cells that count as "at and around the crosshair": this far from it across and up. */
	private static final double CROSSHAIR_ACROSS = 0.25;
	private static final double CROSSHAIR_UP = 0.12;
	/** A block keeps this far from the screen's edges. */
	static final double EDGE = 0.04;
	/** In the sky or an open spot a block has at least this many rows (or all it wants, if fewer); with less room the next kind is tried first. */
	static final int GOOD_ROWS = 3;
	/** The last resort before a wall: one row, this much smaller. Under that nothing is shown. */
	static final float LEAST_SCALE = 0.75f;
	/** Before a wall a block aims at the upper left or right of the view: here, across and up. */
	static final double CORNER_ACROSS = 0.55;
	static final double CORNER_UP = 0.5;
	/** How much each wish counts among the spots that are allowed. */
	private static final double DEPTH_WEIGHT = 1.0;
	private static final double CALM_WEIGHT = 1.5;
	private static final double NEAR_ZONE_WEIGHT = 2.0;
	private static final double HORIZON_WEIGHT = 0.4;
	private static final double HORIZON_REACH = 0.3;
	private static final double SIDE_WEIGHT = 0.5;
	private static final double AHEAD_WEIGHT = 1.5;
	private static final double CORNER_WEIGHT = 2.0;
	private static final String PLACEMENT_KEY = "world_placement";
	private static final String PLACE_KEY = "world_place";

	private LyricSpots() {
	}

	/** What is asked for: the view, the block and what it must keep clear of. One, reused. */
	static final class Ask {
		Mode mode = Mode.AUTO;
		/** The grid: per cell how far the view reaches, whether it shows sky, and how bright what it shows is; row by row from the top. */
		float[] depth;
		boolean[] sky;
		float[] light;
		int columns;
		int rows;
		/** The screen's width over its height. */
		double aspect = 16.0 / 9.0;
		/** Where the crosshair lies in the grid's picture: off the middle when the view has turned since the grid was looked at. */
		double crossX;
		double crossY;
		/** Where the horizon lies up the picture. */
		double horizonY;
		/** Half the block's width, the height of one of its rows, and the rows it wants and needs at least. */
		double halfWidth;
		double rowHeight;
		int wantedRows = 1;
		int leastRows = 1;
		/** The sizes tried, largest first, as shares of the size asked for: one for a size the player chose, several for Auto. */
		float[] scales = {1f};
		/** Whether a block in the sky must have sky behind all of it (Auto): it never reaches over the ground or blocks. */
		boolean wholeInSky;
		/** What it keeps clear of (HUD panels, the hand, lines still standing): left, right, bottom, top each. */
		final double[][] taken = new double[32][4];
		int takenCount;
		/** The side of the last placement: -1 left, 1 right, 0 none yet. */
		int lastSide;
		/**
		 * Moving: where the way ahead lies in the picture, and how far away a block near the player has to stand so that the way made while its line
		 * is sung changes its size on the screen by less than the drift allowed, in blocks.
		 */
		boolean moving;
		double aheadX;
		double aheadY;
		double aheadBlocks;

		void take(double left, double right, double bottom, double top) {
			if (takenCount < taken.length) {
				double[] rect = taken[takenCount++];
				rect[0] = left;
				rect[1] = right;
				rect[2] = bottom;
				rect[3] = top;
			}
		}
	}

	/** The spot chosen. One, reused. */
	static final class Spot {
		Kind kind = Kind.NONE;
		/** Its middle in the grid's picture, how far away it stands, its rows, and how much smaller it is set (1 except as the last resort). */
		double x;
		double y;
		double distance;
		int rows;
		float scale = 1f;
		/** The quiet zone it was chosen with. */
		float quiet;
		/** The nearest surface in its area, in blocks. */
		double surface;
	}

	/** @return the quiet zone for a surface this far away at the crosshair, as a share of the screen's height */
	static float quietZone(double crosshairDepth) {
		double t = Math.log(Math.max(crosshairDepth, 1e-3) / QUIET_NEAR_BLOCKS) / Math.log(QUIET_FAR_BLOCKS / QUIET_NEAR_BLOCKS);
		return (float) (QUIET_MOST - (QUIET_MOST - QUIET_LEAST) * Math.clamp(t, 0.0, 1.0));
	}

	/** @return how near the nearest surface at and around the crosshair is, in blocks */
	static double crosshairDepth(float[] depth, int columns, int rows, double crossX, double crossY) {
		double nearest = Double.MAX_VALUE;
		for (int r = 0; r < rows; r++) {
			double y = 1.0 - (r + 0.5) * 2.0 / rows;
			if (Math.abs(y - crossY) > CROSSHAIR_UP) {
				continue;
			}
			for (int c = 0; c < columns; c++) {
				double x = -1.0 + (c + 0.5) * 2.0 / columns;
				if (Math.abs(x - crossX) <= CROSSHAIR_ACROSS) {
					nearest = Math.min(nearest, depth[r * columns + c]);
				}
			}
		}
		// off the grid (the view turned far since the look): the middle of what was seen
		return nearest == Double.MAX_VALUE ? depth[rows / 2 * columns + columns / 2] : nearest;
	}

	/** @return how far a block's nearest edge is from the crosshair, as a share of the screen's height */
	static double fromCrosshair(double x, double y, double halfWidth, double halfHeight, double crossX, double crossY, double aspect) {
		double across = Math.max(0.0, Math.abs(x - crossX) - halfWidth) * aspect / 2.0;
		double up = Math.max(0.0, Math.abs(y - crossY) - halfHeight) / 2.0;
		return Math.sqrt(across * across + up * up);
	}

	/**
	 * Chooses the block's spot.
	 *
	 * @return whether there is one; else nothing is drawn in the world (Sky without sky in view, or not even one row fits at the smallest size)
	 */
	static boolean choose(Ask ask, Spot out) {
		out.kind = Kind.NONE;
		out.scale = 1f;
		out.quiet = quietZone(crosshairDepth(ask.depth, ask.columns, ask.rows, ask.crossX, ask.crossY));
		int good = Math.max(ask.leastRows, Math.min(ask.wantedRows, GOOD_ROWS));
		// with room for a good block first, at each size tried (Auto: from about Normal down to Small), then with fewer rows
		for (int pass = 0; pass < 2; pass++) {
			int least = pass == 0 ? good : ask.leastRows;
			int most = pass == 0 ? ask.wantedRows : good - 1;
			for (float scale : ask.scales) {
				for (Kind kind : Kind.values()) {
					if (kind == Kind.NONE || !allowed(ask.mode, kind)) {
						continue;
					}
					for (int rows = most; rows >= least; rows--) {
						if (best(ask, kind, rows, scale, out)) {
							return true;
						}
					}
				}
			}
		}
		return allowed(ask.mode, Kind.WALL) && best(ask, Kind.WALL, ask.leastRows, LEAST_SCALE, out);
	}

	/** @return whether a mode takes a kind of spot: Auto all, Around the open spot and the wall, Sky the sky alone */
	static boolean allowed(Mode mode, Kind kind) {
		return switch (mode) {
			case AUTO -> kind != Kind.NONE;
			case AROUND -> kind == Kind.OPEN || kind == Kind.WALL;
			case SKY -> kind == Kind.SKY;
		};
	}

	/** Finds the best spot of a kind for a block of so many rows, on a lattice of half cells. */
	private static boolean best(Ask ask, Kind kind, int rows, float scale, Spot out) {
		double halfWidth = ask.halfWidth * scale;
		double halfHeight = rows * ask.rowHeight * scale / 2.0;
		double stepX = 1.0 / ask.columns;
		double stepY = 1.0 / ask.rows;
		double bestScore = -Double.MAX_VALUE;
		boolean found = false;
		int side = ask.lastSide != 0 ? ask.lastSide : deeperSide(ask);
		for (double y = 1.0 - EDGE - halfHeight; y >= -1.0 + EDGE + halfHeight - 1e-9; y -= stepY) {
			for (double x = -1.0 + EDGE + halfWidth; x <= 1.0 - EDGE - halfWidth + 1e-9; x += stepX) {
				double away = fromCrosshair(x, y, halfWidth, halfHeight, ask.crossX, ask.crossY, ask.aspect);
				if (away < out.quiet || overTaken(ask, x, y, halfWidth, halfHeight)) {
					continue;
				}
				double score = score(ask, kind, x, y, halfWidth, halfHeight, away - out.quiet, side);
				if (!Double.isNaN(score) && score > bestScore) {
					bestScore = score;
					found = true;
					out.kind = kind;
					out.x = x;
					out.y = y;
					out.rows = rows;
					out.scale = scale;
					out.distance = measuredDistance;
					out.surface = measuredSurface;
				}
			}
		}
		return found;
	}

	/** What {@link #score} found for the spot it last judged: how far the block stands there and the nearest surface. */
	private static double measuredDistance;
	private static double measuredSurface;

	/** @return how good a spot of a kind is, or NaN where the kind is not to be had there */
	private static double score(Ask ask, Kind kind, double x, double y, double halfWidth, double halfHeight, double beyondZone, int side) {
		double nearest = Double.MAX_VALUE;
		double sum = 0.0;
		double squares = 0.0;
		int solid = 0;
		int sky = 0;
		int count = 0;
		double halfCellX = 1.0 / ask.columns;
		double halfCellY = 1.0 / ask.rows;
		for (int r = 0; r < ask.rows; r++) {
			double cellY = 1.0 - (r + 0.5) * 2.0 / ask.rows;
			if (cellY - halfCellY >= y + halfHeight || cellY + halfCellY <= y - halfHeight) {
				continue;
			}
			for (int c = 0; c < ask.columns; c++) {
				double cellX = -1.0 + (c + 0.5) * 2.0 / ask.columns;
				if (cellX + halfCellX <= x - halfWidth || cellX - halfCellX >= x + halfWidth) {
					continue;
				}
				int cell = r * ask.columns + c;
				count++;
				nearest = Math.min(nearest, ask.depth[cell]);
				if (ask.sky[cell]) {
					sky++;
				} else {
					sum += ask.light[cell];
					squares += ask.light[cell] * ask.light[cell];
					solid++;
				}
			}
		}
		if (count == 0) {
			return Double.NaN;
		}
		double middle = depthAt(ask, x, y);
		double distance;
		switch (kind) {
			case SKY -> {
				if (nearest < DRAWN_OVER_BLOCKS || sky < (ask.wholeInSky ? count : SKY_SHARE * count)) {
					return Double.NaN;
				}
				distance = SKY_BLOCKS;
			}
			case OPEN -> {
				// what is nearer than may be drawn over sets the depth that is free; else the depth in the block's middle
				double free = nearest < DRAWN_OVER_BLOCKS ? nearest : Math.max(DRAWN_OVER_BLOCKS, middle);
				if (free * OPEN_SHARE < OPEN_LEAST_BLOCKS) {
					return Double.NaN;
				}
				// moving, it may stand farther than the usual most, as far as the sight is free: so it keeps its size while the line is sung
				double most = ask.moving ? Math.max(OPEN_MOST_BLOCKS, Math.min(SKY_BLOCKS, ask.aheadBlocks)) : OPEN_MOST_BLOCKS;
				distance = Math.min(most, free * OPEN_SHARE);
			}
			default -> {
				distance = Math.max(WALL_LEAST_BLOCKS, nearest - WALL_GAP_BLOCKS);
				if (ask.moving) {
					// walking along a tunnel or over a field looked down on, a block a few steps away is passed before its line is sung: it stands
					// farther along the way instead, drawn over the blocks between, where the walk hardly changes its size
					distance = Math.max(distance, Math.min(SKY_BLOCKS, ask.aheadBlocks));
				}
			}
		}
		measuredDistance = distance;
		measuredSurface = nearest;
		double mean = solid == 0 ? 0.0 : sum / solid;
		double busy = solid < 2 ? 0.0 : Math.sqrt(Math.max(0.0, squares / solid - mean * mean));
		double score = DEPTH_WEIGHT * Math.min(1.0, distance / OPEN_MOST_BLOCKS) - CALM_WEIGHT * busy + (kind == Kind.SKY ? sky / (double) count : 0.0);
		score += HORIZON_WEIGHT * Math.clamp((y - ask.horizonY) / HORIZON_REACH, -1.0, 1.0);
		if (kind == Kind.WALL) {
			// off to the side and above: the upper corner on the side with more room
			double cornerX = (side == 0 ? (x >= ask.crossX ? 1 : -1) : side) * CORNER_ACROSS;
			score -= CORNER_WEIGHT * Math.hypot((x - cornerX) * ask.aspect / 2.0, (y - CORNER_UP) / 2.0);
		} else {
			// just outside the quiet zone, not far out at the screen's edge
			score -= NEAR_ZONE_WEIGHT * beyondZone;
		}
		if (ask.moving && kind != Kind.SKY) {
			// ahead in the direction of travel, and far enough to stay in view, at its size, for the whole line
			score -= AHEAD_WEIGHT * Math.hypot((x - ask.aheadX) * ask.aspect / 2.0, (y - ask.aheadY) / 2.0);
			if (distance < ask.aheadBlocks) {
				score -= AHEAD_WEIGHT * (1.0 - distance / ask.aheadBlocks);
			}
		} else if (side != 0 && (x - ask.crossX) * side > 0.0) {
			score += SIDE_WEIGHT;
		}
		return score;
	}

	private static double depthAt(Ask ask, double x, double y) {
		int column = Math.clamp((int) Math.floor((x + 1.0) / 2.0 * ask.columns), 0, ask.columns - 1);
		int row = Math.clamp((int) Math.floor((1.0 - y) / 2.0 * ask.rows), 0, ask.rows - 1);
		return ask.depth[row * ask.columns + column];
	}

	/** @return the side of the view that reaches deeper on the whole: -1 left, 1 right */
	static int deeperSide(Ask ask) {
		double left = 0.0;
		double right = 0.0;
		for (int r = 0; r < ask.rows; r++) {
			for (int c = 0; c < ask.columns; c++) {
				double x = -1.0 + (c + 0.5) * 2.0 / ask.columns;
				double depth = Math.min(ask.depth[r * ask.columns + c], OPEN_MOST_BLOCKS);
				if (x < ask.crossX) {
					left += depth;
				} else {
					right += depth;
				}
			}
		}
		return right >= left ? 1 : -1;
	}

	private static boolean overTaken(Ask ask, double x, double y, double halfWidth, double halfHeight) {
		for (int i = 0; i < ask.takenCount; i++) {
			double[] rect = ask.taken[i];
			if (x - halfWidth < rect[1] && x + halfWidth > rect[0] && y - halfHeight < rect[3] && y + halfHeight > rect[2]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A config from before the three placements: its "Manual" becomes the place chosen with it ("Around you" is Around, "In the sky" is Sky);
	 * Auto stays Auto.
	 */
	static void keepOldPlacement(JsonObject json) {
		if (json.has(PLACEMENT_KEY) && json.get(PLACEMENT_KEY).isJsonPrimitive() && "MANUAL".equals(json.get(PLACEMENT_KEY).getAsString())) {
			boolean sky = json.has(PLACE_KEY) && json.get(PLACE_KEY).isJsonPrimitive() && "SKY".equals(json.get(PLACE_KEY).getAsString());
			json.addProperty(PLACEMENT_KEY, sky ? Mode.SKY.name() : Mode.AROUND.name());
		}
	}
}
