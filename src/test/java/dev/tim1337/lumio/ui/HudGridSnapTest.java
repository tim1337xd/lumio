package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.hud.GridSnap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HUD editor's snapping as add-ons get it: the grid and the screen's center lines only, with the editor's own logic. */
class HudGridSnapTest {
	private static final int W = 960;
	private static final int H = 540;
	private static final int GRID = 8;

	private static HudGridSnap snap() {
		HudGridSnap snap = new HudGridSnap(GRID);
		snap.begin(W, H);
		return snap;
	}

	@Test
	void aMovedBoxSnapsToTheGrid() {
		HudGridSnap snap = snap();
		// left 98 is 2 from the line at 96, top 121 is 1 from 120
		assertTrue(snap.move(98, 121, 121, 68, false));
		assertEquals(96, snap.x());
		assertEquals(120, snap.y());
		assertTrue(snap.onGrid(GridSnap.ACROSS));
		assertTrue(snap.onGrid(GridSnap.DOWN));
		assertEquals(96, snap.heldAt(GridSnap.ACROSS));
		assertEquals(GRID, snap.step());
	}

	@Test
	void aBoxesCenterSnapsToTheScreensCenter() {
		HudGridSnap snap = snap();
		// its center 481 goes to the center line at 480
		assertTrue(snap.move(434, 101, 95, 20, false));
		assertEquals(433, snap.x());
		assertTrue(snap.onCenter(GridSnap.ACROSS));
		assertFalse(snap.onGrid(GridSnap.ACROSS));
	}

	@Test
	void theScreensEdgesAreNoTarget() {
		// the HUD editor holds an element 8 from the edge (its margin); here only the grid does, which has no line near
		HudEditSnap editor = new HudEditSnap();
		editor.begin(W, H, HudSnapMode.CENTER_GRID, 200);
		editor.snap(849, 220, 100, 21, false, W, H);
		assertEquals(HudEditSnap.EDGE, editor.heldKind(0));
		HudGridSnap snap = new HudGridSnap(200);
		snap.begin(W, H);
		snap.move(849, 220, 100, 21, false);
		assertEquals(849, snap.x());
		assertFalse(snap.onGrid(GridSnap.ACROSS));
		assertFalse(snap.onCenter(GridSnap.ACROSS));
	}

	@Test
	void shiftPlacesFreely() {
		HudGridSnap snap = snap();
		assertFalse(snap.move(98, 203, 121, 77, true));
		assertEquals(98, snap.x());
		assertEquals(203, snap.y());
	}

	@Test
	void aLineHoldsUntilPulledTwiceAsFar() {
		HudGridSnap snap = new HudGridSnap(32);
		snap.begin(W, H);
		snap.move(98, 300, 121, 77, false);
		assertEquals(96, snap.x());
		// 5 away: still held (it snapped in within 3, lets go beyond 6)
		snap.move(101, 300, 121, 77, false);
		assertEquals(96, snap.x());
		snap.move(107, 300, 121, 77, false);
		assertEquals(107, snap.x());
	}

	@Test
	void aPulledEdgeSnapsOnItsAxisOnly() {
		HudGridSnap snap = snap();
		assertEquals(480, snap.edge(GridSnap.ACROSS, 478, false));
		assertEquals(304, snap.edge(GridSnap.DOWN, 302, false));
		// both stay held
		assertTrue(snap.onCenter(GridSnap.ACROSS));
		assertTrue(snap.onGrid(GridSnap.DOWN));
		assertEquals(478, snap.edge(GridSnap.ACROSS, 478, true));
	}
}
