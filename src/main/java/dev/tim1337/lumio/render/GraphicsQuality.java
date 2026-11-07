package dev.tim1337.lumio.render;

import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;

/** What the game's Graphics setting means for the mod's own effects. */
public final class GraphicsQuality {
	private static final float FAST_SHARE = 0.5f;

	private GraphicsQuality() {
	}

	/** @return how much of an effect's normal amount to draw: all of it, or half on Fast */
	public static float share() {
		return isFast() ? FAST_SHARE : 1f;
	}

	/** @return whether the game runs on its lowest graphics */
	public static boolean isFast() {
		return Minecraft.getInstance().options.graphicsPreset().get() == GraphicsPreset.FAST;
	}
}
