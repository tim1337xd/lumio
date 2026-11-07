package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.RenderPipelines;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The glow of Radiance's Echo around the first person item and hands, as the reference makes it (Lumio-Design/EchoReference.html), in screen space:
 * the item and the hands grown by about 20 pixels, less themselves, looked at through two flowing waves (echo_glow_source), then blurred with a
 * Gaussian twice, narrow and wide (echo_glow_blur), each across and then down. All in pictures half as large as the screen; the composite reads the
 * two blurs with bilinear filtering and adds the narrow one fully and the wide one at 60 % to the screen, nothing where the item is. No distance
 * and nothing keyed to the outline, so the glow has no seams.
 */
final class EchoGlow {
	/** The two blurs' widths in pixels of a 1080 line screen, at the width 1. */
	private static final float NARROW_PIXELS = 6f;
	private static final float WIDE_PIXELS = 18f;
	private static final float REFERENCE_LINES = 1080f;
	/** A blur's width goes to the shader in quarter texels in the quad's red. */
	private static final float SIGMA_STEPS = 4f;
	private static final int ACROSS = 0;
	private static final int DOWN = 255;
	/** How far the blur looks out, in its widths (as echo_glow_blur). */
	private static final float REACH_SIGMAS = 2.5f;
	/** The glow's region in the half sized pictures (x, y, width, height from the bottom left), and with the blur's reach around it; reused. */
	private static final int[] inner = new int[4];
	private static final int[] outer = new int[4];
	private static final Supplier<String> NAME = () -> "Lumio echo glow";

