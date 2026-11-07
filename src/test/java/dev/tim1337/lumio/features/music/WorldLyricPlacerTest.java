package dev.tim1337.lumio.features.music;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A world lyric line only stands where its whole box is free and the eye sees it, in a block grid of the test's own: an open field, a wall in
 * front, a small room, a dense forest, a roof with the sky placement, and the Manual placement with its distance blocked. Every spot found is
 * checked again here, finer and independently of the placer's own sampling.
 */
class WorldLyricPlacerTest {
	/** The eye stands on a field whose top block is at y 64, looking along +z. */
	private static final int GROUND = 64;
	private static final double EYE_X = 0.5;
	/** Standing on the ground: the feet on its top, the eye 1.62 blocks above them. */
	private static final double EYE_Y = GROUND + 2.62;
	private static final double EYE_Z = 0.5;
	private static final float HALF_FOV_X = 55f;
	private static final float HALF_FOV_Y = 35f;
	/** A typical line: two rows of medium width with its glow, in degrees as seen from the eye. */
	private static final float HALF_WIDTH = 9f;
	private static final float HALF_HEIGHT = 5f;
	private static final float GLOW = 1.5f;
	private static final float SPREAD = 0.45f;
	/** The independent check samples finer than the placer, and allows a little less margin for the coarser sampling there. */
	private static final double CHECK_STEP = 0.1;
	private static final double CHECK_MARGIN = 0.2;

	/** The box of a full block, in a cell's own coordinates. */
	private static final double[] FULL = {0, 0, 0, 1, 1, 1};
	/** Short grass: low, and a little narrower than its cell. */
	private static final double[] GRASS = {0.1, 0, 0.1, 0.9, 0.8, 0.9};
	/** A fence post: thin, and taller than a block. */
	private static final double[] FENCE_POST = {0.375, 0, 0.375, 0.625, 1.5, 0.625};

	/** A grid of full blocks on a ground, with some partial ones. */
	private static final class Grid implements WorldLyricBlocks {
		private final Set<Long> cells = new HashSet<>();
		private final Map<Long, double[]> partial = new HashMap<>();

		void partial(int x, int y, int z, double[] shape) {
			partial.put(key(x, y, z), shape);
		}

		void set(int x, int y, int z) {
			cells.add(key(x, y, z));
		}

		void fill(int x0, int y0, int z0, int x1, int y1, int z1) {
			for (int x = x0; x <= x1; x++) {
				for (int y = y0; y <= y1; y++) {
					for (int z = z0; z <= z1; z++) {
						set(x, y, z);
					}
				}
			}
		}

		@Override
		public boolean shape(int x, int y, int z, double[] box) {
			double[] shape = y <= GROUND || cells.contains(key(x, y, z)) ? FULL : partial.get(key(x, y, z));
			if (shape == null) {
				return false;
			}
			System.arraycopy(shape, 0, box, 0, BOX_SIZE);
			return true;
		}

		/** @return whether a point is inside a block */
		boolean inside(double x, double y, double z) {
			double[] box = new double[BOX_SIZE];
			int cellX = floor(x);
			int cellY = floor(y);
			int cellZ = floor(z);
			if (!shape(cellX, cellY, cellZ, box)) {
				return false;
			}
			double localX = x - cellX;
			double localY = y - cellY;
			double localZ = z - cellZ;
			return localX >= box[0] && localX <= box[3] && localY >= box[1] && localY <= box[4] && localZ >= box[2] && localZ <= box[5];
		}

		private static long key(int x, int y, int z) {
			return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | (long) z & 0xFFFFF;
		}
	}

	private static WorldLyricPlacer.Spot place(Grid grid, long seed, boolean sky, boolean manual) {
		WorldLyricPlacer placer = new WorldLyricPlacer(grid, new Random(seed));
		WorldLyricPlacer.Spot spot = new WorldLyricPlacer.Spot();
		placer.place(EYE_X, EYE_Y, EYE_Z, 0f, 0f, HALF_FOV_X, HALF_FOV_Y, sky, manual, HALF_WIDTH, HALF_HEIGHT, GLOW, SPREAD, 1f, spot);
		return spot;
	}

