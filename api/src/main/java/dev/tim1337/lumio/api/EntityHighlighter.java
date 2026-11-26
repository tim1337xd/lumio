package dev.tim1337.lumio.api;

import net.minecraft.world.entity.Entity;

/**
 * Outlines entities with the game's glowing outline, in a color of choice. Asked for every entity in every frame, so all methods must return
 * fast; they must only outline what the player can see (see the server rules in API.md).
 */
public interface EntityHighlighter {
	/** @return whether the entity is outlined now */
	boolean glows(Entity entity);

	/** @return the outline color of the entity as 0xRRGGBB, or -1 to leave the game's own */
	int outlineColor(Entity entity);

	/**
	 * Lumio's own outline instead of the game's: a line this wide around the entity, drawn only where the entity is seen (never through walls). In a frame
	 * where any glowing entity asks for it, all outlines of that frame are drawn this way, with the widest line and glow asked.
	 *
	 * @return the width of the line in screen pixels, up to 8; 0 (the default) leaves the game's glowing outline
	 * @since 1.16.0
	 */
	default float outlineWidth(Entity entity) {
		return 0f;
	}

	/**
	 * @return how far a soft glow reaches beyond Lumio's outline, in screen pixels, up to 24; 0 (the default) for none. Only with an
	 *         {@link #outlineWidth outline width}.
	 * @since 1.16.0
	 */
	default float glowWidth(Entity entity) {
		return 0f;
	}
}
