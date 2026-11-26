package dev.tim1337.lumio.features.waypoints;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shape of a point's block as the game outlines it: its boxes and edges, in the block (0 to 1), so a slab, stairs or a carpet are marked as they
 * are. A full block, or a place with nothing there, is the whole block. Worked out again only when the block or the point changes.
 */
final class PointShape {
	static final int MOST_BOXES = 8;
	static final int MOST_EDGES = 48;
	/** Six numbers per box (the low corner, then the high one) and per edge (one end, then the other). */
	final float[] boxes = new float[MOST_BOXES * 6];
	final float[] edges = new float[MOST_EDGES * 6];
	int boxCount;
	int edgeCount;
	/** Whether the shape is the whole block, drawn as one box. */
	boolean full = true;
	private BlockState state;
	private Waypoint point;

	/** Takes the shape of the block at the point, if it changed. */
	void update(BlockGetter level, BlockPos pos, Waypoint at) {
		BlockState now = level.getBlockState(pos);
		if (now == state && at == point) {
			return;
		}
		state = now;
		point = at;
		boxCount = 0;
		edgeCount = 0;
		full = true;
		VoxelShape shape = now.getShape(level, pos);
		if (shape.isEmpty()) {
			return;
		}
		shape.forAllBoxes(this::addBox);
		full = boxCount == 0 || boxCount == 1 && isWhole(boxes);
		if (!full) {
			shape.forAllEdges(this::addEdge);
		}
	}

	/** Forgets the shape, for a point whose chunk is not there: the whole block. */
	void reset() {
		state = null;
		point = null;
		boxCount = 0;
		edgeCount = 0;
		full = true;
	}

	private void addBox(double x0, double y0, double z0, double x1, double y1, double z1) {
		if (boxCount < MOST_BOXES) {
			put(boxes, boxCount++, x0, y0, z0, x1, y1, z1);
		}
	}

	private void addEdge(double x0, double y0, double z0, double x1, double y1, double z1) {
		if (edgeCount < MOST_EDGES) {
			put(edges, edgeCount++, x0, y0, z0, x1, y1, z1);
		}
	}

	private static void put(float[] into, int index, double x0, double y0, double z0, double x1, double y1, double z1) {
		int at = index * 6;
		into[at] = (float) x0;
		into[at + 1] = (float) y0;
		into[at + 2] = (float) z0;
		into[at + 3] = (float) x1;
		into[at + 4] = (float) y1;
		into[at + 5] = (float) z1;
	}

	/** @return whether a box is the whole block */
	static boolean isWhole(float[] box) {
		return box[0] <= 0f && box[1] <= 0f && box[2] <= 0f && box[3] >= 1f && box[4] >= 1f && box[5] >= 1f;
	}
}
