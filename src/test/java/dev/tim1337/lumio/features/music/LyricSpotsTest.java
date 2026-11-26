package dev.tim1337.lumio.features.music;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a block of the lyrics in the world goes, by the view alone: the order sky, open spot, wall; no sky or open spot where the sight is blocked
 * nearer than the block; the quiet zone by how near the surface at the crosshair is, and nothing ever placed inside it; the fade when it gets
 * covered, the new placing when its size drifts, and the modes changing only at a line's start.
 */
class LyricSpotsTest {
	private static final int COLUMNS = WorldLyricOpenSky.COLUMNS;
	private static final int ROWS = WorldLyricOpenSky.ROWS;
	private static final float FAR = 128f;

	/** @return a view: sky in the rows above the horizon row, ground below it at a depth that comes nearer toward the feet */
	private static LyricSpots.Ask view(LyricSpots.Mode mode) {
		LyricSpots.Ask ask = new LyricSpots.Ask();
		ask.mode = mode;
		ask.columns = COLUMNS;
		ask.rows = ROWS;
		ask.depth = new float[COLUMNS * ROWS];
		ask.sky = new boolean[COLUMNS * ROWS];
		ask.light = new float[COLUMNS * ROWS];
		ask.halfWidth = 0.24;
		ask.rowHeight = 0.13;
		ask.wantedRows = 5;
		ask.leastRows = 1;
		Arrays.fill(ask.light, 0.3f);
		return ask;
	}

	private static void fill(LyricSpots.Ask ask, int fromRow, int toRow, int fromColumn, int toColumn, float depth, boolean sky) {
		for (int r = fromRow; r <= toRow; r++) {
			for (int c = fromColumn; c <= toColumn; c++) {
				ask.depth[r * COLUMNS + c] = depth;
				ask.sky[r * COLUMNS + c] = sky;
			}
		}
	}

	/** Open sky above the horizon, ground below. */
	private static LyricSpots.Ask outdoors(LyricSpots.Mode mode) {
		LyricSpots.Ask ask = view(mode);
		fill(ask, 0, 4, 0, COLUMNS - 1, FAR, true);
		for (int r = 5; r < ROWS; r++) {
			fill(ask, r, r, 0, COLUMNS - 1, 40f / (r - 4), false);
		}
		return ask;
	}

	/** A hall: walls all around at a depth, nothing of the sky. */
	private static LyricSpots.Ask room(LyricSpots.Mode mode, float depth) {
		LyricSpots.Ask ask = view(mode);
		fill(ask, 0, ROWS - 1, 0, COLUMNS - 1, depth, false);
		return ask;
	}

	private static double nearestUnder(LyricSpots.Ask ask, LyricSpots.Spot spot) {
		double halfHeight = spot.rows * ask.rowHeight * spot.scale / 2.0;
		double halfWidth = ask.halfWidth * spot.scale;
		double nearest = Double.MAX_VALUE;
		for (int r = 0; r < ROWS; r++) {
			double y = WorldLyricOpenSky.screenY(r);
			for (int c = 0; c < COLUMNS; c++) {
				double x = WorldLyricOpenSky.screenX(c);
				if (Math.abs(x - spot.x) < halfWidth + 1.0 / COLUMNS && Math.abs(y - spot.y) < halfHeight + 1.0 / ROWS) {
					nearest = Math.min(nearest, ask.depth[r * COLUMNS + c]);
				}
			}
		}
		return nearest;
	}

	private static double fromCrosshair(LyricSpots.Ask ask, LyricSpots.Spot spot) {
		return LyricSpots.fromCrosshair(spot.x, spot.y, ask.halfWidth * spot.scale, spot.rows * ask.rowHeight * spot.scale / 2.0, ask.crossX, ask.crossY, ask.aspect);
	}