	/** Checks a found spot: every point of its box with the margin, front and back, is outside solid cells, and the eye sees its middle. */
	private static void assertFree(Grid grid, WorldLyricPlacer.Spot spot) {
		double dx = spot.x - EYE_X;
		double dy = spot.y - EYE_Y;
		double dz = spot.z - EYE_Z;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		assertEquals(spot.distance, distance, 1e-6);
		double fx = dx / distance;
		double fy = dy / distance;
		double fz = dz / distance;
		double horizontal = Math.sqrt(fx * fx + fz * fz);
		double rx = fz / horizontal;
		double rz = -fx / horizontal;
		double ux = -fy * rz;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double halfAcross = distance * Math.tan(Math.toRadians((HALF_WIDTH + GLOW) * spot.shrink)) + CHECK_MARGIN;
		double halfUp = distance * Math.tan(Math.toRadians((HALF_HEIGHT + GLOW) * spot.shrink)) + CHECK_MARGIN;
		for (double depth = -CHECK_MARGIN; depth <= CHECK_MARGIN + 1e-9; depth += CHECK_STEP) {
			for (double side = -halfAcross; side <= halfAcross + 1e-9; side += CHECK_STEP) {
				for (double up = -halfUp; up <= halfUp + 1e-9; up += CHECK_STEP) {
					double x = spot.x + fx * depth + rx * side + ux * up;
					double y = spot.y + fy * depth + uy * up;
					double z = spot.z + fz * depth + rz * side + uz * up;
					assertFalse(grid.inside(x, y, z), "the line's box reaches into a block at " + x + " " + y + " " + z);
				}
			}
		}
		for (double t = 0; t < distance; t += CHECK_STEP / 2) {
			assertFalse(grid.inside(EYE_X + fx * t, EYE_Y + fy * t, EYE_Z + fz * t), "the line is hidden behind a block");
		}
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}

	@Test
	void anOpenFieldGetsASpotAtTheUsualDistance() {
		Grid grid = new Grid();
		for (long seed = 0; seed < 10; seed++) {
			WorldLyricPlacer.Spot spot = place(grid, seed, false, false);
			assertTrue(spot.open);
			assertEquals(1f, spot.shrink);
			assertTrue(spot.distance >= 5.0 * 0.7, "at the usual distance: " + spot.distance);
			assertFree(grid, spot);
		}
	}

	@Test
	void grassOnTheGroundCountsByItsShape() {
		Grid grid = new Grid();
		for (int x = -30; x <= 30; x++) {
			for (int z = -30; z <= 30; z++) {
				grid.partial(x, GROUND + 1, z, GRASS);
			}
		}
		for (long seed = 0; seed < 10; seed++) {
			WorldLyricPlacer.Spot spot = place(grid, seed, false, false);
			assertTrue(spot.open);
			assertEquals(1f, spot.shrink);
			assertFree(grid, spot);
		}
	}

	@Test
	void aThinFenceStillBlocks() {
		Grid grid = new Grid();
		// fence posts all around at four blocks, one block apart, two posts high: thin, but a line must not stand in them
		for (int i = -4; i <= 4; i++) {
			for (int y = GROUND + 1; y <= GROUND + 2; y++) {
				grid.partial(i, y, 4, FENCE_POST);
				grid.partial(i, y, -4, FENCE_POST);
				grid.partial(4, y, i, FENCE_POST);
				grid.partial(-4, y, i, FENCE_POST);
			}
		}
		for (long seed = 0; seed < 10; seed++) {
			WorldLyricPlacer.Spot spot = place(grid, seed, false, false);
			if (spot.open) {
				assertFree(grid, spot);
			}
		}
	}

	@Test
	void aWallRightInFrontIsNeverCrossed() {
		Grid grid = new Grid();
		grid.fill(-40, GROUND + 1, 3, 40, GROUND + 12, 3);
		for (long seed = 0; seed < 10; seed++) {
			WorldLyricPlacer.Spot spot = place(grid, seed, false, false);
			if (spot.open) {
				assertTrue(spot.z < 3.0, "in front of the wall: " + spot.z);
				assertFree(grid, spot);
			}
		}
	}

