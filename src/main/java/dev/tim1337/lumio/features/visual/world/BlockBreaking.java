package dev.tim1337.lumio.features.visual.world;

import net.minecraft.core.BlockPos;

/** The stage of breaking the game was last told for a block: servers with their own mining only say this. Only read, and only one block is kept: the newest. */
public final class BlockBreaking {
	private static final int NONE = -1;
	private static long pos;
	private static int stage = NONE;

	private BlockBreaking() {
	}

	/** The game was told how far a block is broken: from 0 to 9, anything else for no longer. */
	public static void told(BlockPos at, int progress) {
		pos = at.asLong();
		stage = progress >= 0 && progress < 10 ? progress : NONE;
	}

	/** @return the stage of a block, or -1 */
	public static int stageAt(BlockPos at) {
		return at.asLong() == pos ? stage : NONE;
	}
}