	@Test
	void theOrderIsSkyThenAnOpenSpotThenTheWall() {
		LyricSpots.Spot spot = new LyricSpots.Spot();
		assertTrue(LyricSpots.choose(outdoors(LyricSpots.Mode.AUTO), spot));
		assertEquals(LyricSpots.Kind.SKY, spot.kind);
		assertEquals(LyricSpots.SKY_BLOCKS, spot.distance, 1e-9, "one distance in the sky, whatever the render distance");

		assertTrue(LyricSpots.choose(room(LyricSpots.Mode.AUTO, 30f), spot));
		assertEquals(LyricSpots.Kind.OPEN, spot.kind, "a hall: no sky, but an open spot");
		assertEquals(30.0 * LyricSpots.OPEN_SHARE, spot.distance, 1e-6, "the free depth less 15 %");

		assertTrue(LyricSpots.choose(room(LyricSpots.Mode.AUTO, 5f), spot));
		assertEquals(LyricSpots.Kind.WALL, spot.kind, "only blocks in front: still shown");
		assertEquals(5.0 - LyricSpots.WALL_GAP_BLOCKS, spot.distance, 1e-6, "a block before the surface");
		assertTrue(spot.y > 0.0 && Math.abs(spot.x) > 0.2, "off to the side and above, not at the crosshair: " + spot.x + " " + spot.y);

		assertTrue(LyricSpots.choose(room(LyricSpots.Mode.AUTO, 1.5f), spot));
		assertEquals(LyricSpots.WALL_LEAST_BLOCKS, spot.distance, 1e-9, "never nearer than three blocks");
	}

	@Test
	void theModesTakeOnlyTheirKinds() {
		LyricSpots.Spot spot = new LyricSpots.Spot();
		assertTrue(LyricSpots.choose(outdoors(LyricSpots.Mode.AROUND), spot));
		assertEquals(LyricSpots.Kind.OPEN, spot.kind, "Around stands near also outdoors");
		assertTrue(spot.distance >= LyricSpots.OPEN_LEAST_BLOCKS && spot.distance <= LyricSpots.OPEN_MOST_BLOCKS, "between 8 and 48 blocks: " + spot.distance);

		assertTrue(LyricSpots.choose(outdoors(LyricSpots.Mode.SKY), spot));
		assertEquals(LyricSpots.Kind.SKY, spot.kind);
		assertFalse(LyricSpots.choose(room(LyricSpots.Mode.SKY, 30f), spot), "Sky without sky in view: nothing in the world");
		assertEquals(LyricSpots.Kind.NONE, spot.kind);
		assertTrue(LyricSpots.choose(room(LyricSpots.Mode.AROUND, 4f), spot));
		assertEquals(LyricSpots.Kind.WALL, spot.kind);
	}

	@Test
	void noSkyOrOpenSpotWhereTheSightIsBlockedNearerThanTheBlock() {
		LyricSpots.Spot spot = new LyricSpots.Spot();
		for (int seed = 0; seed < 200; seed++) {
			java.util.Random random = new java.util.Random(seed);
			LyricSpots.Ask ask = outdoors(seed % 2 == 0 ? LyricSpots.Mode.AUTO : LyricSpots.Mode.AROUND);
			// trees and walls here and there, at any depth
			for (int k = 0; k < 6; k++) {
				int c = random.nextInt(COLUMNS - 2);
				int r = random.nextInt(ROWS - 2);
				fill(ask, r, r + random.nextInt(3), c, c + random.nextInt(3), 2f + random.nextFloat() * 60f, false);
			}
			if (!LyricSpots.choose(ask, spot) || spot.kind == LyricSpots.Kind.WALL) {
				continue;
			}
			double nearest = nearestUnder(ask, spot);
			// far scenery may be drawn over; anything nearer than that must lie behind the block
			assertTrue(nearest >= Math.min(spot.distance, LyricSpots.DRAWN_OVER_BLOCKS) - 1e-6, "seed " + seed + ": " + spot.kind + " at " + spot.distance + " with a block at " + nearest);
		}
	}

