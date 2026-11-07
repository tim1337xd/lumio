package dev.tim1337.lumio.features.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the world lyric placement needs to know about the world: what in a block cell would hide or cut through a line. Behind an interface so
 * the placement can be tested with a block grid of its own.
 */
interface WorldLyricBlocks {
	/** The six values of a cell's box: its lowest corner (x, y, z), then its highest. */
	int BOX_SIZE = 6;

	/**
	 * Writes the box around what fills a cell into {@code box}, in the cell's own coordinates from 0 to 1: its lowest corner, then its highest.
	 *
	 * @return false when nothing in the cell can hide or cut through a line
	 */
	boolean shape(int x, int y, int z, double[] box);

	/** @return the blocks of the world the player is in */
	static WorldLyricBlocks client() {
		return new Client();
	}

	/**
	 * The world of the game. Every block with any shape counts, not only full ones: leaves, glass, fences, panes, slabs, plants and torches, each
	 * by the box around its shape, since a line through them looks as broken as one inside stone. A fluid counts at its surface only, up to its
	 * height there, so a line may stand under water when the player is there, but never cuts through the surface.
	 */
	final class Client implements WorldLyricBlocks {
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		@Override
		public boolean shape(int x, int y, int z, double[] box) {
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return false;
			}
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (state.isAir()) {
				return false;
			}
			boolean filled = false;
			VoxelShape shape = state.getShape(level, pos);
			if (!shape.isEmpty()) {
				box[0] = shape.min(Direction.Axis.X);
				box[1] = shape.min(Direction.Axis.Y);
				box[2] = shape.min(Direction.Axis.Z);
				box[3] = shape.max(Direction.Axis.X);
				box[4] = shape.max(Direction.Axis.Y);
				box[5] = shape.max(Direction.Axis.Z);
				filled = true;
			}
			FluidState fluid = state.getFluidState();
			if (!fluid.isEmpty() && level.getFluidState(pos.set(x, y + 1, z)).isEmpty()) {
				float height = fluid.getHeight(level, pos.set(x, y, z));
				// the surface spans the whole cell, up to the fluid's height or the block's top, whichever is higher
				double top = filled ? Math.max(box[4], height) : height;
				box[0] = 0.0;
				box[1] = 0.0;
				box[2] = 0.0;
				box[3] = 1.0;
				box[4] = top;
				box[5] = 1.0;
				filled = true;
			}
			return filled;
		}
	}
}
