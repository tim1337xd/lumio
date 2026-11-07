package dev.tim1337.lumio.features.visual.world.cinematic;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * Looks at the blocks around the player for the cinematic camera, without creating objects: a walk through the block grid along a line (the
 * game's ray cast makes several objects per ray, and the camera casts dozens per frame), whether a point has room around it, and the light there.
 * Every block with a collision shape counts as solid, including glass and leaves: the camera never looks through any of them.
 */
final class CineRays {
	/** A ray never walks further than this many blocks. */
	private static final int LONGEST_STEPS = 96;
	/** Light levels as the game counts them. */
	private static final float FULL_LIGHT = 15f;

	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

	private CineRays() {
	}

	/** @return whether the block at the point is solid: it has a collision shape */
	static boolean solid(Level level, double x, double y, double z) {
		POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		return solidAt(level);
	}

	private static boolean solidAt(Level level) {
		var state = level.getBlockState(POS);
		return !state.isAir() && !state.getCollisionShape(level, POS).isEmpty();
	}

	/** @return whether the point is in water or lava */
	static boolean inFluid(Level level, double x, double y, double z) {
		POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		return !level.getBlockState(POS).getFluidState().isEmpty();
	}

	/** @return whether a cube of the given half size around the point touches no solid block */
	static boolean roomy(Level level, double x, double y, double z, double half) {
		int x0 = Mth.floor(x - half);
		int y0 = Mth.floor(y - half);
		int z0 = Mth.floor(z - half);
		int x1 = Mth.floor(x + half);
		int y1 = Mth.floor(y + half);
		int z1 = Mth.floor(z + half);
		for (int bx = x0; bx <= x1; bx++) {
			for (int by = y0; by <= y1; by++) {
				for (int bz = z0; bz <= z1; bz++) {
					POS.set(bx, by, bz);
					if (solidAt(level)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** @return whether the line between the points crosses no solid block */
	static boolean clear(Level level, double x0, double y0, double z0, double x1, double y1, double z1) {
		double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		return reach(level, x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, length) >= length - 1e-3;
	}

	/**
	 * Walks the block grid from a point in a direction, cell by cell (the cells a line really passes, none skipped).
	 *
	 * @return how far the line gets before it enters a solid block, at most {@code limit}
	 */
	static double reach(Level level, double x, double y, double z, double dx, double dy, double dz, double limit) {
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1e-6) {
			return 0.0;
		}
		dx /= length;
		dy /= length;
		dz /= length;
		int bx = Mth.floor(x);
		int by = Mth.floor(y);
		int bz = Mth.floor(z);
		int stepX = dx > 0 ? 1 : -1;
		int stepY = dy > 0 ? 1 : -1;
		int stepZ = dz > 0 ? 1 : -1;
		double deltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
		double deltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
		double deltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
		double nextX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? bx + 1 - x : x - bx) * deltaX;
		double nextY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? by + 1 - y : y - by) * deltaY;
		double nextZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? bz + 1 - z : z - bz) * deltaZ;
		double travelled = 0.0;
		for (int step = 0; step < LONGEST_STEPS && travelled < limit; step++) {
			POS.set(bx, by, bz);
			if (solidAt(level)) {
				return Math.min(limit, travelled);
			}
			if (nextX < nextY && nextX < nextZ) {
				travelled = nextX;
				nextX += deltaX;
				bx += stepX;
			} else if (nextY < nextZ) {
				travelled = nextY;
				nextY += deltaY;
				by += stepY;
			} else {
				travelled = nextZ;
				nextZ += deltaZ;
				bz += stepZ;
			}
		}
		return Math.min(limit, travelled);
	}

	/** @return the light at a point from 0 to 1: the block light, or the sky's as far as it is day */
	static float light(Level level, double x, double y, double z, float day) {
		POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		int block = level.getBrightness(LightLayer.BLOCK, POS);
		int sky = level.getBrightness(LightLayer.SKY, POS);
		return Math.max(block, sky * day) / FULL_LIGHT;
	}

	/** @return how bright light sources near a point are, from 0 to 1, for a night background that has some */
	static float glow(Level level, double x, double y, double z) {
		POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		return level.getBrightness(LightLayer.BLOCK, POS) / FULL_LIGHT;
	}
}
