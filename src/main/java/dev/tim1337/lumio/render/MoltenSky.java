package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.SkyFeature;
import dev.tim1337.lumio.features.visual.world.SkyMolten;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the Molten sky of {@link SkyFeature}: thin glowing lines over a dark sky, by day and by night, on the same box around the camera as the mod's
 * other skies and in the same pass, before the sun and the moon, so they stay on top (dimmed) and the clouds come after. The lines are worked out
 * in the shader from a seamless noise on the view's direction, so there is no texture and no seam.
 * <p>
 * On High it is drawn at the screen's full size. That costs well under a tenth of a millisecond on a strong graphics card, but its noise is worked
 * out three times per pixel, which a weak one feels: on Low and Medium it is drawn at half the size, its lines kept as wide on the screen, and made
 * larger with a smooth filter, a little softer. It uses the same calls the game uses for its sky, so it works with every backend.
 */
public final class MoltenSky {
	/** The box around the camera has 6 faces of 2 triangles each. */
	private static final int INDICES = 36;
	private static final int TEXELS = 5;
	/** How far the brightness, thickness and glow settings reach, as a byte: up to 2.55. */
	private static final float BRIGHTNESS_STEP = 100f;
	private static final float SKIPPED = 0.004f;

	/** The sky at the screen's full size, over the game's sky color. */
	private static final RenderPipeline PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_molten"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky_molten"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	/** The lower qualities: the sky at half the screen's size into a texture of its own, written as it is. */
	private static final RenderPipeline HALF_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_molten_half"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky_molten"))
			.withShaderDefine("HALF_SIZE")
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	/** The half-size picture made larger over the game's sky color, in the sky's pass. */
	private static final RenderPipeline UP_PIPELINE = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/sky_molten_up"))
			.withVertexShader(Lumio.id("core/sky"))
			.withFragmentShader(Lumio.id("core/sky_molten_up"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	);

	/** The levels of the sky's quality below this draw it at half the size. */
	private static final int FULL_SIZE_QUALITY = 2;
	private static final int HALF = 2;
	private static TextureTarget halfTarget;
	private static boolean halfUsable;

	private static ParameterTexture parameters;
	private static double seconds;
	private static long lastNanos;
	private static int checkedFor = -1;
	private static boolean usable;

	/** What {@link #prepare} left for the draw in the sky's pass: no pipeline when there is nothing to draw. */
	private static RenderPipeline prepared;
	private static GpuBuffer preparedBox;
	private static GpuBuffer preparedIndexBuffer;
	private static RenderSystem.AutoStorageIndexBuffer preparedIndices;
	private static GpuBufferSlice preparedTransform;

	private MoltenSky() {
	}

	/** @return whether a draw is ready for the sky's pass */
	static boolean isPrepared() {
		return prepared != null;
	}

	/** Everything of a frame's Molten sky but its draw in the sky's pass: its parameters. */
	static void prepare(GpuBuffer box, RenderTarget target) {
		prepared = null;
		SkyFeature sky = ModFeatures.SKY;
		float level = sky.moltenLevel();
		if (level < SKIPPED || !pipelinesUsable()) {
			lastNanos = 0L;
			return;
		}
		if (parameters == null) {
			parameters = new ParameterTexture("sky_molten_parameters", TEXELS);
		}
		// its own clock at its own speed: a change of the speed never makes the lines jump
		long now = System.nanoTime();
		if (lastNanos != 0L) {
			seconds = (seconds + Math.min(0.25, (now - lastNanos) / 1e9) * sky.moltenSpeed()) % 65536.0;
		}
		lastNanos = now;
		int whole = (int) seconds;
		parameters.set(0, whole & 0xFF, whole >> 8 & 0xFF, Math.min(255, (int) ((seconds - whole) * 255.0)), 255);
		int line = sky.moltenRgb();
		parameters.set(1, line >> 16 & 0xFF, line >> 8 & 0xFF, line & 0xFF, Math.min(255, Math.round(sky.moltenBrightness() * BRIGHTNESS_STEP)));
		float[] horizon = SkyMolten.horizon(line);
		float[] zenith = SkyMolten.zenith(line);
		parameters.set(2, ParameterTexture.unit(horizon[0]), ParameterTexture.unit(horizon[1]), ParameterTexture.unit(horizon[2]), ParameterTexture.unit(level));
		parameters.set(3, ParameterTexture.unit(zenith[0]), ParameterTexture.unit(zenith[1]), ParameterTexture.unit(zenith[2]), sky.moltenLines());
		parameters.set(4, ParameterTexture.unit(sky.moltenFlash()), Math.round(sky.moltenThickness() * BRIGHTNESS_STEP), Math.round(sky.moltenGlow() * BRIGHTNESS_STEP), sky.moltenPulses() ? 255 : 0);
		parameters.upload();

		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(INDICES);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
		preparedBox = box;
		preparedIndexBuffer = indexBuffer;
		preparedIndices = indices;
		preparedTransform = transform;
		if (sky.quality() < FULL_SIZE_QUALITY && halfUsable) {
			TextureTarget half = halfTarget(target);
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lumio Molten sky", half.getColorTextureView(), Optional.empty(), half.getDepthTextureView(), OptionalDouble.empty())) {
				draw(pass, HALF_PIPELINE);
			}
			prepared = UP_PIPELINE;
			return;
		}
		prepared = PIPELINE;
	}

	/** Draws what {@link #prepare} made ready into the sky's pass; nothing when it made nothing ready. */
	static void drawPrepared(RenderPass pass) {
		if (prepared == null) {
			return;
		}
		draw(pass, prepared);
		prepared = null;
	}

	private static void draw(RenderPass pass, RenderPipeline pipeline) {
		pass.setPipeline(pipeline);
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", preparedTransform);
		pass.bindTexture("Sampler0", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
		if (pipeline == UP_PIPELINE) {
			pass.bindTexture("Sampler1", halfTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		}
		pass.setVertexBuffer(0, preparedBox.slice());
		pass.setIndexBuffer(preparedIndexBuffer, preparedIndices.type());
		pass.drawIndexed(INDICES, 1, 0, 0, 0);
	}

	/** @return whether all of its pipelines can be bound; kept until the shaders reload, as this is asked every frame */
	private static boolean pipelinesUsable() {
		if (checkedFor != ModPipelines.generation()) {
			checkedFor = ModPipelines.generation();
			usable = ModPipelines.usable(PIPELINE);
			// without the two of the half size it is drawn at full size
			halfUsable = ModPipelines.usable(HALF_PIPELINE) && ModPipelines.usable(UP_PIPELINE);
		}
		return usable;
	}

	/** @return the half-size texture, sized to the screen divided by {@link #HALF}, rounded up, as the filter up expects */
	private static TextureTarget halfTarget(RenderTarget screen) {
		int width = Math.max(1, (screen.width + HALF - 1) / HALF);
		int height = Math.max(1, (screen.height + HALF - 1) / HALF);
		if (halfTarget == null) {
			halfTarget = new TextureTarget("Lumio Molten sky", width, height, true, GpuFormat.RGBA16_FLOAT);
		} else if (halfTarget.width != width || halfTarget.height != height) {
			halfTarget.resize(width, height);
		}
		return halfTarget;
	}
}