	@Test
	void theQuietZoneGrowsAsTheSurfaceInFrontComesNear() {
		assertEquals(LyricSpots.QUIET_LEAST, LyricSpots.quietZone(FAR), 1e-6f, "an open view: small");
		assertEquals(LyricSpots.QUIET_LEAST, LyricSpots.quietZone(LyricSpots.QUIET_FAR_BLOCKS), 1e-6f);
		assertEquals(LyricSpots.QUIET_MOST, LyricSpots.quietZone(1.0), 1e-6f, "at a wall: large");
		float before = LyricSpots.QUIET_MOST;
		for (double depth = 2.0; depth <= 64.0; depth *= 1.3) {
			float zone = LyricSpots.quietZone(depth);
			assertTrue(zone <= before + 1e-6f, "never larger farther away: " + depth);
			before = zone;
		}
		assertEquals(0.18f, LyricSpots.quietZone(10.0), 0.03f, "at ten blocks about in between");
		// the surface at and around the crosshair counts, not the ground at the feet
		LyricSpots.Ask plain = outdoors(LyricSpots.Mode.AUTO);
		assertEquals(FAR, LyricSpots.crosshairDepth(plain.depth, COLUMNS, ROWS, 0.0, 0.0), 1e-6);
		fill(plain, 3, 5, 7, 8, 3f, false);
		assertEquals(3.0, LyricSpots.crosshairDepth(plain.depth, COLUMNS, ROWS, 0.0, 0.0), 1e-6, "a block right at the crosshair");
	}

	@Test
	void nothingIsEverPlacedInsideTheQuietZone() {
		LyricSpots.Spot spot = new LyricSpots.Spot();
		for (int seed = 0; seed < 300; seed++) {
			java.util.Random random = new java.util.Random(seed);
			LyricSpots.Ask ask = seed % 3 == 0 ? room(LyricSpots.Mode.AUTO, 1.5f + random.nextFloat() * 40f) : outdoors(LyricSpots.Mode.values()[seed % 3]);
			for (int k = 0; k < 4; k++) {
				int c = random.nextInt(COLUMNS - 3);
				int r = random.nextInt(ROWS - 3);
				fill(ask, r, r + random.nextInt(4), c, c + random.nextInt(4), 1.5f + random.nextFloat() * 30f, false);
			}
			ask.crossX = (random.nextDouble() - 0.5) * 0.3;
			ask.crossY = (random.nextDouble() - 0.5) * 0.3;
			ask.take(-1.0, -0.4, -1.0, -0.5);
			if (LyricSpots.choose(ask, spot)) {
				assertTrue(fromCrosshair(ask, spot) >= spot.quiet - 1e-9, "seed " + seed + ": " + fromCrosshair(ask, spot) + " inside " + spot.quiet);
				assertEquals(LyricSpots.quietZone(LyricSpots.crosshairDepth(ask.depth, COLUMNS, ROWS, ask.crossX, ask.crossY)), spot.quiet, 1e-6f);
				double halfHeight = spot.rows * ask.rowHeight * spot.scale / 2.0;
				assertFalse(spot.x - ask.halfWidth * spot.scale < -0.4 && spot.y - halfHeight < -0.5, "clear of what is taken (a panel, the hand)");
			}
		}
	}

	@Test
	void aTightViewShowsFewerRowsRatherThanMoveIntoTheZone() {
		// at a wall the zone is large: beside it there is room for a few rows only
		LyricSpots.Ask ask = room(LyricSpots.Mode.AUTO, 2f);
		ask.rowHeight = 0.2;
		ask.wantedRows = 6;
		LyricSpots.Spot spot = new LyricSpots.Spot();
		assertTrue(LyricSpots.choose(ask, spot));
		assertEquals(LyricSpots.QUIET_MOST, spot.quiet, 1e-6f);
		assertTrue(spot.rows < 6 || fromCrosshair(ask, spot) >= spot.quiet, "fewer rows or outside the zone");
		assertTrue(fromCrosshair(ask, spot) >= spot.quiet - 1e-9);
		// no room for a single row: nothing is shown
		ask.halfWidth = 0.9;
		ask.rowHeight = 1.5;
		assertFalse(LyricSpots.choose(ask, spot));
	}

	@Test
	void theSameViewGivesTheSameSpot() {
		LyricSpots.Ask ask = outdoors(LyricSpots.Mode.AUTO);
		LyricSpots.Spot first = new LyricSpots.Spot();
		LyricSpots.Spot again = new LyricSpots.Spot();
		assertTrue(LyricSpots.choose(ask, first));
		assertTrue(LyricSpots.choose(ask, again));
		assertEquals(first.x, again.x, 1e-9, "the same view gives the same spot");
		assertEquals(first.y, again.y, 1e-9);
	}

