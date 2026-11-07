package dev.tim1337.lumio.api;

import net.minecraft.core.BlockPos;

/** The color of the block outline for a block an add-on cares about (tick glide on the block being mined). Asked every frame, so it is kept. */
public interface BlockTint {
	/** @return the outline color of the block as 0xRRGGBB, or -1 to leave the block overlay's own */
	int color(BlockPos pos);
}
