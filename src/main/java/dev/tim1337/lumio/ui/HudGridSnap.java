package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.hud.GridSnap;

/**
 * The HUD editor's snapping for add-ons ({@link GridSnap}): the same {@link HudEditSnap} the editor drags its elements with, with the grid and
 * the screen's center lines as its only targets (no screen edges, no other elements) and the grid's spacing as the editor's Snap menu sets it.
 */
public final class HudGridSnap implements GridSnap {
	private final HudEditSnap snap = new HudEditSnap();
	private final int fixedStep;
	private int step = 1;
	private int screenWidth;
	private int screenHeight;

	/** With the grid's spacing as the player set it in the HUD editor. */
	public HudGridSnap() {
		this(0);
	}

	/** @param step a fixed spacing of the grid, for tests; 0 takes the HUD editor's */
	HudGridSnap(int step) {
		fixedStep = step;
	}

	@Override
	public void begin(int width, int height) {
		screenWidth = width;
		screenHeight = height;
		step = Math.max(1, fixedStep > 0 ? fixedStep : HudEditScreen.gridStep());
		snap.begin(width, height, HudSnapMode.CENTER_GRID, step, false);
	}

	@Override
	public boolean move(int x, int y, int width, int height, boolean free) {
		return snap.snap(x, y, width, height, free, screenWidth, screenHeight);
	}

	@Override
	public int x() {
		return snap.snapped[0];
	}

	@Override
	public int y() {
		return snap.snapped[1];
	}

	@Override
	public int edge(int axis, int edge, boolean free) {
		return snap.snapEdge(axis, edge, free);
	}

	@Override
	public boolean onGrid(int axis) {
		return snap.heldKind(axis) == HudEditSnap.GRID;
	}

	@Override
	public boolean onCenter(int axis) {
		return snap.heldKind(axis) == HudEditSnap.CENTER;
	}

	@Override
	public int heldAt(int axis) {
		return snap.heldAt(axis);
	}

	@Override
	public int step() {
		return step;
	}
}