	@Test
	void theSideStaysForTheSongAndFastMovementPlacesAhead() {
		LyricSpots.Spot spot = new LyricSpots.Spot();
		for (int side = -1; side <= 1; side += 2) {
			LyricSpots.Ask ask = outdoors(LyricSpots.Mode.AUTO);
			ask.lastSide = side;
			assertTrue(LyricSpots.choose(ask, spot));
			assertTrue(spot.x * side > 0.0, "on the side of the last placement: " + side);
		}
		// a tunnel: deep ahead on the left, a wall on the right; moving fast, the block goes far ahead
		LyricSpots.Ask tunnel = room(LyricSpots.Mode.AROUND, 6f);
		fill(tunnel, 1, 5, 2, 6, 40f, false);
		tunnel.moving = true;
		tunnel.aheadX = -0.5;
		tunnel.aheadY = 0.2;
		tunnel.aheadBlocks = 30.0;
		tunnel.wantedRows = 3;
		assertTrue(LyricSpots.choose(tunnel, spot));
		assertEquals(LyricSpots.Kind.OPEN, spot.kind);
		assertTrue(spot.x < 0.0 && spot.distance >= 30.0, "ahead and far: " + spot.x + ", " + spot.distance);
		// walking along a narrow tunnel, only walls in the block's area: it stands far along the way, where the walk hardly changes its size,
		// rather than three blocks ahead, where it would be passed before its line is sung
		LyricSpots.Ask narrow = room(LyricSpots.Mode.AUTO, 2.5f);
		assertTrue(LyricSpots.choose(narrow, spot));
		assertEquals(LyricSpots.WALL_LEAST_BLOCKS, spot.distance, 1e-9, "standing: before the wall");
		narrow.moving = true;
		narrow.aheadBlocks = 72.0;
		assertTrue(LyricSpots.choose(narrow, spot));
		assertEquals(LyricSpots.Kind.WALL, spot.kind);
		assertEquals(72.0, spot.distance, 1e-9, "moving: six times the way of the line away");
	}

	@Test
	void aSavedManualPlacementBecomesThePlaceItHad() {
		JsonObject near = new JsonObject();
		near.addProperty("world_placement", "MANUAL");
		near.addProperty("world_place", "NEAR");
		LyricSpots.keepOldPlacement(near);
		assertEquals("AROUND", near.get("world_placement").getAsString());
		JsonObject sky = new JsonObject();
		sky.addProperty("world_placement", "MANUAL");
		sky.addProperty("world_place", "SKY");
		LyricSpots.keepOldPlacement(sky);
		assertEquals("SKY", sky.get("world_placement").getAsString());
		JsonObject manualAlone = new JsonObject();
		manualAlone.addProperty("world_placement", "MANUAL");
		LyricSpots.keepOldPlacement(manualAlone);
		assertEquals("AROUND", manualAlone.get("world_placement").getAsString(), "the place's default was Around you");
		JsonObject auto = new JsonObject();
		auto.addProperty("world_placement", "AUTO");
		LyricSpots.keepOldPlacement(auto);
		assertEquals("AUTO", auto.get("world_placement").getAsString());
	}

	@Test
	void autoTriesFromNormalDownToSmallNeverAboveOrUnder() {
		assertTrue(Arrays.equals(new float[] {1f, 0.925f, 0.85f}, LyricReach.autoScales(2, false)), "a short line starts at Normal");
		assertTrue(Arrays.equals(new float[] {0.925f, 0.85f}, LyricReach.autoScales(6, false)), "a line a step smaller");
		assertTrue(Arrays.equals(new float[] {0.85f}, LyricReach.autoScales(12, false)), "a long line two steps");
		assertTrue(Arrays.equals(new float[] {0.85f}, LyricReach.autoScales(4, true)), "a full block two steps");
		for (int words = 0; words < 20; words++) {
			for (float scale : LyricReach.autoScales(words, words % 2 == 0)) {
				assertTrue(scale <= 1f && scale >= LyricReach.Size.SMALL.factor, words + ": " + scale);
			}
		}
	}