	@Test
	void aShortWallInFrontSendsTheLineAroundIt() {
		Grid grid = new Grid();
		grid.fill(-1, GROUND + 1, 3, 1, GROUND + 12, 3);
		for (long seed = 0; seed < 10; seed++) {
			WorldLyricPlacer.Spot spot = place(grid, seed, false, false);
			assertTrue(spot.open);
			assertFree(grid, spot);
		}
	}

	@Test
	void aSmallRoomHasNoRoomForALine() {
		// a 3×3 room around the player, two blocks high, with walls, a floor and a ceiling
		Grid room = new Grid();
		room.fill(-2, GROUND + 1, -2, 2, GROUND + 2, -2);
		room.fill(-2, GROUND + 1, 2, 2, GROUND + 2, 2);
		room.fill(-2, GROUND + 1, -2, -2, GROUND + 2, 2);
		room.fill(2, GROUND + 1, -2, 2, GROUND + 2, 2);
		room.fill(-2, GROUND + 3, -2, 2, GROUND + 3, 2);
		for (long seed = 0; seed < 5; seed++) {
			assertFalse(place(room, seed, false, false).open);
			assertFalse(place(room, seed, true, false).open);
		}
	}

	@Test
	void aDenseForestOnlyGetsFreeSpots() {
		for (long seed = 0; seed < 20; seed++) {
			Grid grid = new Grid();
			Random trees = new Random(seed);
			for (int x = -24; x <= 24; x += 3) {
				for (int z = -24; z <= 24; z += 3) {
					if (x == 0 && z == 0 || trees.nextFloat() < 0.25f) {
						continue;
					}
					int treeX = x + trees.nextInt(2);
					int treeZ = z + trees.nextInt(2);
					grid.fill(treeX, GROUND + 1, treeZ, treeX, GROUND + 5, treeZ);
					grid.fill(treeX - 1, GROUND + 4, treeZ - 1, treeX + 1, GROUND + 6, treeZ + 1);
				}
			}
			WorldLyricPlacer.Spot spot = place(grid, seed, false, false);
			if (spot.open) {
				assertFree(grid, spot);
			}
		}
	}

	@Test
	void theSkyUnderARoofIsCheckedForBlocks() {
		Grid open = new Grid();
		for (long seed = 0; seed < 5; seed++) {
			WorldLyricPlacer.Spot spot = place(open, seed, true, false);
			assertTrue(spot.open, "an open sky has room");
			assertTrue(spot.y > EYE_Y + 10.0, "up in the sky: " + spot.y);
			assertFree(open, spot);
		}
		Grid small = new Grid();
		small.fill(-5, GROUND + 4, -5, 5, GROUND + 4, 5);
		for (long seed = 0; seed < 10; seed++) {
			WorldLyricPlacer.Spot spot = place(small, seed, true, false);
			if (spot.open) {
				assertTrue(spot.y > EYE_Y, "in the sky: " + spot.y);
				assertFree(small, spot);
			}
		}
		Grid wide = new Grid();
		wide.fill(-80, GROUND + 4, -80, 80, GROUND + 4, 80);
		for (long seed = 0; seed < 5; seed++) {
			assertFalse(place(wide, seed, true, false).open, "no sky under a wide roof");
		}
	}

	@Test
	void manualComesCloserInItsOwnDirection() {
		Grid grid = new Grid();
		// a wall at five blocks: the chosen distance of five to ten blocks is blocked
		grid.fill(-40, GROUND + 1, 5, 40, GROUND + 12, 5);
		for (long seed = 0; seed < 10; seed++) {
			WorldLyricPlacer placer = new WorldLyricPlacer(grid, new Random(seed));
			WorldLyricPlacer.Spot spot = new WorldLyricPlacer.Spot();
			placer.place(EYE_X, EYE_Y, EYE_Z, 0f, 0f, HALF_FOV_X, HALF_FOV_Y, false, true, HALF_WIDTH, HALF_HEIGHT, GLOW, SPREAD, 1f, spot);
			assertTrue(spot.open);
			assertTrue(spot.distance < 4.5, "closer than the wall: " + spot.distance);
			assertTrue(spot.distance >= 2.5);
			// the first side (left, looking along +z the left is +x) in its own direction
			assertEquals(WorldLyricPlacer.LEFT, placer.lastKind());
			assertTrue(spot.x > EYE_X, "on the left: " + spot.x);
			assertFree(grid, spot);
		}
	}
}
