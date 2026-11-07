package dev.tim1337.lumio.render;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * A white texture whose picture is only in its alpha, with mipmaps and linear filtering between them, so it stays smooth when drawn small and
 * sharp when drawn large. Every pixel is white, also where it is clear, so filtering never mixes in a dark color at the edges. The levels are made
 * off the render thread ({@link #levels}) and uploaded here.
 */
public final class MipTexture extends AbstractTexture {
	private final int width;
	private final int height;
	private final NativeImage[] images;

	/** @param levels how many mip levels, the full size included */
	public MipTexture(String label, int width, int height, int levels) {
		this.width = width;
		this.height = height;
		images = new NativeImage[levels];
		for (int level = 0; level < levels; level++) {
			images[level] = new NativeImage(NativeImage.Format.RGBA, Math.max(1, width >> level), Math.max(1, height >> level), true);
		}
		var device = RenderSystem.getDevice();
		texture = device.createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, width, height, 1,
			levels);
		textureView = device.createTextureView(texture);
		sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
	}

	/** @return how many mip levels a texture of a size gets, down to a side of a few pixels */
	public static int levelsFor(int width, int height, int most) {
		int levels = 1;
		while (levels < most && (width >> levels) >= 4 && (height >> levels) >= 4) {
			levels++;
		}
		return levels;
	}

	/**
	 * Makes the mip levels of an alpha picture: every level averages four pixels of the one before. Runs on any thread.
	 *
	 * @param alpha the full size picture's alpha, row by row, from 0 to 255
	 * @return the pixels of every level, white with the alpha
	 */
	public static int[][] levels(int[] alpha, int width, int height, int count) {
		int[][] out = new int[count][];
		int[] current = alpha;
		int w = width;
		int h = height;
		for (int level = 0; level < count; level++) {
			int[] pixels = new int[w * h];
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = current[i] << 24 | 0xFFFFFF;
			}
			out[level] = pixels;
			if (level + 1 < count) {
				int nw = Math.max(1, w >> 1);
				int nh = Math.max(1, h >> 1);
				int[] next = new int[nw * nh];
				for (int y = 0; y < nh; y++) {
					int y0 = Math.min(h - 1, y * 2);
					int y1 = Math.min(h - 1, y * 2 + 1);
					for (int x = 0; x < nw; x++) {
						int x0 = Math.min(w - 1, x * 2);
						int x1 = Math.min(w - 1, x * 2 + 1);
						next[y * nw + x] = (current[y0 * w + x0] + current[y0 * w + x1] + current[y1 * w + x0] + current[y1 * w + x1] + 2) / 4;
					}
				}
				current = next;
				w = nw;
				h = nh;
			}
		}
		return out;
	}

	/** Uploads the levels made by {@link #levels}; on the render thread. */
	public void upload(int[][] levels) {
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		for (int level = 0; level < images.length && level < levels.length; level++) {
			NativeImage image = images[level];
			MemoryUtil.memIntBuffer(image.getPointer(), image.getWidth() * image.getHeight()).put(levels[level]);
			encoder.writeToTexture(texture, image, level, 0, 0, 0);
		}
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	@Override
	public void close() {
		for (NativeImage image : images) {
			image.close();
		}
		super.close();
	}
}
