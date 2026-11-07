package dev.tim1337.lumio.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** A dynamic texture with linear filtering, so it stays clean when drawn at non-integer scales. */
public class SmoothTexture extends DynamicTexture {
	public SmoothTexture(String name, NativeImage image) {
		super(() -> name, image);
		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}
}
