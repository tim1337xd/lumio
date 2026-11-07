package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A tiny texture used to pass values to a shader. Shaders cannot receive uniforms without the graphics backend knowing about them, but every
 * backend supports sampling a texture.
 * <p>
 * Every texel has four bytes. Values needing more than 8 bits can span two of them, see {@link #setPacked16}. The shader reads them with the same
 * layout: a texel's value is its channels from 0 to 255.
 */
public final class ParameterTexture {
	private final Identifier id;
	private final DynamicTexture texture;
	private final NativeImage image;
	/** Whether a value changed since the last upload: an upload of the same values is skipped. */
	private boolean dirty = true;

	/** Must be created while the game has a texture manager, which is the case whenever something is being drawn. */
	public ParameterTexture(String name, int texels) {
		id = Lumio.id(name);
		image = new NativeImage(NativeImage.Format.RGBA, texels, 1, true);
		texture = new DynamicTexture(() -> "Lumio " + id, image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
	}

	public Identifier id() {
		return id;
	}

	public GpuTextureView view() {
		return texture.getTextureView();
	}

	public void set(int texel, int red, int green, int blue, int alpha) {
		int abgr = (alpha & 0xFF) << 24 | (blue & 0xFF) << 16 | (green & 0xFF) << 8 | (red & 0xFF);
		if (image.getPixel(texel, 0) != abgr) {
			image.setPixelABGR(texel, 0, abgr);
			dirty = true;
		}
	}

	/** Stores a value from 0 to 1 in the red and green channels with 16-bit precision. */
	public void setPacked16(int texel, float value, int blue, int alpha) {
		int packed = Math.round(Math.clamp(value, 0f, 1f) * 65535f);
		set(texel, packed & 0xFF, packed >> 8, blue, alpha);
	}

	/** Sends the values to the graphics card, unless none changed since the last time. */
	public void upload() {
		if (!dirty) {
			return;
		}
		dirty = false;
		texture.upload();
	}
}
