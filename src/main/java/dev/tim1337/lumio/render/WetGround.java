package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.world.AmbienceFeature;
import dev.tim1337.lumio.features.visual.world.AmbienceLook;

import org.joml.Matrix4f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The wet ground of the Rain style: reflections in screen space on what faces up, near the camera. They are worked out at a smaller size from the finished picture and its depth, and laid over it, before the
 * hand. Nothing runs while the ground is dry.
 */
public final class WetGround {
	private static final int TEXELS = 16;
	private static final int DIVISOR = 2;
	private static final int DIVISOR_FAST = 3;
	private static final int STEPS = 22;
	private static final int STEPS_FAST = 10;
	private static final float LEAST_WET = 0.004f;
	/** The reflection distance at which the ray takes its usual steps, the least and most share of them, and the shader's most. */
	private static final float USUAL_DISTANCE = 64f;
	private static final float LEAST_STEP_SHARE = 0.6f;
	private static final float MOST_STEP_SHARE = 1.45f;
	private static final int MOST_STEPS = 32;

	private static final RenderPipeline REFLECT = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/wet_ground"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/wet_ground"))
			.withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").withSampler("Sampler2").build())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
			.build()
	);

	private static final RenderPipeline LAY_OVER = ModPipelines.register(
		RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
			.withLocation(Lumio.id("pipeline/wet_ground_over"))
			.withVertexShader(Lumio.id("core/glass_blit"))
			.withFragmentShader(Lumio.id("core/wet_ground_over"))
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.build()
	);

	private static final Matrix4f NO_TRANSFORM = new Matrix4f();
	private static ParameterTexture parameters;
	private static TextureTarget reflections;

	private WetGround() {
	}

	/** Lays the reflections over the finished world; nothing while the ground is dry. */
	public static void draw(RenderTarget main) {
		AmbienceFeature ambience = ModFeatures.AMBIENCE;
		float wet = ambience.field().wet();
		Minecraft client = Minecraft.getInstance();
		if (wet <= LEAST_WET || client.level == null || !ModPipelines.usable(REFLECT) || !ModPipelines.usable(LAY_OVER)) {
			return;
		}
		Camera camera = client.gameRenderer.mainCamera();
		if (camera.getFluidInCamera() != FogType.NONE) {
			return;
		}
		boolean fast = GraphicsQuality.isFast();
		int divisor = fast ? DIVISOR_FAST : DIVISOR;
		int width = Math.max(1, main.width / divisor);
		int height = Math.max(1, main.height / divisor);
		if (reflections == null) {
			reflections = new TextureTarget("Lumio wet ground", width, height, false, GpuFormat.RGBA8_UNORM);
		} else if (reflections.width != width || reflections.height != height) {
			reflections.resize(width, height);
		}
		if (parameters == null) {
			parameters = new ParameterTexture("wet_ground_parameters", TEXELS);
		}
		writeParameters(ambience, wet, fast, camera);
		parameters.upload();

		GpuBuffer quad = GlassPanes.fullQuad();
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(6);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(NO_TRANSFORM);
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = encoder.createRenderPass(() -> "Lumio wet ground", reflections.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(REFLECT);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", parameters.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.bindTexture("Sampler1", main.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.bindTexture("Sampler2", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setVertexBuffer(0, quad.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(6, 1, 0, 0, 0);
		}
		try (RenderPass pass = encoder.createRenderPass(() -> "Lumio wet ground over", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(LAY_OVER);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.bindTexture("Sampler0", reflections.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setVertexBuffer(0, quad.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(6, 1, 0, 0, 0);
		}
	}

	private static void writeParameters(AmbienceFeature ambience, float wet, boolean fast, Camera camera) {
		// a longer reach takes more steps along the reflected ray, so it still finds what is far; a shorter one fewer
		float distance = ambience.reflectionDistance();
		int steps = Math.round((fast ? STEPS_FAST : STEPS) * Mth.clamp(distance / USUAL_DISTANCE, LEAST_STEP_SHARE, MOST_STEP_SHARE));
		parameters.set(0, Math.round(Mth.clamp(wet, 0f, 1f) * 255f), ambience.wetGround() == AmbienceLook.WetGround.EVERYWHERE ? 255 : 0, Math.min(MOST_STEPS, steps), 255);
		parameters.set(10, Math.round(Mth.clamp(distance, 0f, 255f)), 0, 0, 255);
		// the sky as the frame shows it, at the horizon (the fog) and straight up: what a ray that leaves the picture sees
		int horizon = FogState.color();
		int zenith = FogState.skyColor();
		parameters.set(11, horizon >> 16 & 0xFF, horizon >> 8 & 0xFF, horizon & 0xFF, 255);
		parameters.set(12, zenith >> 16 & 0xFF, zenith >> 8 & 0xFF, zenith & 0xFF, 255);
		double seconds = System.nanoTime() / 1e9 % 65536.0;
		int whole = (int) seconds;
		parameters.set(1, whole & 0xFF, whole >> 8, (int) ((seconds - whole) * 255.0), 255);
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		Vector3fc forward = camera.forwardVector();
		// the camera's axes in the world: to the right, up and backwards
		vector(2, -left.x(), -left.y(), -left.z());
		vector(4, up.x(), up.y(), up.z());
		vector(6, -forward.x(), -forward.y(), -forward.z());
		Vec3 position = camera.position();
		wrapped(8, position.x);
		wrapped(9, position.z);
	}

	/** A direction in two texels, 16 bits to each part. */
	private static void vector(int texel, float x, float y, float z) {
		int a = signed16(x);
		int b = signed16(y);
		int c = signed16(z);
		parameters.set(texel, a & 0xFF, a >> 8, b & 0xFF, b >> 8);
		parameters.set(texel + 1, c & 0xFF, c >> 8, 0, 255);
	}

	private static int signed16(float value) {
		return Math.round((Mth.clamp(value, -1f, 1f) * 0.5f + 0.5f) * 65535f);
	}

	/** A coordinate kept in 4096 blocks, which is as far as the patterns on the ground need it: 16 bits of whole blocks and a fraction. */
	private static void wrapped(int texel, double coordinate) {
		double kept = ((coordinate % 4096.0) + 4096.0) % 4096.0;
		int whole = (int) kept;
		parameters.set(texel, whole & 0xFF, whole >> 8, (int) ((kept - whole) * 255.0), 255);
	}
}
