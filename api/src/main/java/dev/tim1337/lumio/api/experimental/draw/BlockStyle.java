package dev.tim1337.lumio.api.experimental.draw;

import net.minecraft.network.chat.Component;

/** How a block is highlighted in the world: the block overlay's styles, which add-ons draw their highlights in too. */
public enum BlockStyle {
	OUTLINE("Outline"),
	FILL("Fill"),
	GLOW("Glow"),
	SHADER("Shader");

	private final String title;

	BlockStyle(String title) {
		this.title = title;
	}

	public Component displayName() {
		return Component.literal(title);
	}
}