	private static final RenderPipeline SOURCE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/echo_glow_source"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/echo_glow_source"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
			.build()
	);

	private static final RenderPipeline BLUR = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/echo_glow_blur"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/echo_glow_blur"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
			.build()
	);

	/** The source, the blur across, and the two blurs done. */
	private static final TextureTarget[] targets = new TextureTarget[4];
	private static final int SOURCE_TARGET = 0;
	private static final int ACROSS_TARGET = 1;
	private static final int NARROW_TARGET = 2;
	private static final int WIDE_TARGET = 3;
	/** One quad per blur width and direction, made when first needed. */
	private static final Map<Integer, GpuBuffer> quads = new HashMap<>();

	private EchoGlow() {
	}

	/** @return whether the glow can be made: all its pipelines compiled */
	static boolean usable() {
		return ModPipelines.usable(SOURCE) && ModPipelines.usable(BLUR);
	}

	/** @return the narrow blur, made for a screen of this size */
	static GpuTextureView narrow(RenderTarget main) {
		ensureTargets(main);
		return targets[NARROW_TARGET].getColorTextureView();
	}

	/** @return the wide blur, made for a screen of this size */
	static GpuTextureView wide(RenderTarget main) {
		ensureTargets(main);
		return targets[WIDE_TARGET].getColorTextureView();
	}

	/**
	 * Makes the glow of this frame from the depth the hand pass left.
	 *
	 * @param parameters the after-glow's values: the time and the shimmer, and the width
	 * @param width      the width setting, which widens the blurs
	 * @param region     where the glow can reach, in screen pixels from the bottom left (x, y, width, height), or null for the whole screen
	 */
	static void build(CommandEncoder encoder, RenderTarget main, GpuBufferSlice transform, GpuTextureView parameters, float width, int[] region) {
		ensureTargets(main);
		TextureTarget half = targets[SOURCE_TARGET];
		float texelsPerLine = half.height / REFERENCE_LINES;
		// the second blur and the composite read the passes before them up to the wide blur's reach around the region
		int reach = (int) Math.ceil(WIDE_PIXELS * width * texelsPerLine * REACH_SIGMAS) + 2;
		boolean bounded = region != null;
		if (bounded) {
			inner[0] = Math.max(0, region[0] / 2 - 1);
			inner[1] = Math.max(0, region[1] / 2 - 1);
			inner[2] = Math.min(half.width - inner[0], region[2] / 2 + 3);
			inner[3] = Math.min(half.height - inner[1], region[3] / 2 + 3);
			outer[0] = Math.max(0, inner[0] - reach);
			outer[1] = Math.max(0, inner[1] - reach);
			outer[2] = Math.min(half.width, inner[0] + inner[2] + reach) - outer[0];
			outer[3] = Math.min(half.height, inner[1] + inner[3] + reach) - outer[1];
		}
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		TextureTarget source = targets[SOURCE_TARGET];
		try (RenderPass pass = encoder.createRenderPass(NAME, source.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(SOURCE);
			if (bounded) {
				pass.enableScissor(outer[0], outer[1], outer[2], outer[3]);
			}
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", parameters, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.bindTexture("Sampler1", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setVertexBuffer(0, GlassPanes.fullQuad().slice());
			pass.setIndexBuffer(indices.getBuffer(6), indices.type());
			pass.drawIndexed(6, 1, 0, 0, 0);
		}
		// a pixel of a 1080 line screen is this many texels of the half sized pictures
		float texels = source.height / REFERENCE_LINES;
		blur(encoder, transform, indices, NARROW_PIXELS * width * texels, NARROW_TARGET, bounded);
		blur(encoder, transform, indices, WIDE_PIXELS * width * texels, WIDE_TARGET, bounded);
	}

	private static void blur(CommandEncoder encoder, GpuBufferSlice transform, RenderSystem.AutoStorageIndexBuffer indices, float sigmaTexels, int into,
							 boolean bounded) {
		int sigma = Math.clamp(Math.round(sigmaTexels * SIGMA_STEPS), 1, 255);
		// across over the wider region, as the pass down reads it above and below; down only where the glow can be
		draw(encoder, targets[ACROSS_TARGET], targets[SOURCE_TARGET].getColorTextureView(), quad(sigma, ACROSS), transform, indices, bounded ? outer : null);
		draw(encoder, targets[into], targets[ACROSS_TARGET].getColorTextureView(), quad(sigma, DOWN), transform, indices, bounded ? inner : null);
	}

	private static void draw(CommandEncoder encoder, TextureTarget into, GpuTextureView from, GpuBuffer quad, GpuBufferSlice transform,
		RenderSystem.AutoStorageIndexBuffer indices, int[] scissor) {
		try (RenderPass pass = encoder.createRenderPass(NAME, into.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(BLUR);
			if (scissor != null) {
				pass.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
			}
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", from, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setVertexBuffer(0, quad.slice());
			pass.setIndexBuffer(indices.getBuffer(6), indices.type());
			pass.drawIndexed(6, 1, 0, 0, 0);
		}
	}

	private static void ensureTargets(RenderTarget main) {
		int width = (main.width + 1) / 2;
		int height = (main.height + 1) / 2;
		if (targets[0] != null && targets[0].width == width && targets[0].height == height) {
			return;
		}
		for (int i = 0; i < targets.length; i++) {
			if (targets[i] != null) {
				targets[i].destroyBuffers();
			}
			targets[i] = new TextureTarget("Lumio echo glow " + i, width, height, false, GpuFormat.RGBA16_FLOAT);
		}
	}

	/** @return a quad over the whole target with a blur's width in its red and its direction in its green, as glass_blit takes it */
	private static GpuBuffer quad(int red, int green) {
		return quads.computeIfAbsent(red << 8 | green, key -> {
			int color = 0xFF000000 | (green & 0xFF) << 8 | red & 0xFF;
			float[][] corners = {{-1f, -1f}, {1f, -1f}, {1f, 1f}, {-1f, 1f}};
			ByteBuffer data = ByteBuffer.allocateDirect(4 * 24).order(ByteOrder.nativeOrder());
			for (float[] corner : corners) {
				data.putFloat(corner[0]).putFloat(corner[1]).putFloat(0f);
				data.putFloat(corner[0] * 0.5f + 0.5f).putFloat(corner[1] * 0.5f + 0.5f);
				data.putInt(color);
			}
			data.flip();
			return RenderSystem.getDevice().createBuffer(() -> "Lumio echo glow quad", GpuBuffer.USAGE_VERTEX, data);
		});
	}
}
