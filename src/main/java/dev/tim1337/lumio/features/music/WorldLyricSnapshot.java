package dev.tim1337.lumio.features.music;

import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;

/**
 * The blocks a look of {@link WorldLyricOpenSky} may meet, copied on the render thread, the way the game copies chunk sections for the threads that
 * build its meshes. The background thread reads only these copies, never the live world, so a chunk changing or unloading cannot touch it. Once
 * handed to that thread a snapshot is not changed any more.
 */
final class WorldLyricSnapshot implements BlockGetter {
	/** A section's size, in blocks, and the shift and mask that go with it. */
	private static final int SECTION_BLOCKS = 16;
	private static final int SECTION_SHIFT = 4;
	private static final int IN_SECTION = SECTION_BLOCKS - 1;
	private static final int KEY_XZ_BITS = 22;
	private static final int KEY_Y_BITS = 20;
	private static final long KEY_XZ_MASK = (1L << KEY_XZ_BITS) - 1L;
	private static final long KEY_Y_MASK = (1L << KEY_Y_BITS) - 1L;

	/** A copied section: its blocks (null when it was all air or not loaded) and the look it was copied for. */
	record Section(PalettedContainer<BlockState> states, int look) {
	}

	private final Long2ObjectOpenHashMap<Section> sections;
	private final int minY;
	private final int height;

	WorldLyricSnapshot(Long2ObjectOpenHashMap<Section> sections, int minY, int height) {
		this.sections = sections;
		this.minY = minY;
		this.height = height;
	}

	/** @return the key of a section by its coordinates, in sections */
	static long key(int x, int y, int z) {
		return ((long) x & KEY_XZ_MASK) << KEY_XZ_BITS + KEY_Y_BITS | ((long) z & KEY_XZ_MASK) << KEY_Y_BITS | (long) y & KEY_Y_MASK;
	}

	/** @return the section's x, y or z from its key */
	static int keyX(long key) {
		return (int) (key >> KEY_XZ_BITS + KEY_Y_BITS);
	}

	static int keyY(long key) {
		return (int) (key << Long.SIZE - KEY_Y_BITS >> Long.SIZE - KEY_Y_BITS);
	}

	static int keyZ(long key) {
		return (int) (key << KEY_XZ_BITS >> Long.SIZE - KEY_XZ_BITS);
	}

	/**
	 * Walks a ray through the grid of sections and names each one it passes, from the start to its reach.
	 *
	 * @param dx the ray's direction, of length 1, as {@code dy} and {@code dz}
	 */
	static void sectionsAlong(double x, double y, double z, double dx, double dy, double dz, double reach, LongConsumer out) {
		int sx = Math.floorDiv((int) Math.floor(x), SECTION_BLOCKS);
		int sy = Math.floorDiv((int) Math.floor(y), SECTION_BLOCKS);
		int sz = Math.floorDiv((int) Math.floor(z), SECTION_BLOCKS);
		int stepX = dx > 0.0 ? 1 : -1;
		int stepY = dy > 0.0 ? 1 : -1;
		int stepZ = dz > 0.0 ? 1 : -1;
		double nextX = firstCrossing(x, sx, dx);
		double nextY = firstCrossing(y, sy, dy);
		double nextZ = firstCrossing(z, sz, dz);
		double eachX = dx == 0.0 ? Double.POSITIVE_INFINITY : SECTION_BLOCKS / Math.abs(dx);
		double eachY = dy == 0.0 ? Double.POSITIVE_INFINITY : SECTION_BLOCKS / Math.abs(dy);
		double eachZ = dz == 0.0 ? Double.POSITIVE_INFINITY : SECTION_BLOCKS / Math.abs(dz);
		out.accept(key(sx, sy, sz));
		while (Math.min(nextX, Math.min(nextY, nextZ)) <= reach) {
			if (nextX <= nextY && nextX <= nextZ) {
				sx += stepX;
				nextX += eachX;
			} else if (nextY <= nextZ) {
				sy += stepY;
				nextY += eachY;
			} else {
				sz += stepZ;
				nextZ += eachZ;
			}
			out.accept(key(sx, sy, sz));
		}
	}

	/** @return how far along the ray it first leaves its section on this axis */
	private static double firstCrossing(double from, int section, double direction) {
		if (direction == 0.0) {
			return Double.POSITIVE_INFINITY;
		}
		double border = (direction > 0.0 ? section + 1 : section) * (double) SECTION_BLOCKS;
		return (border - from) / direction;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		Section section = sections.get(key(pos.getX() >> SECTION_SHIFT, pos.getY() >> SECTION_SHIFT, pos.getZ() >> SECTION_SHIFT));
		if (section == null || section.states() == null) {
			return Blocks.AIR.defaultBlockState();
		}
		return section.states().get(pos.getX() & IN_SECTION, pos.getY() & IN_SECTION, pos.getZ() & IN_SECTION);
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public int getMinY() {
		return minY;
	}
}
