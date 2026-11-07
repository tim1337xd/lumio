package dev.tim1337.lumio.api.experimental.util;

import net.minecraft.core.BlockPos;

/**
 * How far the block being mined is broken, for servers that break blocks themselves (the client's own progress stays near zero there). An add-on that knows
 * it sets one source when it starts; the block overlay asks it every frame for the block it shows, so the answer must be cheap.
 */
public final class BreakingProgress {
	/** Answers for one block. */
	@FunctionalInterface
	public interface Source {
		/**
		 * @return how far the block at this position is along, from 0 to 1, or a negative number when it is not known here (then the game's own
		 *         progress is shown). 1 is the moment the player can move on to the next block, which may come before the break (a server that
		 *         needs the next block started a ping early); the overlay fills to it as it is, so the answer should be smooth between ticks and
		 *         never go back.
		 */
		float of(BlockPos pos);
	}

	private static volatile Source source;

	private BreakingProgress() {
	}

	/** Sets the source; a later one replaces it. */
	public static void set(Source value) {
		source = value;
	}

	/** @return how far the block is broken from 0 to 1, or a negative number when no source knows it */
	public static float of(BlockPos pos) {
		Source now = source;
		return now == null ? -1f : now.of(pos);
	}
}