	/** @return a view with sky only in the top rows, over a band of columns, and ground everywhere else */
	private static LyricSpots.Ask skyWindow(int skyRows, int fromColumn, int toColumn) {
		LyricSpots.Ask ask = view(LyricSpots.Mode.SKY);
		fill(ask, 0, ROWS - 1, 0, COLUMNS - 1, 40f, false);
		fill(ask, 0, skyRows - 1, fromColumn, toColumn, FAR, true);
		ask.wantedRows = 3;
		ask.scales = LyricReach.autoScales(2, false);
		ask.wholeInSky = true;
		return ask;
	}

	/** @return whether every cell the block lies over shows the sky */
	private static boolean allSky(LyricSpots.Ask ask, LyricSpots.Spot spot) {
		double halfWidth = ask.halfWidth * spot.scale;
		double halfHeight = spot.rows * ask.rowHeight * spot.scale / 2.0;
		for (int r = 0; r < ROWS; r++) {
			double y = 1.0 - (r + 0.5) * 2.0 / ROWS;
			for (int c = 0; c < COLUMNS; c++) {
				double x = -1.0 + (c + 0.5) * 2.0 / COLUMNS;
				boolean under = Math.abs(x - spot.x) < halfWidth + 1.0 / COLUMNS && Math.abs(y - spot.y) < halfHeight + 1.0 / ROWS;
				if (under && !ask.sky[r * COLUMNS + c]) {
					return false;
				}
			}
		}
		return true;
	}

	@Test
	void autoUsesNormalWithMuchOpenSky() {
		LyricSpots.Ask ask = outdoors(LyricSpots.Mode.SKY);
		ask.wantedRows = 3;
		ask.scales = LyricReach.autoScales(2, false);
		ask.wholeInSky = true;
		LyricSpots.Spot spot = new LyricSpots.Spot();
		assertTrue(LyricSpots.choose(ask, spot));
		assertEquals(1f, spot.scale, "a lot of open sky: Normal");
		assertEquals(3, spot.rows);
		assertTrue(allSky(ask, spot), "all of it over the sky");
	}

	@Test
	void autoGoesSmallerInLessRoomNeverUnderSmallAndStaysInItsFreeArea() {
		LyricSpots.Ask ask = skyWindow(2, 4, 11);
		ask.rowHeight = 0.135;
		LyricSpots.Spot spot = new LyricSpots.Spot();
		assertTrue(LyricSpots.choose(ask, spot));
		assertEquals(3, spot.rows, "its rows kept");
		assertTrue(spot.scale < 1f && spot.scale >= LyricReach.Size.SMALL.factor, "smaller, not under Small: " + spot.scale);
		assertTrue(allSky(ask, spot), "never over the ground or blocks");
		// with even less room: fewer rows, never under Small
		LyricSpots.Ask tight = skyWindow(1, 4, 11);
		tight.rowHeight = 0.135;
		LyricSpots.Spot one = new LyricSpots.Spot();
		assertTrue(LyricSpots.choose(tight, one));
		assertTrue(one.rows < 3 && one.scale >= LyricReach.Size.SMALL.factor, one.rows + " rows at " + one.scale);
		assertTrue(allSky(tight, one));
	}

	@Test
	void anOldNormalBecomesAutoOnceAndAPickedSizeStays() {
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty(LyricReach.SIZE_KEY, "NORMAL");
		LyricReach.offerAuto(old);
		assertEquals("AUTO", old.get(LyricReach.SIZE_KEY).getAsString(), "untouched: Auto");
		com.google.gson.JsonObject small = new com.google.gson.JsonObject();
		small.addProperty(LyricReach.SIZE_KEY, "SMALL");
		LyricReach.offerAuto(small);
		assertEquals("SMALL", small.get(LyricReach.SIZE_KEY).getAsString(), "a size picked stays");
		com.google.gson.JsonObject later = new com.google.gson.JsonObject();
		later.addProperty(LyricReach.SIZE_KEY, "NORMAL");
		later.addProperty(LyricReach.AUTO_OFFERED_KEY, true);
		LyricReach.offerAuto(later);
		assertEquals("NORMAL", later.get(LyricReach.SIZE_KEY).getAsString(), "Normal picked after Auto came stays");
	}
}
