package dev.tim1337.lumio.api;

import net.minecraft.world.entity.Entity;

/**
 * Outlines entities with the game's glowing outline, in a color of choice. Asked for every entity in every frame, so both methods must return
 * fast; they must only outline what the player can see (see the server rules in API.md).
 */
public interface EntityHighlighter {
	/** @return whether the entity is outlined now */
	boolean glows(Entity entity);

	/** @return the outline color of the entity as 0xRRGGBB, or -1 to leave the game's own */
	int outlineColor(Entity entity);
}
