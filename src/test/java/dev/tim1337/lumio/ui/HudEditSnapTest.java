package dev.tim1337.lumio.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HUD editor's snapping: the screen's center lines and edges, the grid, Shift, and the other elements only when asked for. */
class HudEditSnapTest {
	private static final int W = 960;
	private static final int H = 540;
	private static final int GRID = 8;

	private static HudEditSnap snap(HudSnapMode mode) {
		HudEditSnap snap = new HudEditSnap();
		snap.begin(W, H, mode, GRID);
		return snap;
	}

	@Test
	void anElementsCenterSnapsToTheScreensCenterLine() {
		HudEditSnap snap = snap(HudSnapMode.CENTER_GRID);
		// 100 wide, its center at 479: 1 from the center line at 480 (a grid line too: on a tie the center line wins)
		assertTrue(snap.snap(429, 101, 100, 20, false, W, H));
		assertEquals(430, snap.snapped[0]);
		assertTrue(snap.centered(0));
		assertEquals(HudEditSnap.CENTER, snap.heldKind(0));
		assertEquals(480, snap.heldAt(0));
	}

	@Test
	void anEdgeSnapsToTheCenterLineToo() {
		HudEditSnap snap = snap(HudSnapMode.CENTER_GRID);
		// its left edge 1 from the center line, its center and right edge no nearer to a grid line
		snap.snap(481, 101, 61, 20, false, W, H);
		assertEquals(480, snap.snapped[0]);
		assertEquals(HudEditSnap.CENTER, snap.heldKind(0));
		assertFalse(snap.centered(0));
	}

	@Test
	void cornersSnapToGridPoints() {
		HudEditSnap snap = snap(HudSnapMode.CENTER_GRID);
		// the top left 1 off the grid point (200, 104) on both axes, far from the center lines and the edges
		snap.snap(201, 103, 50, 30, false, W, H);
		assertEquals(200, snap.snapped[0]);
		assertEquals(104, snap.snapped[1]);
		assertTrue(snap.onGridPoint());
	}

	@Test
	void theEdgesSnapWithTheMargin() {
		HudEditSnap snap = snap(HudSnapMode.CENTER_GRID);
		// the right edge 1 from the margin line at 952 (a grid line too: the edge wins the tie), its left and center no nearer to the grid
		snap.snap(902, 300, 51, 20, false, W, H);
		assertEquals(W - HudEditSnap.MARGIN - 51, snap.snapped[0]);
		assertEquals(HudEditSnap.EDGE, snap.heldKind(0));
		assertEquals("the right edge", snap.describe(0));
	}

	@Test
	void theSnapDistanceIsSmall() {
		HudEditSnap snap = snap(HudSnapMode.CENTER_GRID);
		// its center 5 GUI units from the center line (10 pixels at GUI scale 2): the center line does not take it (the grid does)
		assertFalse(snap.snap(423, 101, 105, 20, false, W, H) && snap.heldKind(0) == HudEditSnap.CENTER);
	}

	@Test
	void gridOnlyHasNoCenterLinesOrEdges() {
		HudEditSnap snap = snap(HudSnapMode.GRID_ONLY);
		snap.snap(429, 101, 100, 20, false, W, H);
		assertEquals(HudEditSnap.GRID, snap.heldKind(0));
		assertFalse(snap.centered(0));
	}

	@Test
	void offAndShiftSnapToNothing() {
		HudEditSnap off = snap(HudSnapMode.OFF);
		assertFalse(off.snap(429, 101, 100, 20, false, W, H));
		assertEquals(429, off.snapped[0]);
		HudEditSnap shift = snap(HudSnapMode.CENTER_GRID);
		assertFalse(shift.snap(429, 101, 100, 20, true, W, H));
		assertEquals(429, shift.snapped[0]);
		assertEquals(101, shift.snapped[1]);
	}

	@Test
	void otherElementsAreNoTargetUnlessAskedFor() {
		int[] other = {300, 200, 80, 40};
		HudEditSnap without = snap(HudSnapMode.OFF);
		// its left edge 2 from the other element's right edge plus the gap (384), level with it
		assertFalse(without.snap(386, 210, 50, 20, false, W, H));
		HudEditSnap with = snap(HudSnapMode.OFF);
		with.addBox(other, "Scoreboard", null);
		assertTrue(with.snap(386, 210, 50, 20, false, W, H));
		assertEquals(300 + 80 + HudEditSnap.GAP, with.snapped[0]);
		assertEquals(HudEditSnap.ELEMENT, with.heldKind(0));
		assertEquals("Scoreboard", with.describe(0));
	}

	@Test
	void aSnapHoldsUntilPulledFurther() {
		HudEditSnap snap = snap(HudSnapMode.CENTER_GRID);
		snap.snap(429, 101, 100, 20, false, W, H);
		assertTrue(snap.centered(0));
		// 5 away: beyond the snap distance, within the hold
		snap.snap(425, 101, 100, 20, false, W, H);
		assertTrue(snap.centered(0));
		assertEquals(430, snap.snapped[0]);
		// 8 away: let go
		snap.snap(422, 101, 100, 20, false, W, H);
		assertFalse(snap.centered(0));
	}

	@Test
	void resizingSnapsThePulledEdge() {
		HudEditSnap snap = snap(HudSnapMode.CENTER_GRID);
		// the right edge pulled to 2 short of the center line
		assertEquals(480, snap.snapEnd(478, 100, 20, false));
		// with Shift as it is
		assertEquals(478, snap.snapEnd(478, 100, 20, true));
	}

	@Test
	void theGridSizeIsTheStep() {
		HudEditSnap snap = new HudEditSnap();
		snap.begin(W, H, HudSnapMode.GRID_ONLY, HudGridSize.LARGE.step());
		// every 16: its center 238 goes to 240 (at every 8 its right edge 263 would go to 264 instead)
		snap.snap(213, 99, 50, 30, false, W, H);
		assertEquals(215, snap.snapped[0]);
	}
}
